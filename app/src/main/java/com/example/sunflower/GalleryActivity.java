package com.example.sunflower;

import static android.content.ContentValues.TAG;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.util.Locale;

public class GalleryActivity extends AppCompatActivity {

    private ClassifierWithModel cls;
    private ImageView imageView;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        Button selectBtn = findViewById(R.id.selectBtn);
        selectBtn.setOnClickListener(v -> getImageFromGallery());

        imageView = findViewById(R.id.imageView);
        textView = findViewById(R.id.textView);

        cls = new ClassifierWithModel(this);
        try {
            cls.init();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void getImageFromGallery() {

        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        intent.setAction(Intent.ACTION_PICK);
        startActivityForResult.launch(intent);
    }

    ActivityResultLauncher<Intent> startActivityForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>()
            {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent intent = result.getData();
                        if (intent == null) {
                            return;
                        }
                        Uri selectedImage = intent.getData();
                        Bitmap bitmap = null;

                        try {
                            if (Build.VERSION.SDK_INT >= 29) {
                                ImageDecoder.Source src =
                                        ImageDecoder.createSource(getContentResolver(), selectedImage);
                                bitmap = ImageDecoder.decodeBitmap(src);
                            } else {
                                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImage);
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