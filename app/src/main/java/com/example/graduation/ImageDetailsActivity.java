package com.example.graduation;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.io.File;

public class ImageDetailsActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_image_details);

        ImageView imageViewSelected = findViewById(R.id.image_view_selected);
        ImageView imageViewLower = findViewById(R.id.image_view_lower);

        String imagePath = getIntent().getStringExtra("image_file_path");
        if (imagePath != null) {
            File imageFile = new File(imagePath);
            Glide.with(this).load(imageFile).into(imageViewSelected);
        }

        // Set click listener for the lower image view
        imageViewLower.setOnClickListener(v -> openImageGallery());
    }

    // Method to open image gallery
    private void openImageGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    // Override onActivityResult to handle the result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            ImageView imageViewLower = findViewById(R.id.image_view_lower);
            TextView textViewLower = findViewById(R.id.text_view_lower);
            imageViewLower.setImageURI(imageUri);
            // Hide the TextView
            textViewLower.setVisibility(View.GONE);
        }
    }
}
