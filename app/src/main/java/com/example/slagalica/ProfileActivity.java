package com.example.slagalica;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.slagalica.service.UserService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.File;
import java.io.FileOutputStream;

public class ProfileActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "profile_cache";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_REGION = "region";
    private static final String KEY_AVATAR_URL = "avatar_url";

    private UserService userService;

    private TextView textUsername;
    private TextView textEmail;
    private TextView textRegion;
    private ImageView avatarImage;
    private ImageButton buttonEditAvatar;
    private ProgressBar avatarProgressBar;

    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        uploadAvatar(imageUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        userService = new UserService();

        textUsername = findViewById(R.id.textUsername);
        textEmail = findViewById(R.id.textEmail);
        textRegion = findViewById(R.id.textRegion);
        avatarImage = findViewById(R.id.avatarImage);
        buttonEditAvatar = findViewById(R.id.buttonEditAvatar);
        avatarProgressBar = findViewById(R.id.avatarProgressBar);

        loadProfile();

        Button logoutButton = findViewById(R.id.buttonLogout);
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply();
            Intent intent = new Intent(ProfileActivity.this, WelcomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        buttonEditAvatar.setOnClickListener(v -> openGallery());

        ImageView qrCodeImage = findViewById(R.id.qrCodeImage);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String qrLink = currentUser != null ? currentUser.getUid() : "";
        generateQrCode(qrLink, qrCodeImage);

        View WhoKnowsStatsToggleHeader = findViewById(R.id.WhoKnowsStatsToggleHeader);
        View WhoKnowsStatsExpandedContainer = findViewById(R.id.WhoKnowsStatsExpandedContainer);
        ImageView WhoKnowsStatsToggleIcon = findViewById(R.id.WhoKnowsStatsToggleIcon);

        View NumbersGameStatsToggleHeader = findViewById(R.id.NumbersGameStatsToggleHeader);
        View NumbersGameStatsExpandedContainer = findViewById(R.id.NumbersGameStatsExpandedContainer);
        ImageView NumbersGameStatsToggleIcon = findViewById(R.id.NumbersGameStatsToggleIcon);

        View StepByStepStatsToggleHeader = findViewById(R.id.StepByStepStatsToggleHeader);
        View StepByStepStatsExpandedContainer = findViewById(R.id.StepByStepStatsExpandedContainer);
        ImageView StepByStepStatsToggleIcon = findViewById(R.id.StepByStepStatsToggleIcon);

        View AssociationsStatsToggleHeader = findViewById(R.id.AssociationsStatsToggleHeader);
        View AssociationsStatsExpandedContainer = findViewById(R.id.AssociationsStatsExpandedContainer);
        ImageView AssociationsStatsToggleIcon = findViewById(R.id.AssociationsStatsToggleIcon);

        View SkockoStatsToggleHeader = findViewById(R.id.SkockoStatsToggleHeader);
        View SkockoStatsExpandedContainer = findViewById(R.id.SkockoStatsExpandedContainer);
        ImageView SkockoStatsToggleIcon = findViewById(R.id.SkockoStatsToggleIcon);

        View ConnectionsStatsToggleHeader = findViewById(R.id.ConnectionsStatsToggleHeader);
        View ConnectionsStatsExpandedContainer = findViewById(R.id.ConnectionsStatsExpandedContainer);
        ImageView ConnectionsStatsToggleIcon = findViewById(R.id.ConnectionsStatsToggleIcon);

        WhoKnowsStatsToggleHeader.setOnClickListener(v -> {
            boolean expanded = WhoKnowsStatsExpandedContainer.getVisibility() == View.VISIBLE;
            WhoKnowsStatsExpandedContainer.setVisibility(expanded ? View.GONE : View.VISIBLE);
            WhoKnowsStatsToggleIcon.setImageResource(expanded ? R.drawable.ic_arrow_down : R.drawable.ic_arrow_up);
        });
        NumbersGameStatsToggleHeader.setOnClickListener(v -> {
            boolean expanded = NumbersGameStatsExpandedContainer.getVisibility() == View.VISIBLE;
            NumbersGameStatsExpandedContainer.setVisibility(expanded ? View.GONE : View.VISIBLE);
            NumbersGameStatsToggleIcon.setImageResource(expanded ? R.drawable.ic_arrow_down : R.drawable.ic_arrow_up);
        });
        StepByStepStatsToggleHeader.setOnClickListener(v -> {
            boolean expanded = StepByStepStatsExpandedContainer.getVisibility() == View.VISIBLE;
            StepByStepStatsExpandedContainer.setVisibility(expanded ? View.GONE : View.VISIBLE);
            StepByStepStatsToggleIcon.setImageResource(expanded ? R.drawable.ic_arrow_down : R.drawable.ic_arrow_up);
        });
        AssociationsStatsToggleHeader.setOnClickListener(v -> {
            boolean expanded = AssociationsStatsExpandedContainer.getVisibility() == View.VISIBLE;
            AssociationsStatsExpandedContainer.setVisibility(expanded ? View.GONE : View.VISIBLE);
            AssociationsStatsToggleIcon.setImageResource(expanded ? R.drawable.ic_arrow_down : R.drawable.ic_arrow_up);
        });
        SkockoStatsToggleHeader.setOnClickListener(v -> {
            boolean expanded = SkockoStatsExpandedContainer.getVisibility() == View.VISIBLE;
            SkockoStatsExpandedContainer.setVisibility(expanded ? View.GONE : View.VISIBLE);
            SkockoStatsToggleIcon.setImageResource(expanded ? R.drawable.ic_arrow_down : R.drawable.ic_arrow_up);
        });
        ConnectionsStatsToggleHeader.setOnClickListener(v -> {
            boolean expanded = ConnectionsStatsExpandedContainer.getVisibility() == View.VISIBLE;
            ConnectionsStatsExpandedContainer.setVisibility(expanded ? View.GONE : View.VISIBLE);
            ConnectionsStatsToggleIcon.setImageResource(expanded ? R.drawable.ic_arrow_down : R.drawable.ic_arrow_up);
        });
    }

    private void loadProfile() {
        loadCachedProfile();

        userService.loadProfile().addOnSuccessListener(document -> {
            if (document.exists()) {
                String username = document.getString("username");
                String email = document.getString("email");
                String region = document.getString("region");
                String avatarUrl = document.getString("avatarUrl");

                textUsername.setText(username != null ? username : "Nepoznato");
                textEmail.setText(email != null ? email : "Nepoznato");
                textRegion.setText(region != null ? "Region: " + region : "Region: Nepoznato");

                loadAvatar(avatarUrl);

                cacheProfile(username, email, region, avatarUrl);
            }
        }).addOnFailureListener(e -> {
            if (getCachedUsername() == null) {
                Toast.makeText(this, "Greška pri učitavanju profila: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadCachedProfile() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String username = prefs.getString(KEY_USERNAME, null);
        String email = prefs.getString(KEY_EMAIL, null);
        String region = prefs.getString(KEY_REGION, null);
        String avatarUrl = prefs.getString(KEY_AVATAR_URL, null);

        if (username != null) textUsername.setText(username);
        if (email != null) textEmail.setText(email);
        if (region != null) textRegion.setText("Region: " + region);
        if (avatarUrl != null) loadAvatar(avatarUrl);
    }

    private void cacheProfile(String username, String email, String region, String avatarUrl) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_EMAIL, email)
                .putString(KEY_REGION, region)
                .putString(KEY_AVATAR_URL, avatarUrl)
                .apply();
    }

    private String getCachedUsername() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_USERNAME, null);
    }

    private void updateCachedAvatarUrl(String avatarUrl) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_AVATAR_URL, avatarUrl)
                .apply();
    }

    private void loadAvatar(String avatarUrl) {
        Glide.with(this)
                .load(avatarUrl != null && !avatarUrl.isEmpty() ? avatarUrl : R.drawable.default_profile)
                .placeholder(R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .circleCrop()
                .into(avatarImage);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    private void uploadAvatar(Uri imageUri) {
        buttonEditAvatar.setEnabled(false);
        avatarProgressBar.setVisibility(View.VISIBLE);

        loadAvatar(imageUri.toString());

        Uri compressedUri = compressImage(imageUri);

        userService.uploadAndUpdateAvatar(compressedUri)
                .addOnSuccessListener(url -> {
                    updateCachedAvatarUrl(url.toString());
                    Glide.with(this).load(url.toString()).circleCrop().into(avatarImage);
                    Toast.makeText(this, "Avatar ažuriran", Toast.LENGTH_SHORT).show();
                    buttonEditAvatar.setEnabled(true);
                    avatarProgressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    buttonEditAvatar.setEnabled(true);
                    avatarProgressBar.setVisibility(View.GONE);
                });
    }

    private Uri compressImage(Uri imageUri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);

            int maxSize = 600;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            float ratio = (float) Math.max(width, height) / maxSize;
            if (ratio > 1) {
                width = (int) (width / ratio);
                height = (int) (height / ratio);
            }

            Bitmap resized = Bitmap.createScaledBitmap(bitmap, width, height, true);

            File tempDir = new File(getCacheDir(), "upload");
            tempDir.mkdirs();
            File tempFile = new File(tempDir, "avatar_compressed.jpg");
            FileOutputStream out = new FileOutputStream(tempFile);
            resized.compress(Bitmap.CompressFormat.JPEG, 80, out);
            out.close();

            if (bitmap != resized) bitmap.recycle();
            resized.recycle();

            return Uri.fromFile(tempFile);
        } catch (Exception e) {
            e.printStackTrace();
            return imageUri;
        }
    }

    private void generateQrCode(String text, ImageView targetView) {
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(text, BarcodeFormat.QR_CODE, 400, 400);
            targetView.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }
}
