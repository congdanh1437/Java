package com.example.graduation;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.util.Random;

public class ImageDisplayActivity extends AppCompatActivity {
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int REQUEST_PERMISSION = 2;

    private ImageView imageViewSelected;
    private ImageView imageViewLower;
    private TextView textViewLower;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_display);

        imageViewSelected = findViewById(R.id.image_view_selected);
        imageViewLower = findViewById(R.id.image_view_lower);
        textViewLower = findViewById(R.id.text_view_lower);

        // Check for necessary permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
        }

        String folderPath = getIntent().getStringExtra("folderPath");
        if (folderPath != null) {
            displayRandomImageFromFolder(folderPath);
        }

        // Set click listener for lower ImageView
        imageViewLower.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openImageGallery();
            }
        });
    }

    private void displayRandomImageFromFolder(String folderPath) {
        File folder = new File(folderPath);
        File[] files = folder.listFiles();
        if (files != null && files.length > 0) {
            // Choose a random image from the folder
            int randomIndex = new Random().nextInt(files.length);
            Bitmap bitmap = BitmapFactory.decodeFile(files[randomIndex].getAbsolutePath());

            // Display the image in the upper ImageView
            imageViewSelected.setImageBitmap(bitmap);

            // You can add additional code to handle the lower ImageView and TextView if needed
        }
    }

    private void openImageGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                imageViewLower.setImageBitmap(bitmap);
                textViewLower.setVisibility(View.GONE); // Hide the TextView
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, do nothing
            } else {
                // Permission denied, handle accordingly
                // You might want to show a message to the user
            }
        }
    }
}