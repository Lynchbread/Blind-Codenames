package userinterface;

// Java
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

// Android
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.icu.text.SimpleDateFormat;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

// AndroidX
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

// Google's Machine Learning Kit
import com.google.mlkit.vision.text.Text;

// OpenCV
import static org.opencv.imgproc.Imgproc.cvtColorTwoPlane;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import imageanalysis.*;
import com.example.android_codenames_repo.R;
import com.google.common.util.concurrent.ListenableFuture;


public class CameraActivity extends AppCompatActivity
{
    // Static final class variables
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    // Static class variables
    private static List<Text.Element> first_word_list;
    private static List<Text.Element> current_word_list;
    public static int number_expected;

    // Class variables
    private Executor executor = Executors.newSingleThreadExecutor();
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalyzer;
    private PreviewView view_finder;
    private int[] original_resolution;
    private Sonar sonar;
    private boolean testing = false;
    private int current_word_index;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Prevents the screen from going to sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        first_word_list = new ArrayList<>(0);
        current_word_list = new ArrayList<>(0);
        original_resolution = new int[] {1, 1};
        setContentView(R.layout.cameraactivity);
        view_finder = findViewById(R.id.viewFinder);
        current_word_index = 0;
        sonar = new Sonar(this.getApplicationContext());
        number_expected = 25;

        if (allPermissionsGranted())
            startCamera();
        else
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);

        Button captureButton = findViewById(R.id.image_capture_button);

        captureButton.setOnClickListener(view -> {
            // Call takePhoto() when the capture button is clicked
            takePhoto();
        });
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (!first_word_list.isEmpty())
        {
            current_word_index = 0;
            play_word_audio();
            startImageAnalysis();
        }
    }

    // Overrides Android's default controller inputs
    @Override
    public boolean dispatchKeyEvent(KeyEvent event)
    {
        int action = event.getAction();
        int keyCode = event.getKeyCode();

        // Respond to key events based on the key code and action
        switch (keyCode)
        {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_L2:
                if (action == KeyEvent.ACTION_DOWN) {
                    // Go up list
                    current_word_index--;

                    if (current_word_index <= -1)
                        current_word_index = current_word_list.size() - 1;

                    play_word_audio();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_R2:
                if (action == KeyEvent.ACTION_DOWN) {
                    // Go down list
                    current_word_index++;

                    if (current_word_index >= current_word_list.size())
                        current_word_index = 0;

                    play_word_audio();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (action == KeyEvent.ACTION_DOWN) {
                    // Repeat word
                    play_word_audio();
                    return true;
                }
                break;
            default:
                return super.dispatchKeyEvent(event);
        }

        // Android default
        return super.dispatchKeyEvent(event);
    }

    private boolean allPermissionsGranted()
    {
        for (String permission : REQUIRED_PERMISSIONS)
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
                return false;
        return true;
    }

    private void startCamera()
    {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                bind(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraActivity", "Error binding camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bind(@NonNull ProcessCameraProvider cameraProvider)
    {
        Preview preview = new Preview.Builder().build();

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        // Stores latest frame into RAM for analysis. Ignores additional frames
        // until processing is done.
        imageAnalyzer = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) this,
                cameraSelector, preview, imageCapture, imageAnalyzer);

        preview.setSurfaceProvider(view_finder.getSurfaceProvider());
    }

    private File getOutputFile()
    {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String fileName = "IMG_" + sdf.format(System.currentTimeMillis()) + ".jpg";
        File outputDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return new File(outputDir, fileName);
    }

    private void takePhoto()
    {
        File photoFile = getOutputFile();

        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                outputFileOptions, executor, new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Uri savedUri = Uri.fromFile(photoFile);
                        String msg = "Photo capture succeeded: " + savedUri;
                        Log.d("TAKEPHOTO", msg);
                        Bitmap image = BitmapFactory.decodeFile(photoFile.getAbsolutePath());

                        original_resolution[0] = image.getHeight();
                        original_resolution[1] = image.getWidth();

                        Log.d("WIDTH", "" + image.getWidth());
                        Log.d("HEIGHT", "" + image.getHeight());

                        if (testing)
                        {
                            String image_filename = "game_board_example3.jpg";
                            try {
                                InputStream is = getAssets().open(image_filename);
                                Bitmap loaded_image = BitmapFactory.decodeStream(is);
                                is.close();
                                current_word_list = OCR.read_cards(loaded_image, getAssets(), number_expected, first_word_list);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        else
                        {
                            current_word_list = OCR.read_cards(image, getAssets(), number_expected, first_word_list);
                        }

                        sort_elements_alphabetically(current_word_list);
                        openWordList();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("CameraActivity", "Photo capture failed: " + exception.getMessage(), exception);
                    }
                }
        );
    }

    private void play_word_audio()
    {
        if (current_word_index >= current_word_list.size())
            current_word_index = current_word_list.size() - 1;

        if (current_word_index < current_word_list.size())
            WordAudio.play_word_audio(this.getApplicationContext(), current_word_list.get(current_word_index).getText());
    }

    private void openWordList()
    {
        if (first_word_list.isEmpty())
            first_word_list = current_word_list;

        String[] string_array = element_to_string(current_word_list);

        Intent intent = new Intent(this, WordListActivity.class);
        intent.putExtra("word_list", string_array);
        startActivity(intent);
    }

    // Handle the result of the request for camera permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startImageAnalysis()
    {
        imageAnalyzer.setAnalyzer(executor, image -> {
            processImage(image);
            image.close();
        });
    }

    private void processImage(ImageProxy image)
    {
        if (current_word_index >= current_word_list.size())
            current_word_index = current_word_list.size() - 1;

        if (!current_word_list.isEmpty())
        {
            Mat mat = imageProxyToMatBGR(image);
            double distance = Pointer.get_distance(mat,
                    current_word_list.get(current_word_index),
                    original_resolution);
            sonar.ping_sonar(distance);
        }
    }

    private static void sort_elements_alphabetically(List<Text.Element> list)
    {
        if (list.size() <= 1)
            return;

        for (int i = 0; i < list.size(); i++)
        {
            int smallest = i;

            for (int j = i; j < list.size(); j++)
                if(list.get(j).getText().compareTo(list.get(smallest).getText()) < 0)
                    smallest = j;

            Collections.swap(list, i, smallest);
        }
    }

    private static String[] element_to_string(List<Text.Element> element_list)
    {
        String[] string_array = new String[element_list.size()];

        for (int i = 0; i < element_list.size(); i++)
            string_array[i] = element_list.get(i).getText();

        return string_array;
    }

    private static Mat imageProxyToMatBGR(ImageProxy imageProxy)
    {
        @SuppressLint("UnsafeOptInUsageError") Image image = imageProxy.getImage();
        Image.Plane[] planes = image.getPlanes();

        // Get original image Width and Height
        int width = image.getWidth();
        int height = image.getHeight();

        // Extract image data from planes
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uvBuffer = planes[1].getBuffer();

        // Create Mats for Y and UV planes
        Mat yMat = new Mat(height, width, CvType.CV_8UC1, yBuffer);
        Mat uvMat = new Mat(height / 2, width / 2, CvType.CV_8UC2, uvBuffer);

        // Create an empty Mat for the BGR image
        Mat bgrMat = new Mat(height, width, CvType.CV_8UC3);

        // Convert YUV to BGR
        cvtColorTwoPlane(yMat, uvMat, bgrMat, Imgproc.COLOR_YUV2BGR_NV12);

        // Release the Mats created from the planes
        yMat.release();
        uvMat.release();

        return bgrMat;
    }
}
