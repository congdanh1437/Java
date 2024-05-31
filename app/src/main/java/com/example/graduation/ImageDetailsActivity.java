package com.example.graduation;

import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.io.File;

public class ImageDetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_image_details);

        ImageView imageViewSelected = findViewById(R.id.image_view_selected);

        String imagePath = getIntent().getStringExtra("image_file_path");
        if (imagePath != null) {
            File imageFile = new File(imagePath);
            Glide.with(this).load(imageFile).into(imageViewSelected);
        }
    }
}
