package com.example.graduation;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.graduation.databinding.ActivityImageGalleryBinding;

import java.io.File;
import java.util.ArrayList;

public class ImageGalleryActivity extends AppCompatActivity {
    private ActivityImageGalleryBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImageGalleryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String folderPath = getIntent().getStringExtra("folderPath");
        File imageFolder = new File(folderPath);
        File[] imageFiles = imageFolder.listFiles((dir, name) -> name.endsWith(".png"));

        ArrayList<String> imagePaths = new ArrayList<>();
        if (imageFiles != null) {
            for (File file : imageFiles) {
                imagePaths.add(file.getAbsolutePath());
            }
        }

        ImageGalleryAdapter adapter = new ImageGalleryAdapter(this, imagePaths);
        binding.gridView.setAdapter(adapter);
    }
}
