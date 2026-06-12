 package com.example.slagalica;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.slagalica.service.UserService;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.File;
import java.io.FileOutputStream;

public class ProfileFragment extends Fragment {

    private static final String PREFS_NAME = "profile_cache";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_REGION = "region";
    private static final String KEY_AVATAR_URL = "avatar_url";

    private UserService userService;

    private TextView textUsername;
    private TextView textEmail;
    private TextView textRegion;
    private TextView textStars;
    private TextView textTokens;
    private ImageView avatarImage;
    private ImageButton buttonEditAvatar;
    private ProgressBar avatarProgressBar;

    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        uploadAvatar(imageUri);
                    }
                }
            });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userService = new UserService();

        textUsername = view.findViewById(R.id.textUsername);
        textEmail = view.findViewById(R.id.textEmail);
        textRegion = view.findViewById(R.id.textRegion);
        textStars = view.findViewById(R.id.textStars);
        textTokens = view.findViewById(R.id.textTokens);
        avatarImage = view.findViewById(R.id.avatarImage);
        buttonEditAvatar = view.findViewById(R.id.buttonEditAvatar);
        avatarProgressBar = view.findViewById(R.id.avatarProgressBar);
        Button forgotPasswordButton = view.findViewById(R.id.btn_forgot_password);
        forgotPasswordButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), ChangePasswordActivity.class);
            startActivity(intent);
        });

        loadProfile();

        view.findViewById(R.id.buttonLogout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            requireActivity().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE).edit().clear().apply();
            Intent intent = new Intent(requireActivity(), WelcomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        buttonEditAvatar.setOnClickListener(v -> openGallery());

        ImageView qrCodeImage = view.findViewById(R.id.qrCodeImage);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String qrLink = currentUser != null ? currentUser.getUid() : "";
        generateQrCode(qrLink, qrCodeImage);

        setupToggle(view, R.id.WhoKnowsStatsToggleHeader, R.id.WhoKnowsStatsExpandedContainer, R.id.WhoKnowsStatsToggleIcon);
        setupToggle(view, R.id.NumbersGameStatsToggleHeader, R.id.NumbersGameStatsExpandedContainer, R.id.NumbersGameStatsToggleIcon);
        setupToggle(view, R.id.StepByStepStatsToggleHeader, R.id.StepByStepStatsExpandedContainer, R.id.StepByStepStatsToggleIcon);
        setupToggle(view, R.id.AssociationsStatsToggleHeader, R.id.AssociationsStatsExpandedContainer, R.id.AssociationsStatsToggleIcon);
        setupToggle(view, R.id.SkockoStatsToggleHeader, R.id.SkockoStatsExpandedContainer, R.id.SkockoStatsToggleIcon);
        setupToggle(view, R.id.ConnectionsStatsToggleHeader, R.id.ConnectionsStatsExpandedContainer, R.id.ConnectionsStatsToggleIcon);

        setupStatsProgress(view);
    }

    private void setupToggle(View root, int headerId, int containerId, int iconId) {
        View header = root.findViewById(headerId);
        View container = root.findViewById(containerId);
        ImageView icon = root.findViewById(iconId);

        header.setOnClickListener(v -> {
            boolean expanded = container.getVisibility() == View.VISIBLE;
            container.setVisibility(expanded ? View.GONE : View.VISIBLE);
            icon.setImageResource(expanded ? R.drawable.ic_arrow_down : R.drawable.ic_arrow_up);
        });
    }

    private void loadProfile() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        String username = prefs.getString(KEY_USERNAME, null);
        String email = prefs.getString(KEY_EMAIL, null);
        String region = prefs.getString(KEY_REGION, null);
        String avatarUrl = prefs.getString(KEY_AVATAR_URL, null);

        if (username != null) textUsername.setText(username);
        if (email != null) textEmail.setText(email);
        if (region != null) textRegion.setText("Region: " + region);
        if (avatarUrl != null) loadAvatar(avatarUrl);

        userService.loadProfile().addOnSuccessListener(document -> {
            if (document.exists()) {
                String uname = document.getString("username");
                String em = document.getString("email");
                String reg = document.getString("region");
                String avUrl = document.getString("avatarUrl");
                Long stars = document.getLong("stars");
                Long tokens = document.getLong("tokens");

                textUsername.setText(uname != null ? uname : "Nepoznato");
                textEmail.setText(em != null ? em : "Nepoznato");
                textRegion.setText(reg != null ? "Region: " + reg : "Region: Nepoznato");
                textStars.setText("Zvezde: " + (stars != null ? stars : 0));
                textTokens.setText("Tokeni: " + (tokens != null ? tokens : 0));

                loadAvatar(avUrl);

                prefs.edit()
                        .putString(KEY_USERNAME, uname)
                        .putString(KEY_EMAIL, em)
                        .putString(KEY_REGION, reg)
                        .putString(KEY_AVATAR_URL, avUrl)
                        .apply();
            }
        }).addOnFailureListener(e -> {
            if (prefs.getString(KEY_USERNAME, null) == null) {
                Toast.makeText(requireContext(), "Greška pri učitavanju profila: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
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
        avatarProgressBar.setVisibility(View.VISIBLE);

        loadAvatar(imageUri.toString());

        Uri compressedUri = compressImage(imageUri);

        userService.uploadAndUpdateAvatar(compressedUri)
                .addOnSuccessListener(url -> {
                    requireActivity().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                            .edit().putString(KEY_AVATAR_URL, url.toString()).apply();
                    Glide.with(this).load(url.toString()).circleCrop().into(avatarImage);
                    Toast.makeText(requireContext(), "Avatar ažuriran", Toast.LENGTH_SHORT).show();
                    buttonEditAvatar.setEnabled(true);
                    avatarProgressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    buttonEditAvatar.setEnabled(true);
                    avatarProgressBar.setVisibility(View.GONE);
                });
    }

    private Uri compressImage(Uri imageUri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), imageUri);

            int maxSize = 600;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            float ratio = (float) Math.max(width, height) / maxSize;
            if (ratio > 1) {
                width = (int) (width / ratio);
                height = (int) (height / ratio);
            }

            Bitmap resized = Bitmap.createScaledBitmap(bitmap, width, height, true);

            File tempDir = new File(requireActivity().getCacheDir(), "upload");
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

    private void setupStatsProgress(View root) {
        LinearProgressIndicator winProgress = root.findViewById(R.id.winProgress);
        LinearProgressIndicator whoKnowsAvgProgress = root.findViewById(R.id.whoKnowsAvgProgress);
        LinearProgressIndicator numbersRatioProgress = root.findViewById(R.id.numbersRatioProgress);
        LinearProgressIndicator numbersAvgProgress = root.findViewById(R.id.numbersAvgProgress);
        LinearProgressIndicator stepByStepAvgProgress = root.findViewById(R.id.stepByStepAvgProgress);
        LinearProgressIndicator associationsRatioProgress = root.findViewById(R.id.associationsRatioProgress);
        LinearProgressIndicator associationsAvgProgress = root.findViewById(R.id.associationsAvgProgress);
        LinearProgressIndicator skockoAvgProgress = root.findViewById(R.id.skockoAvgProgress);
        LinearProgressIndicator connectionsRatioProgress = root.findViewById(R.id.connectionsRatioProgress);
        LinearProgressIndicator connectionsAvgProgress = root.findViewById(R.id.connectionsAvgProgress);

        int goodColor = ContextCompat.getColor(requireContext(), R.color.correct_answer_color);
        int mediumColor = ContextCompat.getColor(requireContext(), R.color.performance_medium);
        int badColor = ContextCompat.getColor(requireContext(), R.color.wrong_answer_color);

        int winPct = 76;
        winProgress.setProgress(winPct);
        winProgress.setIndicatorColor(winPct >= 50 ? goodColor : badColor);

        int whoKnowsCorrect = 32;
        int whoKnowsTotal = whoKnowsCorrect + 14;
        int whoKnowsPct = whoKnowsTotal > 0 ? (whoKnowsCorrect * 100 / whoKnowsTotal) : 0;

        int whoKnowsAvg = 12;
        int whoKnowsMax = 50;
        whoKnowsAvgProgress.setProgress(whoKnowsAvg * 100 / whoKnowsMax);
        whoKnowsAvgProgress.setIndicatorColor(getPerformanceColor(whoKnowsAvg * 100 / whoKnowsMax, goodColor, mediumColor, badColor));

        int numbersPct = 54;
        numbersRatioProgress.setProgress(numbersPct);
        numbersRatioProgress.setIndicatorColor(getPerformanceColor(numbersPct, goodColor, mediumColor, badColor));

        int numbersAvg = 12;
        int numbersMax = 20;
        numbersAvgProgress.setProgress(numbersAvg * 100 / numbersMax);
        numbersAvgProgress.setIndicatorColor(getPerformanceColor(numbersAvg * 100 / numbersMax, goodColor, mediumColor, badColor));

        int stepByStepPct = 50;

        int stepByStepAvg = 18;
        int stepByStepMax = 40;
        stepByStepAvgProgress.setProgress(stepByStepAvg * 100 / stepByStepMax);
        stepByStepAvgProgress.setIndicatorColor(getPerformanceColor(stepByStepAvg * 100 / stepByStepMax, goodColor, mediumColor, badColor));

        int associationsCorrect = 25;
        associationsRatioProgress.setProgress(associationsCorrect);
        associationsRatioProgress.setIndicatorColor(getPerformanceColor(associationsCorrect, goodColor, mediumColor, badColor));

        int associationsAvg = 32;
        int associationsMax = 60;
        associationsAvgProgress.setProgress(associationsAvg * 100 / associationsMax);
        associationsAvgProgress.setIndicatorColor(getPerformanceColor(associationsAvg * 100 / associationsMax, goodColor, mediumColor, badColor));

        int skockoPct = 60;

        int skockoAvg = 32;
        int skockoMax = 40;
        skockoAvgProgress.setProgress(skockoAvg * 100 / skockoMax);
        skockoAvgProgress.setIndicatorColor(getPerformanceColor(skockoAvg * 100 / skockoMax, goodColor, mediumColor, badColor));

        int connectionsPct = 78;
        connectionsRatioProgress.setProgress(connectionsPct);
        connectionsRatioProgress.setIndicatorColor(getPerformanceColor(connectionsPct, goodColor, mediumColor, badColor));

        int connectionsAvg = 16;
        int connectionsMax = 20;
        connectionsAvgProgress.setProgress(connectionsAvg * 100 / connectionsMax);
        connectionsAvgProgress.setIndicatorColor(getPerformanceColor(connectionsAvg * 100 / connectionsMax, goodColor, mediumColor, badColor));
    }

    private int getPerformanceColor(int percentage, int goodColor, int mediumColor, int badColor) {
        if (percentage >= 70) return goodColor;
        if (percentage >= 40) return mediumColor;
        return badColor;
    }
}
