import AVFoundation
import Vision
import UIKit

final class QrCodeScanner: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {

    private let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "qr.camera.queue")
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var paused = false

    private var currentDevice: AVCaptureDevice?

    var onResult: (([VNBarcodeObservation]) -> Void)?
    var onError: ((String) -> Void)?

    // MARK: - Start

    func start(previewView: UIView, lens: String, resolution: Int) throws {
        session.beginConfiguration()

        // resolution -> sessionPreset
        switch resolution {
        case 0:
            session.sessionPreset = .vga640x480
        case 2:
            session.sessionPreset = .hd1920x1080
        case 3:
            if session.canSetSessionPreset(.hd4K3840x2160) {
                session.sessionPreset = .hd4K3840x2160
            } else {
                session.sessionPreset = .hd1920x1080
            }
        default:
            session.sessionPreset = .hd1280x720
        }

        // lens
        let position: AVCaptureDevice.Position = (lens == "FRONT") ? .front : .back

        guard let device = AVCaptureDevice.default(
            .builtInWideAngleCamera,
            for: .video,
            position: position
        ) else {
            session.commitConfiguration()
            throw NSError(domain: "QrCodeScanner", code: 1, userInfo: [NSLocalizedDescriptionKey: "No camera device"])
        }

        // clear old
        for input in session.inputs { session.removeInput(input) }
        for output in session.outputs { session.removeOutput(output) }

        // input
        let input = try AVCaptureDeviceInput(device: device)
        guard session.canAddInput(input) else {
            session.commitConfiguration()
            throw NSError(domain: "QrCodeScanner", code: 2, userInfo: [NSLocalizedDescriptionKey: "Cannot add camera input"])
        }
        session.addInput(input)

        // output
        let output = AVCaptureVideoDataOutput()
        output.setSampleBufferDelegate(self, queue: queue)
        guard session.canAddOutput(output) else {
            session.commitConfiguration()
            throw NSError(domain: "QrCodeScanner", code: 3, userInfo: [NSLocalizedDescriptionKey: "Cannot add camera output"])
        }
        session.addOutput(output)

        currentDevice = device

        session.commitConfiguration()

        // preview layer
        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.frame = previewView.bounds

        previewView.layer.sublayers?.forEach { $0.removeFromSuperlayer() }
        previewView.layer.addSublayer(layer)
        previewLayer = layer

        paused = false

        if !session.isRunning {
            session.startRunning()
        }
    }

    func updatePreviewFrame(_ frame: CGRect) {
        previewLayer?.frame = frame
    }

    // MARK: - Stop / Pause / Resume

    func stop() {
        paused = true
        if session.isRunning {
            session.stopRunning()
        }
        previewLayer?.removeFromSuperlayer()
        previewLayer = nil
    }

    func pause() { paused = true }
    func resume() { paused = false }

    // MARK: - Torch

    func isTorchAvailable() -> Bool {
        return currentDevice?.hasTorch == true
    }

    func isTorchEnabled() -> Bool {
        return currentDevice?.torchMode == .on
    }

    func enableTorch() {
        guard let device = currentDevice, device.hasTorch else { return }
        do {
            try device.lockForConfiguration()
            device.torchMode = .on
            device.unlockForConfiguration()
        } catch {
            onError?("Torch error")
        }
    }

    func disableTorch() {
        guard let device = currentDevice, device.hasTorch else { return }
        do {
            try device.lockForConfiguration()
            device.torchMode = .off
            device.unlockForConfiguration()
        } catch {
            onError?("Torch error")
        }
    }

    func toggleTorch() {
        guard let device = currentDevice, device.hasTorch else { return }
        do {
            try device.lockForConfiguration()
            device.torchMode = (device.torchMode == .on) ? .off : .on
            device.unlockForConfiguration()
        } catch {
            onError?("Torch error")
        }
    }

    // MARK: - Zoom

    func setZoom(_ ratio: CGFloat) {
        guard let device = currentDevice else { return }
        let maxZoom = device.activeFormat.videoMaxZoomFactor
        let clamped = min(max(1.0, ratio), maxZoom)
        do {
            try device.lockForConfiguration()
            device.videoZoomFactor = clamped
            device.unlockForConfiguration()
        } catch {
            onError?("Zoom error")
        }
    }

    func getZoomRatio() -> CGFloat {
        return currentDevice?.videoZoomFactor ?? 1.0
    }

    func getMinZoomRatio() -> CGFloat {
        return 1.0
    }

    func getMaxZoomRatio() -> CGFloat {
        return currentDevice?.activeFormat.videoMaxZoomFactor ?? 1.0
    }

    // MARK: - Capture output (Vision)

    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        if paused { return }

        let request = VNDetectBarcodesRequest { [weak self] req, err in
            if let err = err {
                self?.onError?(err.localizedDescription)
                return
            }
            let results = req.results as? [VNBarcodeObservation] ?? []
            self?.onResult?(results)
        }

        // Рабочий дефолт. Если нужно идеально по ориентации/фронт-камере — сделаем.
        let handler = VNImageRequestHandler(cmSampleBuffer: sampleBuffer, orientation: .up)

        do {
            try handler.perform([request])
        } catch {
            onError?(error.localizedDescription)
        }
    }
}
