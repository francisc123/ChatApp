package com.example.chat;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import android.content.SharedPreferences;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText editUser = findViewById(R.id.editUsername);
        EditText editPass = findViewById(R.id.editPassword);
        Button btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> {
            String username = editUser.getText().toString().trim();
            String password = editPass.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Completează datele!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Executăm conexiunea pe un thread secundar (obligatoriu în Android)
            new Thread(() -> {
                String result = ConnectionManager.getInstance().connectAndLogin(username, password);

                // Revenim pe UI Thread pentru a afișa rezultatul
                new Handler(Looper.getMainLooper()).post(() -> {
                    if ("SUCCESS".equals(result)) {

                        getSharedPreferences("ChatPrefs", MODE_PRIVATE).edit()
                                .putString("saved_username", username)
                                .putString("saved_password", password)
                                .apply();
                        try {
                            MasterKey masterKey = new MasterKey.Builder(LoginActivity.this)
                                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                                    .build();

                            SharedPreferences securePref = EncryptedSharedPreferences.create(
                                    LoginActivity.this,
                                    "SecretChatPrefs",
                                    masterKey,
                                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                            );

                            securePref.edit()
                                    .putString("saved_username", username)
                                    .putString("saved_password", password)
                                    .apply();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }


                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish(); // Închidem Login
                    } else {
                        Toast.makeText(this, "Login Eșuat: " + result, Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        });
    }
}