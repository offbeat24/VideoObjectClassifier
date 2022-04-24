package com.example.sunflower;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class CameraActivity extends AppCompatActivity {
    public static final String TAG = "[IC]CameraActivity";

    private static final String KEY_SELECTED_URI = "KEY_SELECTED_URI";
    private ClassifierWithModel cls;
    private ImageView imageView;
    private TextView textView;

    Uri selectedImageUri;

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable(KEY_SELECTED_URI, selectedImageUri);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        Button takeBtn = findViewById(R.id.takeBtn);
        takeBtn.setOnClickListener(v -> getImageFromCamera());

        imageView = findViewById(R.id.imageView);
        textView = findViewById(R.id.textView);

        cls = new ClassifierWithModel(this);
        try {
            cls.init();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        if (savedInstanceState != null) {
            Uri uri = savedInstanceState.getParcelable(KEY_SELECTED_URI);
            if (uri != null) {
                selectedImageUri = uri;
            }
        }
    }

    private void getImageFromCamera() {
        File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "picture.jpg");

        if(file.exists()) file.delete();
        selectedImageUri = FileProvider.getUriForFile(this, getPackageName(), file);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, selectedImageUri);
        startActivityForResult.launch(intent);

    }

    ActivityResultLauncher<Intent> startActivityForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == RESULT_OK) {
                        Bitmap bitmap = null;

                        try {
                            if (Build.VERSION.SDK_INT >= 29) {
                                ImageDecoder.Source src =
                                        ImageDecoder.createSource(getContentResolver(), selectedImageUri);
                                bitmap = ImageDecoder.decodeBitmap(src);
                            } else {
                                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
                            }
                        } catch (IOException ioe) {
                            Log.e(TAG, "Failed to read Image", ioe);
                        }

                        if (bitmap != null) {
                            Pair<String, Float> output = cls.classify(bitmap);
                            String resultStr = String.format(Locale.ENGLISH, "class : %s, prob : %.2f%%",
                                    output.first, output.second * 100);

                            imageView.setImageBitmap(bitmap);
                            textView.setText(resultStr);
                        }
                    }
                }
            }
    );
}