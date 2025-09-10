package com.jacob.fruitoftek.localfacerecognition;

import android.Manifest;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.jacob.fruitoftek.localfacerecognition.Drawing.BorderedText;
import com.jacob.fruitoftek.localfacerecognition.Drawing.MultiBoxTracker;
import com.jacob.fruitoftek.localfacerecognition.Drawing.OverlayView;
import com.jacob.fruitoftek.localfacerecognition.Face_Recognition.FaceClassifier;
import com.jacob.fruitoftek.localfacerecognition.Face_Recognition.TFLiteFaceRecognition;
import com.jacob.fruitoftek.localfacerecognition.LiveFeed.CameraConnectionFragment;
import com.jacob.fruitoftek.localfacerecognition.LiveFeed.ImageUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity implements ImageReader.OnImageAvailableListener{

    private HandlerThread inferenceThread;
    private Handler inferenceHandler;
    private Matrix frameToCropTransform;
    private int sensorOrientation;
    private Matrix cropToFrameTransform;

    private static final boolean MAINTAIN_ASPECT = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private BorderedText borderedText;
    private MultiBoxTracker tracker;
    private Integer useFacing = null;
    private static final String KEY_USE_FACING = "use_facing";
    private static final int CROP_SIZE = 1000;
    private static final int TF_OD_API_INPUT_SIZE2 = 160;

    // TODO declare face detector
    FaceDetector detector;

    // TODO declare face recognizer
    private FaceClassifier faceClassifier;

    boolean registerFace = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        //TODO handling permissions
        Intent intent = getIntent();
        useFacing = intent.getIntExtra(KEY_USE_FACING, CameraCharacteristics.LENS_FACING_BACK);

        //TODO handling permissions and show live camera footage
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
            //TODO show live camera footage
            setFragment();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String[] permission = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
                requestPermissions(permission, 121);
            }
        }

        //TODO initialize the tracker to draw rectangles
        tracker = new MultiBoxTracker(this);

        //TODO initalize face detector
        // Multiple object detection in static images
        FaceDetectorOptions highAccuracyOpts =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                        .build();
        detector = FaceDetection.getClient(highAccuracyOpts);

        //TODO initialize FACE Recognition
        try {
            faceClassifier =
                    TFLiteFaceRecognition.create(
                            getAssets(),
                            "facenet.tflite",
                            TF_OD_API_INPUT_SIZE2,
                            false,getApplicationContext());

        } catch (final IOException e) {
            e.printStackTrace();
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        findViewById(R.id.imageView4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                registerFace = true;
            }
        });

        findViewById(R.id.imageView3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchCamera();
            }
        });

        findViewById(R.id.imageViewList).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Ketika ImageView List di klik, buka aktivitas baru untuk menampilkan daftar nama dari database
                Intent intent = new Intent(MainActivity.this, DaftarNamaActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        inferenceThread = new HandlerThread("InferenceThread");
        inferenceThread.start();
        inferenceHandler = new Handler(inferenceThread.getLooper());
    }

    private void stopInferenceThread() {
        if (inferenceThread != null) {
            inferenceThread.quitSafely();
            try {
                inferenceThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            inferenceThread = null;
            inferenceHandler = null;
        }
    }

    @Override
    protected void onPause() {
        stopInferenceThread();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == 121) {
                boolean cameraPermissionGranted = false;
                boolean storagePermissionGranted = false;
                for (int i = 0; i < permissions.length; i++) {
                    if (Manifest.permission.CAMERA.equals(permissions[i])) {
                        cameraPermissionGranted = grantResults.length > i && grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    } else if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissions[i])) {
                        storagePermissionGranted = grantResults.length > i && grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    }
                }
                if (cameraPermissionGranted && storagePermissionGranted) {
                    setFragment();
                }
            }
        }

    //TODO fragment which show live footage from camera
    int previewHeight = 0,previewWidth = 0;
    protected void setFragment() {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String cameraId = null;
        try {
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == useFacing) {
                    cameraId = id;
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        if (cameraId == null) {
            try {
                String[] cameraIds = manager.getCameraIdList();
                if (cameraIds.length > 0) {
                    cameraId = cameraIds[0];
                } else {
                    Toast.makeText(this, "No camera available", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
                Toast.makeText(this, "Cannot access cameras", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        CameraConnectionFragment camera2Fragment =
                CameraConnectionFragment.newInstance(
                        new CameraConnectionFragment.ConnectionCallback() {
                            @Override
                            public void onPreviewSizeChosen(final Size size, final int rotation) {
                                previewHeight = size.getHeight();
                                previewWidth = size.getWidth();

                                final float textSizePx =
                                        TypedValue.applyDimension(
                                                TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
                                borderedText = new BorderedText(textSizePx);
                                borderedText.setTypeface(Typeface.MONOSPACE);

                                int cropSize = CROP_SIZE;

                                previewWidth = size.getWidth();
                                previewHeight = size.getHeight();

                                sensorOrientation = rotation - getScreenOrientation();

                                rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
                                croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

                                frameToCropTransform =
                                        ImageUtils.getTransformationMatrix(
                                                previewWidth, previewHeight,
                                                cropSize, cropSize,
                                                sensorOrientation, MAINTAIN_ASPECT);

                                cropToFrameTransform = new Matrix();
                                frameToCropTransform.invert(cropToFrameTransform);

                                trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
                                trackingOverlay.addCallback(
                                        new OverlayView.DrawCallback() {
                                            @Override
                                            public void drawCallback(final Canvas canvas) {
                                                tracker.draw(canvas);
                                                Log.d("tryDrawRect","inside draw");
                                            }
                                        });
                                tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
                            }
                        },
                        this,
                        R.layout.camera_fragment,
                        new Size(640, 480));

        camera2Fragment.setCamera(cameraId);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, camera2Fragment)
                .commit();
    }

    //TODO getting frames of live camera footage and passing them to model
    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private Bitmap rgbFrameBitmap;
    Bitmap croppedBitmap;
    @Override
    public void onImageAvailable(ImageReader reader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
        }
        try {
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (isProcessingFrame) {
                image.close();
                return;
            }
            isProcessingFrame = true;
            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageUtils.convertYUV420ToARGB8888(
                                    yuvBytes[0],
                                    yuvBytes[1],
                                    yuvBytes[2],
                                    previewWidth,
                                    previewHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    rgbBytes);
                        }
                    };

            postInferenceCallback =
                    new Runnable() {
                        @Override
                        public void run() {
                            image.close();
                            isProcessingFrame = false;
                        }
                    };

            if (inferenceHandler != null) {
                inferenceHandler.post(
                        new Runnable() {
                            @Override
                            public void run() {
                                imageConverter.run();
                                performFaceDetection();
                            }
                        });
            } else {
                image.close();
                isProcessingFrame = false;
            }
        } catch (final Exception e) {
            Log.d("tryError",e.getMessage()+"abc ");
            return;
        }

    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }
    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    List<FaceClassifier.Recognition> mappedRecognitions;

    //TODO Perform face detection
    public void performFaceDetection(){
        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        mappedRecognitions = new ArrayList<>();
        InputImage image = InputImage.fromBitmap(croppedBitmap,0);

        final Handler handler = inferenceHandler;
        detector.process(image)
                .addOnSuccessListener(
                        new Executor() {
                            @Override
                            public void execute(Runnable command) {
                                if (handler != null) {
                                    handler.post(command);
                                }
                            }
                        },
                        new OnSuccessListener<List<Face>>() {
                            @Override
                            public void onSuccess(List<Face> faces) {

                                for (Face face : faces) {
                                    final Rect bounds = face.getBoundingBox();
                                    performFaceRecognition(face, croppedBitmap);
                                }
                                registerFace = false;
                                tracker.trackResults(mappedRecognitions, 10);
                                trackingOverlay.postInvalidate();
                                if (handler != null) {
                                    handler.post(postInferenceCallback);
                                }
                            }
                        })
                .addOnFailureListener(
                        new Executor() {
                            @Override
                            public void execute(Runnable command) {
                                if (handler != null) {
                                    handler.post(command);
                                }
                            }
                        },
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Task failed with an exception
                                // ...
                                if (handler != null) {
                                    handler.post(postInferenceCallback);
                                }
                            }
                        });
    }

    //TODO perform face recognition
    public void performFaceRecognition(Face face,Bitmap input){
        //TODO crop the face
        Rect bounds = face.getBoundingBox();
        if(bounds.top<0){
            bounds.top = 0;
        }
        if(bounds.left<0){
            bounds.left = 0;
        }
        if(bounds.left+bounds.width()>input.getWidth()){
            bounds.right = input.getWidth()-1;
        }
        if(bounds.top+bounds.height()>input.getHeight()){
            bounds.bottom = input.getHeight()-1;
        }

        int faceHeight = Math.max(1, bounds.height() - 30);
        Bitmap crop = Bitmap.createBitmap(input,
                bounds.left,
                bounds.top,
                bounds.width(),
                faceHeight);
        crop = Bitmap.createScaledBitmap(crop,TF_OD_API_INPUT_SIZE2,TF_OD_API_INPUT_SIZE2,false);
        final Bitmap faceBitmap = crop;

        final FaceClassifier.Recognition result = faceClassifier.recognizeImage(faceBitmap, registerFace);
        String title = "Unknown";
        float confidence = 0;
        if (result != null) {
            if (registerFace) {
                registerFaceDialogue(crop,result);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        registerFaceDialogue(faceBitmap,result);
                    }
                });
            } else {
                if (result.getDistance() < 0.75f) {
                    confidence = result.getDistance();
                    title = result.getTitle();
                }
            }
        }

        final RectF location = new RectF(bounds);
        if (bounds != null) {
            if (useFacing == CameraCharacteristics.LENS_FACING_BACK) {
                location.right = input.getWidth() - location.right;
                location.left = input.getWidth() - location.left;
            }
            cropToFrameTransform.mapRect(location);
            final String finalTitle = title;
            final float finalConfidence = confidence;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    FaceClassifier.Recognition recognition = new FaceClassifier.Recognition(face.getTrackingId()+"", finalTitle, finalConfidence, location);
                    mappedRecognitions.add(recognition);
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        stopInferenceThread();
        if (detector != null) {
            detector.close();
        }
        super.onDestroy();
    }

    //TODO register face dialogue
    private void registerFaceDialogue(Bitmap croppedFace, FaceClassifier.Recognition rec) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.register_face_dialogue);
        ImageView ivFace = dialog.findViewById(R.id.dlg_image);
        EditText nameEd = dialog.findViewById(R.id.dlg_input);
        Button register = dialog.findViewById(R.id.button2);
        ivFace.setImageBitmap(croppedFace);
        register.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String name = nameEd.getText().toString();
                if (name.isEmpty()) {
                    nameEd.setError("Enter Name");
                    return;
                }
                faceClassifier.register(name, rec);
                Toast.makeText(MainActivity.this, "Face Registered Successfully", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    //TODO switch camera
    public void switchCamera() {

        Intent intent = getIntent();

        if (useFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            useFacing = CameraCharacteristics.LENS_FACING_BACK;
        } else {
            useFacing = CameraCharacteristics.LENS_FACING_FRONT;
        }

        intent.putExtra(KEY_USE_FACING, useFacing);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

        restartWith(intent);
    }

    private void restartWith(Intent intent) {
        finish();
        overridePendingTransition(0, 0);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }
}
