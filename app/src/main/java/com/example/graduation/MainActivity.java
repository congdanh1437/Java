package com.example.graduation;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.graduation.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements Detector.DetectorListener {
    private ActivityMainBinding binding;
    private final boolean isFrontCamera = false;

    private Preview preview;
    private ImageAnalysis imageAnalyzer;
    private Camera camera;
    private ProcessCameraProvider cameraProvider;
    private Detector detector;

    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        detector = new Detector(getBaseContext(), Constants.MODEL_PATH, Constants.LABELS_PATH, this);
        detector.setup(this);

        if(OpenCVLoader.initDebug()) {
            Log.d(TAG, "SUCESSS");
        }else {Log.d(TAG, "FAIL");}


        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Camera provider initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }


    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            throw new IllegalStateException("Camera initialization failed.");
        }

        int rotation = binding.viewFinder.getDisplay().getRotation();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .build();

        imageAnalyzer = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(binding.viewFinder.getDisplay().getRotation())
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();

        imageAnalyzer.setAnalyzer(cameraExecutor, imageProxy -> {
            // Get the dimensions of the image
            int width = imageProxy.getWidth();
            int height = imageProxy.getHeight();

            // Create a Bitmap with the same dimensions as the image
            Bitmap bitmapBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            // Copy pixel data from the buffer to the Bitmap
            ImageProxy.PlaneProxy planeProxy = imageProxy.getPlanes()[0];
            ByteBuffer buffer = planeProxy.getBuffer();
            buffer.rewind();
            bitmapBuffer.copyPixelsFromBuffer(buffer);

            // Close the ImageProxy after using its data
            imageProxy.close();

            // Create a matrix to handle image rotation
            Matrix matrix = new Matrix();
            matrix.postRotate((float) imageProxy.getImageInfo().getRotationDegrees());

            // If using the front camera, flip the image horizontally
            if (isFrontCamera) {
                matrix.postScale(
                        -1f,
                        1f,
                        width / 2f,
                        height / 2f
                );
            }

            // Rotate and/or flip the Bitmap
            Bitmap rotatedBitmap = Bitmap.createBitmap(
                    bitmapBuffer, 0, 0, width, height,
                    matrix, true
            );

            // Pass the rotated Bitmap to the detector for further processing
            detector.detect(rotatedBitmap);
        });



        cameraProvider.unbindAll();

        try {
            camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
            );

            preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());
        } catch (Exception exc) {
            Log.e(TAG, "Use case binding failed", exc);
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(getBaseContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private final ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA))) {
                    startCamera();
                }
            });

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detector.clear();
        cameraExecutor.shutdown();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS);
        }
    }

    @Override
    public void onEmptyDetect() {
        runOnUiThread(() -> binding.overlay.clear());
    }




    @Override
    public void onDetect(List<BoundingBox> boundingBoxes, long inferenceTime) {
        runOnUiThread(() -> {
            binding.inferenceTime.setText(inferenceTime + "ms");
            binding.overlay.setResults(boundingBoxes);
            binding.overlay.invalidate();
        });
    }

    @Override
    public void onObjectImageSaved(String fileName) {
    }

    private static final String TAG = "Camera";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA
    };
}