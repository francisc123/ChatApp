package com.example.chat;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class ChatProfileActivity extends AppCompatActivity {

    private RecyclerView recyclerImages, recyclerFiles;
    private Button btnImages, btnFiles;
    private TextView txtNoMedia;

    private List<Message> allMessages;
    private List<Message> imageList = new ArrayList<>();
    private List<Message> fileList = new ArrayList<>();
    public static List<Message> tempMessages = null;
    public static String tempImage = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_profile);

        TextView txtName = findViewById(R.id.txtProfileName);
        ImageView imgProfile = findViewById(R.id.imgProfileUser);
        btnImages = findViewById(R.id.btnTabImages);
        btnFiles = findViewById(R.id.btnTabFiles);
        recyclerImages = findViewById(R.id.recyclerImages);
        recyclerFiles = findViewById(R.id.recyclerFiles);
        txtNoMedia = findViewById(R.id.txtNoMedia);

        String userName = getIntent().getStringExtra("user_name");
        String base64Image = tempImage;
        tempImage = null;
        allMessages = (List<Message>) getIntent().getSerializableExtra("message_list");

        if(userName != null) txtName.setText(userName);

        if(base64Image != null && !base64Image.isEmpty()) {
            try {
                byte[] decodedString = android.util.Base64.decode(base64Image, Base64.DEFAULT);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                imgProfile.setImageBitmap(bitmap);
            } catch (Exception e) {
                e.printStackTrace();
                imgProfile.setImageResource(R.drawable.ic_launcher_foreground);
            }
        }

        if(tempMessages != null) {
            allMessages = new ArrayList<>(tempMessages);
            tempMessages = null;
        } else {
            allMessages = new ArrayList<>();
        }

        if(allMessages != null) {
            for(Message m : allMessages) {
                if(m.filePath != null) {
                    if(m.type == 1) {
                        imageList.add(m);
                    } else if (m.type == 2) {
                        fileList.add(m);
                    }
                }
            }
        }

        setupImagesList();
        setupFilesList();

        btnImages.setOnClickListener(v -> switchTab(true));
        btnFiles.setOnClickListener(v -> switchTab(false));
    }

    private void switchTab(boolean showImages) {
        if(showImages) {
            recyclerImages.setVisibility(View.VISIBLE);
            recyclerFiles.setVisibility(View.GONE);
            btnImages.setTextColor(Color.parseColor("#075E54"));
            btnFiles.setTextColor(Color.parseColor("#555555"));
            txtNoMedia.setVisibility(imageList.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            recyclerImages.setVisibility(View.GONE);
            recyclerFiles.setVisibility(View.VISIBLE);
            btnImages.setTextColor(Color.parseColor("#555555"));
            btnFiles.setTextColor(Color.parseColor("#075E54"));
            txtNoMedia.setVisibility(fileList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void setupImagesList() {
        recyclerImages.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerImages.setAdapter(new RecyclerView.Adapter<ImgHolder>() {
            @NonNull
            @Override
            public ImgHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ImageView iv = new ImageView(parent.getContext());
                iv.setLayoutParams(new ViewGroup.LayoutParams(300, 300));
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setPadding(2,2,2,2);
                return new ImgHolder(iv);
            }

            @Override
            public void onBindViewHolder(@NonNull ImgHolder holder, int position) {
                Message m = imageList.get(position);
                Glide.with(ChatProfileActivity.this).load(m.filePath).into((ImageView) holder.itemView);

                holder.itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(ChatProfileActivity.this, ViewImageActivity.class);
                    intent.putExtra("image_path", m.filePath);
                    startActivity(intent);
                });
            }

            @Override
            public int getItemCount() { return imageList.size(); }
        });
    }

    private void setupFilesList() {
        recyclerFiles.setLayoutManager(new LinearLayoutManager(this));

        recyclerFiles.setAdapter(new RecyclerView.Adapter<FileHolder>() {
            @NonNull
            @Override
            public FileHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                TextView tv = new TextView(parent.getContext());

                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, // Lățime
                        ViewGroup.LayoutParams.WRAP_CONTENT  // Înălțime
                );
                params.setMargins(0, 5, 0, 5); // Puțin spațiu între elemente
                tv.setLayoutParams(params);

                tv.setPadding(40, 40, 40, 40); // Spațiu interior
                tv.setTextSize(16);
                tv.setTextColor(android.graphics.Color.BLACK);
                tv.setBackgroundColor(android.graphics.Color.WHITE); // Fundal alb


                tv.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_file, 0, 0, 0);
                tv.setCompoundDrawablePadding(20);

                return new FileHolder(tv);
            }

            @Override
            public void onBindViewHolder(@NonNull FileHolder holder, int position) {
                Message m = fileList.get(position);

                TextView tv = (TextView) holder.itemView;

                String numeFisier = (m.fileName != null) ? m.fileName : "Document necunoscut";
                tv.setText(numeFisier);

                holder.itemView.setOnClickListener(v -> {
                    if (m.filePath != null && m.fileName != null && m.fileName.toLowerCase().endsWith(".pdf")) {
                        try {
                            Intent intent = new Intent(ChatProfileActivity.this, ViewPdfActivity.class);
                            intent.putExtra("file_path", m.filePath);
                            intent.putExtra("file_name", m.fileName);
                            startActivity(intent);
                        } catch (Exception e) {
                            android.widget.Toast.makeText(ChatProfileActivity.this, "Eroare la deschidere PDF", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        android.widget.Toast.makeText(ChatProfileActivity.this, "Nu este un fișier PDF valid", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public int getItemCount() {
                return fileList.size();
            }
        });
    }

    static class ImgHolder extends RecyclerView.ViewHolder {
        public ImgHolder(@NonNull View itemView) { super(itemView); }
    }
    static class FileHolder extends  RecyclerView.ViewHolder {
        public FileHolder(@NonNull View itemView) { super(itemView); }
    }
}