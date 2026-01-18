package com.example.chat;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.github.barteksc.pdfviewer.PDFView;
import java.io.File;

public class ViewPdfActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_pdf);

        PDFView pdfView = findViewById(R.id.pdfView);
        ImageButton btnBack = findViewById(R.id.btnBackPdf);
        TextView txtTitle = findViewById(R.id.txtPdfTitle);

        String filePath = getIntent().getStringExtra("file_path");
        String fileName = getIntent().getStringExtra("file_name");

        if(fileName != null) txtTitle.setText(fileName);

        btnBack.setOnClickListener(v -> finish());

        if (filePath != null) {
            File file = new File(filePath);
            if(file.exists()) {
                // Încărcăm PDF-ul
                pdfView.fromFile(file)
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .load();
            } else {
                Toast.makeText(this, "Fișierul nu a fost găsit!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}