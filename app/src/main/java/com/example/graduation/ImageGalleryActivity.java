package com.example.graduation;

import android.content.Intent;
import android.os.Bundle;
import android.widget.GridView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageGalleryActivity extends AppCompatActivity {

    private GridView gridView;
    private ImageAdapter imageAdapter;
    private List<File> imageFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_image_gallery);

        gridView = findViewById(R.id.grid_view);
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            displaySelectedImage(imageFiles.get(position));
        });

        String folderPath = getFolderPath();
        imageFiles = getImageFiles(folderPath);

        imageAdapter = new ImageAdapter(this, imageFiles);
        gridView.setAdapter(imageAdapter);
    }

    private String getFolderPath() {
        int folderIndex = getSharedPreferences("DetectorPrefs", MODE_PRIVATE).getInt("folderIndex", 0);
        folderIndex = folderIndex - 1;
        return getFilesDir().getAbsolutePath() + "/object_" + folderIndex;
    }

    private List<File> getImageFiles(String folderPath) {
        List<File> imageFiles = new ArrayList<>();
        File folder = new File(folderPath);
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".png")) {
                    imageFiles.add(file);
                }
            }
        }
        return imageFiles;
    }

    private void displaySelectedImage(File imageFile) {
        String fileName = imageFile.getName();
        Toast.makeText(this, "Selected file: " + fileName, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, ImageDetailsActivity.class);
        intent.putExtra("image_file_path", imageFile.getAbsolutePath());
        startActivity(intent);
    }
}
