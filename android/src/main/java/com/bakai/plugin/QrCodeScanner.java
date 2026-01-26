package com.bakai.plugin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Size;
import androidx.camera.core.*;
import androidx.camera.core.TorchState;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QrCodeScanner {

    private final Context context;
    private final BarcodeScanner scanner;

    private ExecutorService cameraExecutor;
    private Camera camera;
    private ImageAnalysis analysis;
    private boolean paused = false;

    public interface Callback {
        void onBarcodes(List<Barcode> barcodes);
        void onError(String message);
    }

    public QrCodeScanner(Context context) {
        this.context = context;

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build();

        scanner = BarcodeScanning.getClient(options);
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @SuppressLint("UnsafeOptInUsageError")
    public void start(LifecycleOwner owner, PreviewView previewView, String lensFacing, int resolution, Callback callback) {
        ProcessCameraProvider.getInstance(context).addListener(
            () -> {
                try {
                    ProcessCameraProvider provider = ProcessCameraProvider.getInstance(context).get();

                    Size targetSize;
                    switch (resolution) {
                        case 0:
                            targetSize = new Size(640, 480);
                            break;
                        case 2:
                            targetSize = new Size(1920, 1080);
                            break;
                        default:
                            targetSize = new Size(1280, 720);
                    }

                    CameraSelector selector = "FRONT".equals(lensFacing)
                        ? CameraSelector.DEFAULT_FRONT_CAMERA
                        : CameraSelector.DEFAULT_BACK_CAMERA;

                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());

                    analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(targetSize)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                    analysis.setAnalyzer(cameraExecutor, (image) -> {
                        if (paused || image.getImage() == null) {
                            image.close();
                            return;
                        }

                        InputImage inputImage = InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());

                        scanner
                            .process(inputImage)
                            .addOnSuccessListener(callback::onBarcodes)
                            .addOnFailureListener((e) -> callback.onError(e.getMessage()))
                            .addOnCompleteListener((t) -> image.close());
                    });

                    provider.unbindAll();

                    camera = provider.bindToLifecycle(owner, selector, preview, analysis);
                } catch (Exception e) {
                    callback.onError(e.getMessage());
                }
            },
            ContextCompat.getMainExecutor(context)
        );
    }

    public void stop() {
        paused = true;
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    public void enableTorch(boolean enabled) {
        if (camera != null) {
            camera.getCameraControl().enableTorch(enabled);
        }
    }

    public void setZoom(float ratio) {
        if (camera != null) {
            camera.getCameraControl().setZoomRatio(ratio);
        }
    }

    /// ///

    public boolean isTorchAvailable() {
        return camera != null && camera.getCameraInfo().hasFlashUnit();
    }

    public boolean isTorchEnabled() {
        if (camera == null) return false;
        Integer state = camera.getCameraInfo().getTorchState().getValue();
        return state != null && state == TorchState.ON;
    }

    public void enableTorch() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            camera.getCameraControl().enableTorch(true);
        }
    }

    public void disableTorch() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            camera.getCameraControl().enableTorch(false);
        }
    }

    public void toggleTorch() {
        if (!isTorchAvailable()) return;
        camera.getCameraControl().enableTorch(!isTorchEnabled());
    }

    public float getZoomRatio() {
        if (camera == null) return 1f;
        ZoomState zs = camera.getCameraInfo().getZoomState().getValue();
        return zs != null ? zs.getZoomRatio() : 1f;
    }

    public float getMinZoomRatio() {
        if (camera == null) return 1f;
        ZoomState zs = camera.getCameraInfo().getZoomState().getValue();
        return zs != null ? zs.getMinZoomRatio() : 1f;
    }

    public float getMaxZoomRatio() {
        if (camera == null) return 1f;
        ZoomState zs = camera.getCameraInfo().getZoomState().getValue();
        return zs != null ? zs.getMaxZoomRatio() : 1f;
    }

    public void setZoomRatio(float ratio) {
        if (camera == null) return;
        ZoomState zs = camera.getCameraInfo().getZoomState().getValue();
        if (zs == null) {
            camera.getCameraControl().setZoomRatio(ratio);
            return;
        }
        float clamped = Math.max(zs.getMinZoomRatio(), Math.min(zs.getMaxZoomRatio(), ratio));
        camera.getCameraControl().setZoomRatio(clamped);
    }
}
