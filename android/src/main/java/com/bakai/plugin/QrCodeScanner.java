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
import androidx.lifecycle.Observer;

import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QrCodeScanner {

    private final Context context;
    private final BarcodeScanner scanner;

    private ExecutorService cameraExecutor;
    private final Executor mainExecutor;

    private ProcessCameraProvider provider;
    private Camera camera;
    private ImageAnalysis analysis;
    private Preview preview;

    private boolean paused = false;

    // zoom readiness + pending zoom
    private volatile boolean zoomReady = false;
    private volatile Float pendingZoomRatio = null;

    // ✅ сохраняем analyzer и surface provider, чтобы ставить на паузу/резюмить без unbind/bind
    private ImageAnalysis.Analyzer analyzer;
    private Preview.SurfaceProvider realSurfaceProvider;
    private Preview.SurfaceProvider pausedSurfaceProvider;

    private boolean analyzerAttached = false;

    public interface Callback {
        void onBarcodes(List<Barcode> barcodes);
        void onError(String message);
        void onZoomReady(float minRatio, float maxRatio, float currentRatio);
    }

    public QrCodeScanner(Context context) {
        this.context = context;

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();

        scanner = BarcodeScanning.getClient(options);
        cameraExecutor = Executors.newSingleThreadExecutor();
        mainExecutor = ContextCompat.getMainExecutor(context);

        // ✅ SurfaceProvider для "паузы": говорим CameraX что Surface не будет
        pausedSurfaceProvider = (surfaceRequest) -> surfaceRequest.willNotProvideSurface();
    }

    @SuppressLint("UnsafeOptInUsageError")
    public void start(LifecycleOwner owner, PreviewView previewView, String lensFacing, int resolution, Callback callback) {
        ProcessCameraProvider.getInstance(context).addListener(
                () -> {
                    try {
                        provider = ProcessCameraProvider.getInstance(context).get();

                        Size targetSize;
                        switch (resolution) {
                            case 0: targetSize = new Size(640, 480); break;
                            case 2: targetSize = new Size(1920, 1080); break;
                            default: targetSize = new Size(1280, 720);
                        }

                        CameraSelector selector = "FRONT".equals(lensFacing)
                                ? CameraSelector.DEFAULT_FRONT_CAMERA
                                : CameraSelector.DEFAULT_BACK_CAMERA;

                        preview = new Preview.Builder().build();

                        // ✅ сохраняем реальный surface provider от PreviewView
                        realSurfaceProvider = previewView.getSurfaceProvider();
                        preview.setSurfaceProvider(realSurfaceProvider);

                        analysis = new ImageAnalysis.Builder()
                                .setTargetResolution(targetSize)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build();

                        analyzer = (image) -> {
                            try {
                                if (paused || image.getImage() == null) {
                                    image.close();
                                    return;
                                }

                                InputImage inputImage = InputImage.fromMediaImage(
                                        image.getImage(),
                                        image.getImageInfo().getRotationDegrees()
                                );

                                scanner.process(inputImage)
                                        .addOnSuccessListener(callback::onBarcodes)
                                        .addOnFailureListener(e -> callback.onError(e.getMessage()))
                                        .addOnCompleteListener(t -> image.close());

                            } catch (Exception e) {
                                image.close();
                                callback.onError(e.getMessage());
                            }
                        };

                        analysis.setAnalyzer(cameraExecutor, analyzer);

                        analyzerAttached = true;
                        provider.unbindAll();
                        camera = provider.bindToLifecycle(owner, selector, preview, analysis);

                        observeZoomState(owner, callback);

                    } catch (Exception e) {
                        callback.onError(e.getMessage());
                    }
                },
                mainExecutor
        );
    }

    private void observeZoomState(LifecycleOwner owner, Callback callback) {
        if (camera == null) return;

        camera.getCameraInfo().getZoomState().observe(
                owner,
                new Observer<ZoomState>() {
                    @Override
                    public void onChanged(ZoomState zs) {
                        if (zs == null) return;

                        if (!zoomReady) {
                            zoomReady = true;
                            callback.onZoomReady(zs.getMinZoomRatio(), zs.getMaxZoomRatio(), zs.getZoomRatio());

                            if (pendingZoomRatio != null) {
                                setZoomRatio(pendingZoomRatio);
                                pendingZoomRatio = null;
                            }
                        }
                    }
                }
        );
    }

    public void stop() {
        paused = true;

        mainExecutor.execute(() -> {
            try {
                if (provider != null) provider.unbindAll();
            } catch (Exception ignored) {}

            provider = null;
            camera = null;
            analysis = null;
            preview = null;

            zoomReady = false;
            pendingZoomRatio = null;

            analyzer = null;
            realSurfaceProvider = null;
        });

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }
    }

    /**
     * ✅ PAUSE без unbind/bind:
     * 1) Останавливаем поток камеры в Preview (willNotProvideSurface)
     * 2) Убираем analyzer, чтобы анализ точно не шёл
     */
    public void pause() {
        paused = true;
        if (analysis != null && analyzerAttached) {
            mainExecutor.execute(() -> {
                try {
                    analysis.clearAnalyzer();
                } catch (Exception ignored) {}
                analyzerAttached = false;
            });
        }
    }

    /**
     * ✅ RESUME без unbind/bind:
     * 1) Возвращаем SurfaceProvider обратно
     * 2) Возвращаем analyzer обратно
     */
    public void resume() {
        paused = false;
        if (analysis != null && cameraExecutor != null && analyzer != null && !analyzerAttached) {
            mainExecutor.execute(() -> {
                try {
                    analysis.setAnalyzer(cameraExecutor, analyzer);
                } catch (Exception ignored) {}
                analyzerAttached = true;
            });
        }
    }

    // ===== Torch =====

    public boolean isTorchAvailable() {
        return camera != null && camera.getCameraInfo().hasFlashUnit();
    }

    public boolean isZoomReady() {
        return camera != null && camera.getCameraInfo().getZoomState().getValue() != null;
    }

    public boolean isTorchEnabled() {
        if (camera == null) return false;
        Integer state = camera.getCameraInfo().getTorchState().getValue();
        return state != null && state == TorchState.ON;
    }

    public void enableTorch(boolean enabled) {
        if (camera == null) return;
        mainExecutor.execute(() -> camera.getCameraControl().enableTorch(enabled));
    }

    public void enableTorch() { enableTorch(true); }
    public void disableTorch() { enableTorch(false); }

    public void toggleTorch() {
        if (!isTorchAvailable()) return;
        enableTorch(!isTorchEnabled());
    }

    // ===== Zoom =====

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
        if (camera == null || !zoomReady) {
            pendingZoomRatio = ratio;
            return;
        }

        mainExecutor.execute(() -> {
            if (camera == null) return;

            ZoomState zs = camera.getCameraInfo().getZoomState().getValue();
            if (zs == null) {
                pendingZoomRatio = ratio;
                zoomReady = false;
                return;
            }

            float clamped = Math.max(zs.getMinZoomRatio(), Math.min(zs.getMaxZoomRatio(), ratio));
            camera.getCameraControl().setZoomRatio(clamped);
        });
    }
}
