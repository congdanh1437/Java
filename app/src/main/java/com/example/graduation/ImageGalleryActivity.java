package com.example.graduation;

import android.os.Bundle;
import android.util.Log;
import android.widget.GridView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageGalleryActivity extends AppCompatActivity {

    private static final String TAG = "ImageGalleryActivity";
    private GridView gridView;
    private ImageAdapter imageAdapter;
    private List<File> imageFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_image_gallery);

        gridView = findViewById(R.id.grid_view);

        // Get the folder path
        String folderPath = getFolderPath();

        // Get image files from the folder
        imageFiles = getImageFiles(folderPath);

        // Set up the adapter
        imageAdapter = new ImageAdapter(this, imageFiles);
        gridView.setAdapter(imageAdapter);
    }

    private String getFolderPath() {
        // Get the last index from SharedPreferences
        int folderIndex = getSharedPreferences("DetectorPrefs", MODE_PRIVATE).getInt("folderIndex", 0);
        // Create the folder path
        folderIndex = folderIndex - 1;
        Log.d(TAG, "getFolderPath: " + getFilesDir().getAbsolutePath() + "/object_" + folderIndex);
        return getFilesDir().getAbsolutePath() + "/object_" + folderIndex ;
    }

    private List<File> getImageFiles(String folderPath) {
        List<File> imageFiles = new ArrayList<>();
        File folder = new File(folderPath);
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                // Check if it's an image file (you may need to refine this check based on your requirements)
                if (file.isFile() && file.getName().toLowerCase().endsWith(".png")) {
                    imageFiles.add(file);
                }
            }
        }
        return imageFiles;
    }
}
