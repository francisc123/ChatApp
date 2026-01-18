package com.example.chat;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import androidx.security.crypto.MasterKey;
import androidx.security.crypto.EncryptedSharedPreferences;
import android.content.SharedPreferences;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private MainActivity.UsersAdapter adapter;
    private List<User> users = new ArrayList<>();

    private boolean isFirstLoad = true;
    private volatile boolean isLoading = false;
    private volatile boolean isListening = false;
    private boolean isTransitioning = false;


    @Override
    protected void onResume() {
        super.onResume();

        if(!ConnectionManager.getInstance().isConnected()) {
            return ;
        }

        isTransitioning = false;
        if (isFirstLoad) {
            startLiveListener(false);
            isFirstLoad = false;
        } else {
            startLiveListener(true);
        }
    }

    private void startLiveListener(boolean sendBackCommand) {
        if (isListening) return; // Evităm dublarea firelor
        isListening = true;

        new Thread(() -> {
            try {
                var in = ConnectionManager.getInstance().getIn();
                var out = ConnectionManager.getInstance().getOut();

                if (sendBackCommand) {
                    out.writeUTF("BACK_ANDROID");

                    int retries = 0;
                    while (ConnectionManager.getInstance().isChatActive() && retries < 40) {
                        try { Thread.sleep(50); } catch (InterruptedException e) {}
                        retries++;
                    }
                }

                while (isListening) {
                    String msg = in.readUTF();
                    Log.d("DEBUG_CHAT", "Main loop primit: " + msg);

                    if ("ACK_STOP".equals(msg)) {
                        isListening = false;
                        break;
                    }

                    if ("INBOX_DATA".equals(msg)) {
                        int size = in.readInt();
                        List<String> rawData = new ArrayList<>();

                        for(int i = 0; i < size; i ++) {
                            int len = in.readInt();
                            byte[] buffer = new byte[len];
                            in.readFully(buffer);

                            String entry = new String(buffer, "UTF-8");
                            rawData.add(entry);
                        }

                        updateListUI(rawData);
                    }
                    else if (msg.startsWith("MSG_LIVE|")) {
                        String[] parts = msg.split("\\|", 3);
                        if (parts.length == 3) {
                            updateListLocally(parts[1], parts[2]);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e("DEBUG_CHAT", "Eroare loop main: " + e.getMessage());
                isListening = false;
            }
        }).start();
    }

    private void updateListUI(List<String> data) {
        List<User> temporaryList = new ArrayList<>();
        for(String line : data) {
            String[] parts = line.split("\\|", 4);

            if(parts.length >= 1) {
                String contactName = parts[0];
                if(contactName.equalsIgnoreCase(ConnectionManager.getInstance().getMyUsername())) continue;

                String msg = (parts.length >= 2) ? parts[1] : "";

                int count = 0;
                if(parts.length >= 3) {
                    try {
                        count = Integer.parseInt(parts[2]);
                    } catch (NumberFormatException e) {}
                }

                String imageBase64 = (parts.length == 4) ? parts[3] : null;

                temporaryList.add(new User(contactName, msg, count, imageBase64));
            }
        }
        runOnUiThread(() -> {
            users.clear();
            users.addAll(temporaryList);
            adapter.notifyDataSetChanged();
        });
    }

    // Helper pentru actualizare live (când vine MSG_LIVE)
    private void updateListLocally(String sender, String message) {
        runOnUiThread(() -> {
            int index = -1;
            for (int i = 0; i < users.size(); i++) {
                if (users.get(i).name.equals(sender)) {
                    index = i;
                    break;
                }
            }
            if (index != -1) {

                User oldUser = users.get(index);
                int newCount = oldUser.unreadCount + 1;

                String currentImage = oldUser.profileImageBase64;

                users.remove(index);
                users.add(0, new User(sender, message, newCount, currentImage));
                adapter.notifyDataSetChanged();
            } else {
                users.add(0, new User(sender, message, 1, null));
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar_main);
        setSupportActionBar(toolbar);

        if(getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Conversatii");
        }

        if (!ConnectionManager.getInstance().isConnected()) {
            try {
                MasterKey masterKey = new MasterKey.Builder(this)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();

                SharedPreferences securePrefs = EncryptedSharedPreferences.create(
                        this,
                        "SecretChatPrefs",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );

                String savedUser = securePrefs.getString("saved_username", null);
                String savedPass = securePrefs.getString("saved_password", null);

                if(savedUser != null && savedPass != null) {
                    attemptAutoLogin(savedUser, savedPass);
                } else {
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                    return ;
                }

            } catch (Exception e) {
                e.printStackTrace();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return ;
            }
        }

        recyclerView = findViewById(R.id.recyclerConversations);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new UsersAdapter(users);
        recyclerView.setAdapter(adapter);

    }

    // --- ADAPTER ---
    class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.UserViewHolder> {
        private List<User> userList;

        public UsersAdapter(List<User> list) {
            this.userList = list;
        }

        @NonNull
        @Override
        public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user, parent, false);
            return new UserViewHolder(itemView);
        }

        public class UserViewHolder extends RecyclerView.ViewHolder {
            public TextView txtName;
            public TextView txtLastMsg;
            public TextView txtBadge;
            public android.widget.ImageView imgProfile;

            public UserViewHolder(View view) {
                super(view);
                txtName = view.findViewById(R.id.userName);
                txtLastMsg = view.findViewById(R.id.lastMsg);
                txtBadge = view.findViewById(R.id.unreadBadge);
                imgProfile = view.findViewById(R.id.imgProfile);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
            User user = userList.get(position);
            holder.txtName.setText(user.name);
            holder.txtLastMsg.setText(user.lastMessage);

            if(user.profileImageBase64 != null && !user.profileImageBase64.isEmpty()) {
                try {
                    byte[] decodedString = android.util.Base64.decode(user.profileImageBase64, android.util.Base64.DEFAULT);
                    android.graphics.Bitmap decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    holder.imgProfile.setImageBitmap(decodedByte);
                } catch (Exception e) {
                    holder.imgProfile.setImageResource(R.mipmap.ic_launcher_round);
                }
            }

            if(user.unreadCount > 0) {
                holder.txtBadge.setVisibility(View.VISIBLE);
                holder.txtBadge.setText(String.valueOf(user.unreadCount));
            } else {
                holder.txtBadge.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> {
                if (!isListening || isTransitioning) return;

                isTransitioning = true;
                new Thread(() -> {
                    try {
                        // 1. Cerem oprirea
                        ConnectionManager.getInstance().getOut().writeUTF("STOP_LISTENING");

                        // 2. Așteptăm ca bucla while din startLiveListener să primească ACK_STOP și să pună isListening = false
                        int safety = 0;
                        while (isListening && safety < 20) {
                            Thread.sleep(50);
                            safety++;
                        }

                        // 3. Trimitem numele userului pentru ChatActivity
                        ConnectionManager.getInstance().getOut().writeUTF(user.name);
                        ConnectionManager.getInstance().tempChatImage = user.profileImageBase64;

                        // 4. Pornim activitatea
                        Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                        intent.putExtra("USER_NAME", user.name);
//                        intent.putExtra("PROFILE_IMAGE", user.profileImageBase64);
                        startActivity(intent);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            });
        }

        @Override
        public int getItemCount() {
            return userList.size();
        }
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void attemptAutoLogin(String user, String pass) {
        new Thread(() -> {
            String result = ConnectionManager.getInstance().connectAndLogin(user, pass);

            runOnUiThread(() -> {
                if("SUCCESS".equals(result)) {
                    if(isFirstLoad) {
                        startLiveListener(false);
                        isFirstLoad = false;
                    } else {
                        startLiveListener(true);
                    }
                } else {
                    goToLogin();
                }
            });
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        if(item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}