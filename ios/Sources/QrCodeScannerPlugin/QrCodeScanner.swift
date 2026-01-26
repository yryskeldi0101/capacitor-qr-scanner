import AVFoundation
import Vision
import UIKit

final class QrCodeScanner: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {

    private let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "qr.camera.queue")
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var paused = false

    var onResult: (([VNBarcodeObservation]) -> Void)?
    var onError: ((String) -> Void)?

    // MARK: - Start

    func start(
        previewView: UIView,
        lens: String,
        resolution: Int
    ) throws {

        session.beginConfiguration()

        // ---- resolution ----
        switch resolution {
        case 0:
            session.sessionPreset = .vga640x480
        case 2:
            session.sessionPreset = .hd1920x1080
        default:
            session.sessionPreset = .hd1280x720
        }

        // ---- camera ----
        let position: AVCaptureDevice.Position =
            lens == "FRONT" ? .front : .back

        guard let device = AVCaptureDevice.default(
            .builtInWideAngleCamera,
            for: .video,
            position: position
        ) else {
            throw NSError(domain: "Camera", code: 0)
        }

        session.inputs.forEach { session.removeInput($0) }

        let input = try AVCaptureDeviceInput(device: device)
        session.addInput(input)

        // ---- output ----
        let output = AVCaptureVideoDataOutput()
        output.setSampleBufferDelegate(self, queue: queue)
        session.addOutput(output)

        session.commitConfiguration()

        // ---- preview ----
        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.frame = previewView.bounds

        previewView.layer.sublayers?.forEach { $0.removeFromSuperlayer() }
        previewView.layer.addSublayer(layer)
        previewLayer = layer

        session.startRunning()
    }

    // MARK: - Stop

    func stop() {
        session.stopRunning()
    }

    // MARK: - Pause / Resume

    func pause() {
        paused = true
    }

    func resume() {
        paused = false
    }

    // MARK: - Zoom

    func setZoom(_ ratio: CGFloat) {
        guard
            let device = (session.inputs.first as? AVCaptureDeviceInput)?.device
        else { return }

        try? device.lockForConfiguration()
        device.videoZoomFactor = min(
            max(1.0, ratio),
            device.activeFormat.videoMaxZoomFactor
        )
        device.unlockForConfiguration()
    }

    // MARK: - Capture

    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        if paused { return }

        let request = VNDetectBarcodesRequest { [weak self] request, _ in
            if let results = request.results as? [VNBarcodeObservation] {
                self?.onResult?(results)
            }
        }

        let handler = VNImageRequestHandler(
            cmSampleBuffer: sampleBuffer,
            orientation: .up
        )

        try? handler.perform([request])
    }
}
