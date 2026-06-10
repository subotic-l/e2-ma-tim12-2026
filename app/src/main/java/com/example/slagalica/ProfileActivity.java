package com.example.slagalica;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
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

public class ProfileActivity extends AppCompatActivity {

    private UserService userService;

    private TextView textUsername;
    private TextView textEmail;
    private TextView textRegion;
    private ImageView avatarImage;
    private ImageButton buttonEditAvatar;

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

        loadProfile();

        Button logoutButton = findViewById(R.id.buttonLogout);
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
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
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Greška pri učitavanju profila: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        });
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
        Toast.makeText(this, "Otpremanje slike...", Toast.LENGTH_SHORT).show();

        userService.uploadAndUpdateAvatar(imageUri)
                .addOnSuccessListener(uri -> {
                    loadAvatar(uri.toString());
                    Toast.makeText(this, "Avatar ažuriran", Toast.LENGTH_SHORT).show();
                    buttonEditAvatar.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    buttonEditAvatar.setEnabled(true);
                });
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
