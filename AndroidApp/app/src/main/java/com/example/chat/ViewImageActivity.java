package com.example.chat;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;

public class ViewImageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_image);

        PhotoView fullScreenImage = findViewById(R.id.imgFullScreen);
        ImageButton btnBack = findViewById(R.id.btnBack);

        String imagePath = getIntent().getStringExtra("image_path");

        if(imagePath != null) {
            Glide.with(this)
                    .load(imagePath)
                    .into(fullScreenImage);
        }
        btnBack.setOnClickListener(v -> {
            finish();
        });
    }
}