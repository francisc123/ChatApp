package com.example.chat;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import androidx.exifinterface.media.ExifInterface;

public class SettingsActivity extends AppCompatActivity {

    private ImageView imgProfile;
    private EditText editName; // Îl facem variabilă globală ca să îl accesăm din dialog
    private ActivityResultLauncher<Intent> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbarSettings);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Setări");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 1. Inițializare elemente UI
        imgProfile = findViewById(R.id.imgProfileSettings);
        Button btnChangePhoto = findViewById(R.id.btnChangePhoto);
        Button btnLogout = findViewById(R.id.btnLogout);
        editName = findViewById(R.id.editNewName);

        // 2. Afișăm datele curente (Nume și Poză)
        loadUserData();

        // 3. Logica CLICK pe Nume (Deschide Dialog)
        editName.setOnClickListener(v -> showChangeNameDialog());

        // 4. Logica CLICK pe Schimbă Poza
        // 4. Logica CLICK pe Schimbă Poza
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            // --- MODIFICARE PENTRU ROTIRE ---

                            // 1. Decodăm imaginea brută (care poate fi rotită greșit)
                            InputStream imageStream = getContentResolver().openInputStream(imageUri);
                            Bitmap originalBitmap = BitmapFactory.decodeStream(imageStream);
                            if (imageStream != null) imageStream.close();

                            // 2. Verificăm și aplicăm rotirea necesară
                            Bitmap rotatedBitmap = rotateImageIfRequired(originalBitmap, imageUri);

                            // 3. Folosim imaginea corectată
                            imgProfile.setImageBitmap(rotatedBitmap);
                            saveProfileImage(rotatedBitmap);

                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(this, "Eroare la prelucrarea pozei", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        btnChangePhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });

        // 5. Logica LOGOUT
        btnLogout.setOnClickListener(v -> performLogout());
    }

    private void loadUserData() {
        // A. Încărcare Nume
        String currentName = ConnectionManager.getInstance().getMyUsername();
        if (currentName != null && !currentName.isEmpty()) {
            editName.setText(currentName);
        } else {
            editName.setText("Utilizator");
        }

        // B. Încărcare Poză (Prioritate: 1. Memorie Locală, 2. ConnectionManager)
        loadProfileImage();
    }

    private void showChangeNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Schimbă Numele");

        final EditText input = new EditText(this);
        input.setText(editName.getText().toString());
        builder.setView(input);

        builder.setPositiveButton("Salvează", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                // 1. Actualizăm UI
                editName.setText(newName);

                // 2. Actualizăm în Manager (pentru sesiunea curentă)
                ConnectionManager.getInstance().setMyUsername(newName);

                // 3. (Opțional) Aici ai putea trimite noul nume la server, dacă ai comandă pentru asta
                // ConnectionManager.getInstance().sendMessage("CMD_CHANGE_NAME|" + newName);

                Toast.makeText(this, "Nume schimbat!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Anulează", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void performLogout() {
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
            securePrefs.edit().clear().apply();
        } catch (Exception e) { e.printStackTrace(); }

        getSharedPreferences("ChatPrefs", MODE_PRIVATE).edit().clear().apply();
        ConnectionManager.getInstance().disconnect();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void saveProfileImage(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 30, baos);
        byte[] b = baos.toByteArray();
        String encodedImage = Base64.encodeToString(b, Base64.NO_WRAP);

        // Salvăm local
        getSharedPreferences("ChatPrefs", MODE_PRIVATE).edit()
                .putString("profile_image", encodedImage)
                .apply();

        // Salvăm și în ConnectionManager ca să fie disponibilă în sesiune
        // (Dacă ai metoda setMyProfileImage în ConnectionManager, decomentează linia de mai jos)
        // ConnectionManager.getInstance().setMyProfileImage(encodedImage);

        // Trimitem la server
        ConnectionManager.getInstance().sendProfileImage(encodedImage);

        Toast.makeText(this, "Poză salvată!", Toast.LENGTH_SHORT).show();
        Log.i("DEBUG_CHAT", "Poza trimisa cu succes!");
    }

    private void loadProfileImage() {
        // Încercăm din SharedPreferences
        String encodedImage = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
                .getString("profile_image", "");

        if (!encodedImage.isEmpty()) {
            try {
                byte[] decodedString = Base64.decode(encodedImage, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                imgProfile.setImageBitmap(decodedByte);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Fallback: Dacă nu e în Prefs, poate e în ConnectionManager (venită de la server la login)
        // Decomentează dacă ai câmpul tempProfileImage în ConnectionManager
        /* else if (ConnectionManager.getInstance().tempProfileImage != null) {
             // ... logica de decodare similară ...
        }
        */
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    /**
     * Verifică datele EXIF ale imaginii și o rotește dacă este necesar.
     */
    private Bitmap rotateImageIfRequired(Bitmap img, Uri selectedImage) throws IOException, FileNotFoundException {
        InputStream input = getContentResolver().openInputStream(selectedImage);
        ExifInterface ei;
        try {
            // Încercăm să citim datele EXIF din stream
            ei = new ExifInterface(input);
        } catch (Exception e) {
            // Dacă nu putem citi EXIF, returnăm imaginea originală
            if (input != null) input.close();
            return img;
        }

        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        if (input != null) input.close();

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateBitmap(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateBitmap(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateBitmap(img, 270);
            default:
                return img; // Nu necesită rotire
        }
    }

    /**
     * Rotește efectiv un obiect Bitmap cu un anumit număr de grade.
     */
    private Bitmap rotateBitmap(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);

        // Creăm o nouă imagine rotită
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);

        // Eliberăm memoria ocupată de imaginea veche (foarte important!)
        img.recycle();

        return rotatedImg;
    }
}