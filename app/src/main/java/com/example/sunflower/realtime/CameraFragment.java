package com.example.sunflower.realtime;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.sunflower.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CameraFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CameraFragment extends Fragment {

   private ConnectionCallback connectionCallback;
   private ImageReader.OnImageAvailableListener imageAvailableListener;
   private Size inputSize;
   private String cameraID;



    private CameraFragment(final ConnectionCallback callback,
                           final ImageReader.OnImageAvailableListener imageAvailableListener,
                           final Size inputSize,
                           final String cameraID) {

        this.connectionCallback = callback;
        this.imageAvailableListener = imageAvailableListener;
        this.inputSize = inputSize;
        this.cameraID = cameraID;
    }

    public interface ConnectionCallback {
        void onPreviewSizeChosen(Size size, int cameraRotation);
    }

    public static CameraFragment newInstance(final ConnectionCallback callback,
                                             final ImageReader.OnImageAvailableListener imageAvailableListener,
                                             final Size inputSize,
                                             final String cameraID) {

        return new CameraFragment(callback, imageAvailableListener, inputSize, cameraID);
    }

    private AutoFitTextureView autoFitTextureView = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        autoFitTextureView = view.findViewById(R.id.autoFitTextureView);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        if(!autoFitTextureView.isAvailable())
            autoFitTextureView.setSurfaceTextureListener(surfaceTextureListener);
        else
            openCamera(autoFitTextureView.getWidth(), autoFitTextureView.getHeight());
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @RequiresApi(api = Build.VERSION_CODES.R)
                @Override
                public void onSurfaceTextureAvailable(final SurfaceTexture texture, final int width, final int height) {
                    openCamera(width, height);
                }

                @RequiresApi(api = Build.VERSION_CODES.R)
                @Override
                public void onSurfaceTextureSizeChanged(final SurfaceTexture texture, final int width, final int height) {
                    configureTransform(width,height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
                }
            };

    private Semaphore cameraOpenCloseLock = new Semaphore(1);

    @RequiresApi(api = Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    private void openCamera(final int width, final int height) {
        final Activity activity= getActivity();
        final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        setupCameraOutputs(manager);
        configureTransform(width, height);

        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                Toast.makeText(getContext(),
                        "Time out waiting to lock camera opening.",
                        Toast.LENGTH_LONG).show();
                activity.finish();
            } else {
                manager.openCamera(cameraID, stateCallback, backgroundHandler);
            }
        } catch (final InterruptedException | CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Size previewSize;
    private void setupCameraOutputs(CameraManager manager) {
        int sensorOrientation = 0;
        try {
            final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraID);

            final StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    inputSize.getWidth(),
                    inputSize.getHeight());

            final int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                autoFitTextureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
            } else {
                autoFitTextureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
            }
        } catch (final CameraAccessException cae) {
            cae.printStackTrace();
        }

        connectionCallback.onPreviewSizeChosen(previewSize, sensorOrientation);
    }

    protected Size chooseOptimalSize(final Size[] choices, final int width, final int height) {

        final int minSize = Math.min(width, height);
        final Size desiredSize = new Size(width,height);

        final List<Size> bigEnough = new ArrayList<Size>();
        final List<Size> tooSmall = new ArrayList<Size>();
        for (final Size option : choices) {
            if (option.equals(desiredSize)) {
                return desiredSize;
            }

            if (option.getWidth() >= minSize && option.getWidth() >= minSize) {
                bigEnough.add(option);
            } else {
                tooSmall.add(option);
            }
        }

        if(bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return Collections.max(tooSmall, new CompareSizesByArea());
        }
    }


    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(final Size lhs, final Size rhs) {
            return Long.signum(
                    (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() *
                            rhs.getHeight());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void configureTransform(final int viewWidth, final int viewHeight) {
        final Activity activity = getActivity();
        if (null == autoFitTextureView || null == previewSize || null == activity) {
            return;
        }

        final int rotation = activity.getDisplay().getRotation();
        final Matrix matrix = new Matrix();
        final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        final float centerX = viewRect.centerX();
        final float centerY = viewRect.centerY();

        if(Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(
                    centerX - bufferRect.centerX(),
                    centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            final float scale = Math.max((float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        autoFitTextureView.setTransform(matrix);
    }

    private CameraDevice cameraDevice;

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(final CameraDevice cd) {
            cameraOpenCloseLock.release();
            cameraDevice = cd;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(final CameraDevice cd) {
            cameraOpenCloseLock.release();
            cd.close();
            cameraDevice = null;
        }

        @Override
        public void onError(final CameraDevice cd, final int error) {
            cameraOpenCloseLock.release();
            cd.close();
            cameraDevice = null;
            final Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }
    };

    private ImageReader previewReader;
    private CaptureRequest.Builder previewRequestBuilder;
    private void createCameraPreviewSession() {
        try {
            final SurfaceTexture texture = autoFitTextureView.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            final Surface surface = new Surface(texture);

            previewReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            previewReader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            previewRequestBuilder.addTarget(previewReader.getSurface());

            previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            cameraDevice.createCaptureSession(
                    Arrays.asList(surface, previewReader.getSurface()),
                    sessionStateCallback,
                    null);
        } catch (final CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private CameraCaptureSession captureSession;
    private final CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
            if (null == cameraDevice) {
                return;
            }
            captureSession = cameraCaptureSession;
            try {
                captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, null);
            } catch (final CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
            Toast.makeText(getActivity(), "CameraCaptureSession Failed", Toast.LENGTH_SHORT).show();
        }

    };

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (null != captureSession) {
                captureSession.close();
                captureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != previewReader) {
                previewReader.close();
                previewReader = null;
            }
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    private HandlerThread backgroundThread = null;
    private Handler backgroundHandler = null;

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("ImageListener");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
    }
}