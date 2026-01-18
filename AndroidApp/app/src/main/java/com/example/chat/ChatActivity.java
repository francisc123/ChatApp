package com.example.chat;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView recyclerChat;
    private EditText inputMessage;
    private Button btnSend, btnAttach, btnCancelPreview;

    private RelativeLayout layoutPreview;
    private ImageView imgPreview;
    private TextView txtPreviewName;

    private ChatAdapter adapter;
    private List<Message> messageList;
    private boolean isListening = true;

    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<Intent> pickFileLauncher;

    private Uri pendingFileUri = null;
    private String pendingFileName = null;
    private long pendingFileSize = 0;
    private int pendingFileType = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ImageView imgToolbar = findViewById(R.id.imgProfileToolbar);
        TextView txtChatName = findViewById(R.id.txtChatName);


        recyclerChat = findViewById(R.id.recyclerChat);
        inputMessage = findViewById(R.id.inputMessage);
        btnSend = findViewById(R.id.btnSend);
        btnAttach = findViewById(R.id.btnAttach);

        layoutPreview = findViewById(R.id.layoutPreview);
        imgPreview = findViewById(R.id.imgPreview);
        txtPreviewName = findViewById(R.id.txtPreviewName);
        btnCancelPreview = findViewById(R.id.btnCancelPreview);


        String targetUser = getIntent().getStringExtra("USER_NAME");
        String base64Image = ConnectionManager.getInstance().tempChatImage;
        ConnectionManager.getInstance().tempChatImage = null;

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        txtChatName.setText(targetUser);

        final String currentImageBase64 = base64Image;

        View.OnClickListener openProfileListener = v -> {
            Intent intent = new Intent(ChatActivity.this, ChatProfileActivity.class);
            intent.putExtra("user_name", targetUser);
//            intent.putExtra("message_list", new ArrayList<>(messageList));
//            intent.putExtra("user_image", currentImageBase64);
            ChatProfileActivity.tempImage = currentImageBase64;
            ChatProfileActivity.tempMessages = new ArrayList<>(messageList);

            startActivity(intent);
        };

        txtChatName.setOnClickListener(openProfileListener);
        imgToolbar.setOnClickListener(openProfileListener);

        if (base64Image != null && !base64Image.isEmpty()) {
            try {
                byte[] decodedString = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                imgToolbar.setImageBitmap(bitmap);
            } catch (Exception e) {
                imgToolbar.setImageResource(R.mipmap.ic_launcher_round);
            }
        }

        messageList = new ArrayList<>();
        adapter = new ChatAdapter(messageList, this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerChat.setLayoutManager(layoutManager);
        recyclerChat.setAdapter(adapter);

        new Thread(this::listenForMessages).start();

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if(result.getResultCode() == RESULT_OK && result.getData() != null) {
                        showPreview(result.getData().getData(), 1);
                    }
                }
        );

        pickFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if(result.getResultCode() == RESULT_OK && result.getData() != null) {
                        showPreview(result.getData().getData(), 2);
                    }
                }
        );

        btnAttach.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(ChatActivity.this, btnAttach);
            popup.getMenu().add(0, 1, 0, "Galerie Foto");
            popup.getMenu().add(0, 2, 0, "Fisier General");

            popup.setOnMenuItemClickListener(item -> {
                if(item.getItemId() == 1) {
                    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    pickImageLauncher.launch(intent);
                } else if(item.getItemId() == 2) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    pickFileLauncher.launch(intent);
                }
                return true;
            });
            popup.show();
        });

        btnCancelPreview.setOnClickListener(v -> clearPreview());

        btnSend.setOnClickListener(v -> {
            if (pendingFileUri != null) {
                uploadFileAndAddMessage();
            } else {
                String msg = inputMessage.getText().toString();
                if (!msg.isEmpty()) {
                    sendMessage(msg);
                    inputMessage.setText("");
                }
            }
        });
    }

    private void showPreview(Uri uri, int type) {
        pendingFileUri = uri;
        pendingFileType = type;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex != -1) pendingFileName = cursor.getString(nameIndex);
                if (sizeIndex != -1) pendingFileSize = cursor.getLong(sizeIndex);
            }
        } catch (Exception e) {
            e.printStackTrace();
            pendingFileName = "unknown_file";
        }
        layoutPreview.setVisibility(View.VISIBLE);
        txtPreviewName.setText(pendingFileName);
        if (type == 1) imgPreview.setImageURI(uri);
        else imgPreview.setImageResource(android.R.drawable.ic_menu_save);
    }

    private void clearPreview() {
        pendingFileUri = null;
        pendingFileName = null;
        pendingFileSize = 0;
        pendingFileType = 0;
        layoutPreview.setVisibility(View.GONE);
    }

    private void uploadFileAndAddMessage() {
        try {
            java.io.File localFile = new java.io.File(getFilesDir(), pendingFileName);
            InputStream inputStream = getContentResolver().openInputStream(pendingFileUri);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(localFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while((bytesRead = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.close();
            inputStream.close();

            long actualSize = localFile.length();

            java.io.FileInputStream uploadStream = new java.io.FileInputStream(localFile);
            ConnectionManager.getInstance().sendFile(pendingFileName, actualSize, uploadStream);

            String tag = (pendingFileType == 1) ? "[IMAGE]:" : "[FILE]:";
            String msgContent = tag + pendingFileName;

            Message msg = new Message(msgContent, true, pendingFileType, pendingFileName, localFile.getAbsolutePath());

            addMessageToUiGeneric(msg);
            clearPreview();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Eroare la citirea fișierului", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendMessage(String msgText) {
        Message msg = new Message(msgText, true);
        addMessageToUiGeneric(msg);
        new Thread(() -> {
            try {
                ConnectionManager.getInstance().getOut().writeUTF(msgText);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void addMessageToUiGeneric(Message message) {
        runOnUiThread(() -> {
            messageList.add(message);
            adapter.notifyItemInserted(messageList.size() - 1);
            recyclerChat.scrollToPosition(messageList.size() - 1);
        });
    }

    private Message parseMessageContent(String content, boolean isMine) {
        boolean isImageTag = content.startsWith("[IMAGE]:");
        boolean isFileTag = content.startsWith("[FILE]:");

        String fileName = content ;
        if (isImageTag) fileName = content.substring(8).trim();
        else if (isFileTag) fileName = content.substring(7).trim();

        String lowerName = fileName.toLowerCase();

        boolean isImageExt = lowerName.endsWith(".jpg") ||
                lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") ||
                lowerName.endsWith(".webp");

        if (isImageExt) {
            java.io.File localFile = new java.io.File(getFilesDir(), fileName);
            if(!localFile.exists() && !isMine) {
                ConnectionManager.getInstance().requestDownload(fileName);
            }
            return new Message(content, isMine, 1, fileName, localFile.getAbsolutePath());
        }

        else if (isFileTag || isImageTag || lowerName.endsWith(".pdf")) {
            java.io.File localFile = new java.io.File(getFilesDir(), fileName);
            if(!localFile.exists() && !isMine) {
                ConnectionManager.getInstance().requestDownload(fileName);
            }
            return new Message(content, isMine, 2, fileName, localFile.getAbsolutePath());
        }

        else {
            return new Message(content, isMine);
        }
    }

    private void listenForMessages() {
        DataInputStream in = ConnectionManager.getInstance().getIn();
        ConnectionManager.getInstance().setChatActive(true);

        if(in == null) {
            Log.e("Chat", "Fluxul de intrare este NULL. Conexiune inexistenta");
            runOnUiThread(() -> {
                Toast.makeText(this, "Eroare la conexiune", Toast.LENGTH_SHORT).show();
                finish();
            });
            return;
        }

        ConnectionManager.getInstance().setChatActive(true);

        try {
            while (isListening) {
                String msg = in.readUTF();

                if ("CMD_SENDING_FILE".equals(msg)) {
                    String fileName = in.readUTF();
                    long fileSize = in.readLong();

                    java.io.File file = new java.io.File(getFilesDir(), fileName);
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                        byte[] buffer = new byte[4096];
                        long totalRead = 0;
                        while (totalRead < fileSize) {
                            int read = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead));
                            fos.write(buffer, 0, read);
                            totalRead += read;
                        }
                    }

                    final String downloadedFileName = fileName;

                    runOnUiThread(() -> {
                        boolean updated = false;
                        for (int i = 0; i < messageList.size(); i++) {
                            Message m = messageList.get(i);

                            // Căutăm mesajul care aștepta acest fișier
                            if (m.fileName != null && m.fileName.equals(downloadedFileName)) {

                                // 1. Actualizăm calea
                                m.filePath = file.getAbsolutePath();

                                // 2. LOGICA CORECTĂ: Verificăm extensia
                                String lowerName = downloadedFileName.toLowerCase();
                                boolean isImage = lowerName.endsWith(".jpg") ||
                                        lowerName.endsWith(".jpeg") ||
                                        lowerName.endsWith(".png") ||
                                        lowerName.endsWith(".webp");

                                if (isImage) {
                                    m.type = 1;
                                } else {
                                    m.type = 2;
                                }

                                adapter.notifyItemChanged(i);
                                updated = true;
                            }
                        }

                        if (updated) {
                            Toast.makeText(ChatActivity.this, "Fișier primit!", Toast.LENGTH_SHORT).show();
                        }
                    });
                    continue;
                }

                if("ACK_BACK".equals(msg)) {
                    isListening = false;
                    ConnectionManager.getInstance().setChatActive(false);
                    return;
                }

                if ("CHAT_HISTORY".equals(msg)) {
                    int size = in.readInt();
                    List<Message> history = new ArrayList<>();
                    for (int i = 0; i < size; i++) {
                        String rawLine = in.readUTF();
                        String[] parts = rawLine.split("\\|", 2);
                        if (parts.length >= 2) {
                            boolean isMine = parts[0].equals(ConnectionManager.getInstance().getMyUsername());
                            history.add(parseMessageContent(parts[1], isMine));
                        }
                    }
                    runOnUiThread(() -> {
                        messageList.clear();
                        messageList.addAll(history);
                        adapter.notifyDataSetChanged();
                        if (!messageList.isEmpty()) recyclerChat.scrollToPosition(messageList.size() - 1);
                    });
                }
                else if(msg.startsWith("MSG_LIVE|")) {
                    String[] parts = msg.split("\\|", 3);
                    if(parts.length == 3) {
                        String sender = parts[1];
                        String content = parts[2];
                        boolean isMine = sender.equals(ConnectionManager.getInstance().getMyUsername());
                        Message m = parseMessageContent(content, isMine);
                        addMessageToUiGeneric(m);
                    }
                }
                else {
                    if (!msg.startsWith("Attempting") && !msg.startsWith("Connected") && !msg.equals("INBOX_DATA") && !msg.equals("File received and saved successfully.")) {
                        addMessageToUiGeneric(new Message(msg, false));
                    }
                }
            }
        } catch (IOException e) {
            Log.e("Chat", "Eroare: " + e.getMessage());
        } finally {
            ConnectionManager.getInstance().setChatActive(false);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isListening = false;
    }
}