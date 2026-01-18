package com.example.chat;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ConnectionManager {
    private static ConnectionManager instance;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private String myUsername;

    // Configurare server
    private static final String SERVER_IP = "192.168.1.252";
    private static final int SERVER_PORT = 12345;
    private volatile boolean chatActive = false;
    public String tempChatImage;

    public void setChatActive(boolean active) {
        this.chatActive = active;
    }

    public boolean isChatActive() {
        return chatActive;
    }

    private ConnectionManager() {}

    public static synchronized ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    public void setMyUsername(String myUsername) {
        this.myUsername = myUsername;
    }

    // 1. Conectare și Login
    public String connectAndLogin(String username, String password) {
        try {
            if (socket == null || socket.isClosed()) {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                out = new DataOutputStream(socket.getOutputStream());
                in = new DataInputStream(socket.getInputStream());

                out.writeUTF("CLIENT_TYPE:ANDROID");
                out.flush();
            }

            // --- PROTOCOLUL DIN SERVERUL TĂU ---
            // Server: "Welcome..." și "Do you have an account?"
            String msg = "";
            while (!msg.contains("1. Yes")) {
                msg = in.readUTF();
            }
            out.writeUTF("1"); // 1 = Am cont

            // Server: "What's your name?"
            while (!msg.contains("name?")) {
                msg = in.readUTF();
            }
            out.writeUTF(username);
            this.myUsername = username;

            // Server: "What's your password?"
            while (!msg.contains("password?")) {
                msg = in.readUTF();
            }
            out.writeUTF(password);

            // Server: "Login successful!" sau "Login failed!"
            // Aici trebuie să fim atenți, serverul trimite mai multe mesaje rapid
            msg = in.readUTF();
            if (msg.contains("Login successful")) {
                // Dacă login e ok, serverul trimite imediat lista de useri
                return "SUCCESS";
            } else {
                disconnect();
                return "FAILED";
            }

        } catch (IOException e) {
            e.printStackTrace();
            Log.e("DEBUG_CHAT","Nu se conecteaza: " + e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    public void sendFile(String fileName, long fileSize, InputStream fileInputStream) {
        new Thread(() -> {
            try {
                if(out != null) {
                    out.writeUTF("send");
                    out.writeUTF(fileName);
                    out.writeLong(fileSize);

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while((bytesRead = fileInputStream.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }

                    out.flush();
                    fileInputStream.close();
                    Log.d("DEBUG_CHAT", "Fisier trimis cu succes: " + fileName);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("DEBUG_CHAT", "Eroare la trimiterea fisierului: " + e.getMessage());
            }
        }).start();
    }

    public List<String> readInboxData() {
        List<String> data = new ArrayList<>();
        try {
            while (true) {
                // Citim header-ul
                String header = in.readUTF();
                Log.d("DEBUG_CHAT", "Header primit: " + header);

                // 1. Cazul ideal: Am primit datele
                if ("INBOX_DATA".equals(header)) {
                    int size = in.readInt();
                    for (int i = 0; i < size; i++) {
                        data.add(in.readUTF());
                    }
                    return data; // Ieșim cu datele
                }
                // 2. Confirmarea de back - o ignorăm și continuăm bucla
                else if ("ACK_BACK".equals(header)) {
                    continue;
                }
                // 3. CURĂȚENIE: Mesaje reziduale de la Desktop sau conexiune
                // Dacă prindem "Attempting..." sau "Connected...", le sărim.
                else if (header.startsWith("Attempting") || header.startsWith("Connected")) {
                    Log.w("DEBUG_CHAT", "Am ignorat un mesaj de stare: " + header);
                    continue;
                }
                // 4. Dacă primim istoric din greșeală, îl consumăm ca să nu rămână pe țeavă
                else if ("CHAT_HISTORY".equals(header)) {
                    int size = in.readInt();
                    for(int i=0; i<size; i++) in.readUTF();
                    continue;
                }
                else {
                    // Dacă e altceva complet neașteptat, ne oprim să nu intrăm în buclă infinită
                    Log.e("DEBUG_CHAT", "Header critic neasteptat: " + header);
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }
    public void disconnect() {
        try {
            if (out != null) out.writeUTF("exit"); // Spunem serverului pa
            if (socket != null) socket.close();
        } catch (IOException e) {
            Log.e("DEBUG_CHAT", "Eroare la citirea inbox-ului: " + e.getMessage());
        }
        socket = null;
    }

    public void requestDownload(String fileName) {
        new Thread(() -> {
            try {
                if(out != null) {
                    synchronized (out) {
                        out.writeUTF("download_direct");
                        out.writeUTF(fileName);
                        out.flush();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void sendProfileImage(String base64Image) {
        new Thread(() -> {
            try{
                if(out != null) {

                    byte[] imagesBytes = base64Image.getBytes("UTF-8");
                    out.writeUTF("CMD_UPDATE_PHOTO_LARGE");

                    out.writeInt(imagesBytes.length);

                    out.write(imagesBytes);
                    out.flush();

                    Log.d("DEBUG_CHAT", "Poza trimisa pe server");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public DataInputStream getIn() { return in; }
    public DataOutputStream getOut() { return out; }
    public String getMyUsername() { return myUsername; }
    public boolean isConnected() { return socket != null && !socket.isClosed(); }
}