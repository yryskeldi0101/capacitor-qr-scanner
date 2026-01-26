import Capacitor
import AVFoundation
import UIKit

@objc(QrCodeScannerPlugin)
public class QrCodeScannerPlugin: CAPPlugin {

    private let scanner = QrCodeScanner()
    private var previewView: UIView?

    // MARK: - startScan

    @objc func startScan(_ call: CAPPluginCall) {

        let lens =
            call.getObject("options")?
                .getString("lensFacing") ?? "BACK"

        let resolution =
            call.getObject("options")?
                .getInt("resolution") ?? 1

        DispatchQueue.main.async {

            self.previewView = UIView(frame: UIScreen.main.bounds)
            self.bridge?.viewController?.view.addSubview(self.previewView!)

            self.scanner.onResult = { barcodes in
                self.notifyListeners(
                    "barcodesScanned",
                    BarcodeMapper.toJS(barcodes)
                )
            }

            do {
                try self.scanner.start(
                    previewView: self.previewView!,
                    lens: lens,
                    resolution: resolution
                )
                call.resolve()
            } catch {
                call.reject("Camera error")
            }
        }
    }

    // MARK: - stopScan

    @objc func stopScan(_ call: CAPPluginCall) {
        scanner.stop()
        previewView?.removeFromSuperview()
        previewView = nil
        call.resolve()
    }

    // MARK: - pause / resume

    @objc func pauseScan(_ call: CAPPluginCall) {
        scanner.pause()
        call.resolve()
    }

    @objc func resumeScan(_ call: CAPPluginCall) {
        scanner.resume()
        call.resolve()
    }

    // MARK: - zoom

    @objc func setZoomRatio(_ call: CAPPluginCall) {
        let ratio = call.getFloat("zoomRatio") ?? 1
        scanner.setZoom(CGFloat(ratio))
        call.resolve()
    }

    // MARK: - readBarcodesFromImage

    @objc func readBarcodesFromImage(_ call: CAPPluginCall) {

        guard let path = call.getString("path") else {
            call.reject("No path provided")
            return
        }

        let url = URL(fileURLWithPath: path)

        let request = VNDetectBarcodesRequest { req, _ in
            let results =
                req.results as? [VNBarcodeObservation] ?? []
            call.resolve(BarcodeMapper.toJS(results))
        }

        let handler = VNImageRequestHandler(url: url)
        try? handler.perform([request])
    }

    // MARK: - scan (modal)

    @objc func scan(_ call: CAPPluginCall) {

        DispatchQueue.main.async {

            let view = UIView(frame: UIScreen.main.bounds)
            self.bridge?.viewController?.view.addSubview(view)

            self.scanner.onResult = { barcodes in
                call.resolve(BarcodeMapper.toJS(barcodes))
                self.scanner.stop()
                view.removeFromSuperview()
            }

            try? self.scanner.start(
                previewView: view,
                lens: "BACK",
                resolution: 1
            )
        }
    }

    // MARK: - permissions

    @objc func checkPermissions(_ call: CAPPluginCall) {
        let status = AVCaptureDevice.authorizationStatus(for: .video)

        let value: String =
            status == .authorized ? "granted" :
            status == .limited ? "limited" :
            "denied"

        call.resolve(["camera": value])
    }

    @objc func requestPermissions(_ call: CAPPluginCall) {
        AVCaptureDevice.requestAccess(for: .video) { _ in
            self.checkPermissions(call)
        }
    }

    @objc func openSettings(_ call: CAPPluginCall) {
        guard let url = URL(string: UIApplication.openSettingsURLString) else {
            call.reject("Cannot open settings")
            return
        }
        UIApplication.shared.open(url)
        call.resolve()
    }
}
