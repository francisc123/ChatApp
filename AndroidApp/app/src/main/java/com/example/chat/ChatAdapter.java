package com.example.chat;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView; // Import necesar pentru containerul pozei
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.List;
import java.io.OutputStream;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    private List<Message> messageList;
    private Context context;

    public ChatAdapter(List<Message> messageList, Context context) {
        this.messageList = messageList;
        this.context = context;
    }

    @Override
    public int getItemViewType(int position) {
        return messageList.get(position).isSentByUser() ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_SENT) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_sent, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_received, parent, false);
        }
        return new MessageViewHolder(view);
    }

    // În ChatAdapter.java

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messageList.get(position);

        // 1. Resetăm vizibilitatea
        holder.messageText.setVisibility(View.GONE);
        holder.cardImage.setVisibility(View.GONE);

        // ================= CAZ IMAGINE (TYPE 1) =================
        if (message.type == 1) {
            holder.cardImage.setVisibility(View.VISIBLE);

            if (message.filePath != null && !message.filePath.isEmpty()) {
                com.bumptech.glide.Glide.with(context)
                        .load(message.filePath)
                        .override(800, 800)
                        .centerCrop()
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_delete)
                        .into(holder.messageImage);
            } else {
                holder.messageImage.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            // Click pe Imagine -> Deschide ViewImageActivity
            holder.itemView.setOnClickListener(v -> {
                if (message.filePath != null) {
                    Intent intent = new Intent(context, ViewImageActivity.class);
                    intent.putExtra("image_path", message.filePath);
                    context.startActivity(intent);
                }
            });

        }
        // ================= CAZ FIȘIER (TYPE 2) =================
        else if (message.type == 2) {
            holder.messageText.setVisibility(View.VISIBLE);
            String icon = message.fileName.toLowerCase().endsWith(".pdf") ? "📕 " : "📄 ";
            holder.messageText.setText(icon + message.fileName);

            // Click pe Fișier -> Deschide ViewPdfActivity DOAR dacă e PDF
            holder.itemView.setOnClickListener(v -> {
                if (message.filePath != null) {

                    if(message.fileName.toLowerCase().endsWith(".pdf")) {
                        // AICI ERA GREȘEALA: Trebuie ViewPdfActivity, nu ViewImageActivity
                        Intent intent = new Intent(context, ViewPdfActivity.class);
                        intent.putExtra("file_path", message.filePath);
                        intent.putExtra("file_name", message.fileName);
                        context.startActivity(intent);
                    } else {
                        openFileExternally(message);
                    }
                } else {
                    Toast.makeText(context, "Format neacceptat pentru vizualizare", Toast.LENGTH_SHORT).show();
                }
            });

        }
        // ================= CAZ TEXT (TYPE 0) =================
        else {
            holder.messageText.setVisibility(View.VISIBLE);
            holder.messageText.setText(message.getContent());
            // La text nu vrem click simplu
            holder.itemView.setOnClickListener(null);
        }

        // ================= LONG CLICK (DOWNLOAD) =================
        // Acesta se aplică peste logică de mai sus fără să o strice
        if (message.type == 1 || message.type == 2) {
            holder.itemView.setOnLongClickListener(v -> {
                showDownloadDialog(message);
                return true;
            });
        } else {
            holder.itemView.setOnLongClickListener(null);
        }
    }

    private void showDownloadDialog(Message message) {
        new AlertDialog.Builder(context)
                .setTitle("Descarca fisierul")
                .setMessage("Salvare in folderul Downloads")
                .setPositiveButton("Da", (dialog, which) -> {
                    saveFileToDownloads(message);
                })
                .setNegativeButton("Nu", null)
                .show();
    }

    private void saveFileToDownloads(Message message) {
        if(message.filePath == null) return;

        try {
            java.io.File privateFile = new java.io.File(message.filePath);
            if(!privateFile.exists()) {
                Toast.makeText(context, "Fisierul nu a fost descarcat inca.", Toast.LENGTH_SHORT).show();
                return;
            }

            android.content.ContentValues values = new android.content.ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "ChatApp_" + message.fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, message.type == 1 ? "image/jpeg" : "application/pdf");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

            if(uri != null) {
                OutputStream  out = context.getContentResolver().openOutputStream(uri);
                java.io.FileInputStream in = new java.io.FileInputStream(privateFile);

                byte[] buffer = new byte[4096];
                int len;
                while((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                out.close();
                in.close();
                Toast.makeText(context, "Salvat in Downloads.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Eroare la salvare: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    private void openFileExternally(Message message) {
        try {
            // 1. Verificăm fișierul intern
            java.io.File privateFile = new java.io.File(message.filePath);
            if (!privateFile.exists()) {
                Toast.makeText(context, "Fișierul nu există fizic.", Toast.LENGTH_SHORT).show();
                return;
            }

            // 2. Îl copiem temporar în Downloads ca să fie vizibil pentru alte aplicații
            android.content.ContentValues values = new android.content.ContentValues();
            // Punem un prefix "Open_" ca să nu se bată cap în cap cu alte descărcări
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "Open_" + message.fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream"); // Tip generic
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri publicUri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

            if (publicUri != null) {
                // 3. Copiem efectiv datele (Stream Copy)
                OutputStream out = context.getContentResolver().openOutputStream(publicUri);
                java.io.FileInputStream in = new java.io.FileInputStream(privateFile);

                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                out.close();
                in.close();

                // 4. Lansăm Intent-ul de deschidere
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(publicUri, "*/*"); // Lăsăm Android să decidă cu ce îl deschide
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

                Toast.makeText(context, "Se deschide...", Toast.LENGTH_SHORT).show();
                context.startActivity(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Nu am găsit o aplicație pentru acest fișier.", Toast.LENGTH_LONG).show();
        }
    }

    // Clasa ViewHolder actualizată să găsească și imaginea
    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        ImageView messageImage;
        CardView cardImage;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            // Găsim elementele după ID-urile din XML-urile modificate anterior
            messageText = itemView.findViewById(R.id.text_message_body);
            messageImage = itemView.findViewById(R.id.image_message_body);
            cardImage = itemView.findViewById(R.id.card_image);
        }
    }
}