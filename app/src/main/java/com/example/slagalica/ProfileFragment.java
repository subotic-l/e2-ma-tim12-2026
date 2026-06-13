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
import com.example.slagalica.data.StatsRepository;
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
    private ImageView avatarImage;
    private ImageButton buttonEditAvatar;
    private ProgressBar avatarProgressBar;
    private StatsRepository statsRepository;

    private TextView textTotalGames;
    private TextView textWinLoseRatio;
    private LinearProgressIndicator winProgress;
    private LinearProgressIndicator whoKnowsAvgProgress;
    private LinearProgressIndicator numbersRatioProgress;
    private LinearProgressIndicator numbersAvgProgress;
    private LinearProgressIndicator stepByStepAvgProgress;
    private LinearProgressIndicator associationsRatioProgress;
    private LinearProgressIndicator associationsAvgProgress;
    private LinearProgressIndicator skockoAvgProgress;
    private LinearProgressIndicator connectionsRatioProgress;
    private LinearProgressIndicator connectionsAvgProgress;
    private TextView WhoKnowsRatio;
    private TextView textGameStatKoZnaZna;
    private TextView NumbersGameRatio;
    private TextView NumbersGamePoints;
    private TextView StepByStepRatio;
    private TextView StepByStepPoints;
    private TextView AssociationsRatio;
    private TextView AssociationsPoints;
    private TextView SkockoRatio;
    private TextView SkockoPoints;
    private TextView ConnectionsRatio;
    private TextView ConnectionsPoints;

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
        avatarImage = view.findViewById(R.id.avatarImage);
        buttonEditAvatar = view.findViewById(R.id.buttonEditAvatar);
        avatarProgressBar = view.findViewById(R.id.avatarProgressBar);
        Button forgotPasswordButton = view.findViewById(R.id.btn_forgot_password);
        forgotPasswordButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), ChangePasswordActivity.class);
            startActivity(intent);
        });

        Button notificationsButton = view.findViewById(R.id.buttonNotifications);

        notificationsButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), NotificationsActivity.class);
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

        initStatsViews(view);
        statsRepository = new StatsRepository();
        loadRealStats();
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

                textUsername.setText(uname != null ? uname : "Nepoznato");
                textEmail.setText(em != null ? em : "Nepoznato");
                textRegion.setText(reg != null ? "Region: " + reg : "Region: Nepoznato");

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

    private void initStatsViews(View root) {
        textTotalGames = root.findViewById(R.id.textTotalGames);
        textWinLoseRatio = root.findViewById(R.id.textWinLoseRatio);
        winProgress = root.findViewById(R.id.winProgress);
        whoKnowsAvgProgress = root.findViewById(R.id.whoKnowsAvgProgress);
        numbersRatioProgress = root.findViewById(R.id.numbersRatioProgress);
        numbersAvgProgress = root.findViewById(R.id.numbersAvgProgress);
        stepByStepAvgProgress = root.findViewById(R.id.stepByStepAvgProgress);
        associationsRatioProgress = root.findViewById(R.id.associationsRatioProgress);
        associationsAvgProgress = root.findViewById(R.id.associationsAvgProgress);
        skockoAvgProgress = root.findViewById(R.id.skockoAvgProgress);
        connectionsRatioProgress = root.findViewById(R.id.connectionsRatioProgress);
        connectionsAvgProgress = root.findViewById(R.id.connectionsAvgProgress);
        WhoKnowsRatio = root.findViewById(R.id.WhoKnowsRatio);
        textGameStatKoZnaZna = root.findViewById(R.id.textGameStatKoZnaZna);
        NumbersGameRatio = root.findViewById(R.id.NumbersGameRatio);
        NumbersGamePoints = root.findViewById(R.id.NumbersGamePoints);
        StepByStepRatio = root.findViewById(R.id.StepByStepRatio);
        StepByStepPoints = root.findViewById(R.id.StepByStepPoints);
        AssociationsRatio = root.findViewById(R.id.AssociationsRatio);
        AssociationsPoints = root.findViewById(R.id.AssociationsPoints);
        SkockoRatio = root.findViewById(R.id.SkockoRatio);
        SkockoPoints = root.findViewById(R.id.SkockoPoints);
        ConnectionsRatio = root.findViewById(R.id.ConnectionsRatio);
        ConnectionsPoints = root.findViewById(R.id.ConnectionsPoints);
    }

    private void loadRealStats() {
        statsRepository.loadStats().addOnSuccessListener(stats -> {
            displayStats(stats);
        }).addOnFailureListener(e -> {
            displayEmptyStats();
        });
    }

    private void displayStats(StatsRepository.PlayerStats stats) {
        int goodColor = ContextCompat.getColor(requireContext(), R.color.correct_answer_color);
        int mediumColor = ContextCompat.getColor(requireContext(), R.color.performance_medium);
        int badColor = ContextCompat.getColor(requireContext(), R.color.wrong_answer_color);

        textTotalGames.setText(String.valueOf(stats.totalMatches));

        int totalDecided = stats.wins + stats.losses;
        int winPct = totalDecided > 0 ? stats.wins * 100 / totalDecided : 0;
        int lossPct = totalDecided > 0 ? stats.losses * 100 / totalDecided : 0;
        textWinLoseRatio.setText(winPct + "% / " + lossPct + "%");
        winProgress.setProgress(winPct);
        winProgress.setIndicatorColor(winPct >= 50 ? goodColor : badColor);

        int whoKnowsPct = stats.whoKnowsTotal > 0 ? stats.whoKnowsCorrect * 100 / stats.whoKnowsTotal : 0;
        WhoKnowsRatio.setText(stats.whoKnowsCorrect + " / " + stats.whoKnowsWrong);

        int whoKnowsAvg = stats.whoKnowsGames > 0 ? stats.whoKnowsScoreSum / stats.whoKnowsGames : 0;
        int whoKnowsMx = Math.max(stats.whoKnowsMaxScore, 50);
        textGameStatKoZnaZna.setText(whoKnowsAvg + " / " + whoKnowsMx);
        whoKnowsAvgProgress.setProgress(Math.min(whoKnowsAvg * 100 / Math.max(whoKnowsMx, 1), 100));
        whoKnowsAvgProgress.setIndicatorColor(getPerformanceColor(whoKnowsPct, goodColor, mediumColor, badColor));

        int numbersPct = stats.mojBrojTotal > 0 ? stats.mojBrojFound * 100 / stats.mojBrojTotal : 0;
        NumbersGameRatio.setText(numbersPct + "%");
        numbersRatioProgress.setProgress(numbersPct);
        numbersRatioProgress.setIndicatorColor(getPerformanceColor(numbersPct, goodColor, mediumColor, badColor));

        int numbersAvg = stats.mojBrojGames > 0 ? stats.mojBrojScoreSum / stats.mojBrojGames : 0;
        int numbersMx = Math.max(stats.mojBrojMaxScore, 20);
        NumbersGamePoints.setText(numbersAvg + " / " + numbersMx);
        numbersAvgProgress.setProgress(Math.min(numbersAvg * 100 / Math.max(numbersMx, 1), 100));
        numbersAvgProgress.setIndicatorColor(getPerformanceColor(numbersAvg * 100 / Math.max(numbersMx, 1), goodColor, mediumColor, badColor));

        int stepByStepPct = stats.korakTotal > 0 ? stats.korakFound * 100 / stats.korakTotal : 0;
        StringBuilder sb = new StringBuilder();
        sb.append("Pronadjeno: ").append(stepByStepPct).append("%\n");
        for (int i = 0; i < 7; i++) {
            int stepPct = stats.korakGames > 0 ? stats.korakStepCounts[i] * 100 / stats.korakGames : 0;
            sb.append("K").append(i+1).append(":").append(stepPct).append("% ");
        }
        StepByStepRatio.setText(sb.toString());
        int stepByStepAvg = stats.korakGames > 0 ? stats.korakScoreSum / stats.korakGames : 0;
        int stepByStepMx = Math.max(stats.korakMaxScore, 25);
        StepByStepPoints.setText(stepByStepAvg + " / " + stepByStepMx);
        stepByStepAvgProgress.setProgress(Math.min(stepByStepAvg * 100 / Math.max(stepByStepMx, 1), 100));
        stepByStepAvgProgress.setIndicatorColor(getPerformanceColor(stepByStepPct, goodColor, mediumColor, badColor));

        int asocPct = stats.asocijacijeTotal > 0 ? stats.asocijacijeSolved * 100 / stats.asocijacijeTotal : 0;
        AssociationsRatio.setText(asocPct + "% / " + (100 - asocPct) + "%");
        associationsRatioProgress.setProgress(asocPct);
        associationsRatioProgress.setIndicatorColor(getPerformanceColor(asocPct, goodColor, mediumColor, badColor));

        int asocAvg = stats.asocijacijeGames > 0 ? stats.asocijacijeScoreSum / stats.asocijacijeGames : 0;
        int asocMx = Math.max(stats.asocijacijeMaxScore, 60);
        AssociationsPoints.setText(asocAvg + " / " + asocMx);
        associationsAvgProgress.setProgress(Math.min(asocAvg * 100 / Math.max(asocMx, 1), 100));
        associationsAvgProgress.setIndicatorColor(getPerformanceColor(asocAvg * 100 / Math.max(asocMx, 1), goodColor, mediumColor, badColor));

        int skockoPct = stats.skockoTotal > 0 ? stats.skockoFound * 100 / stats.skockoTotal : 0;
        StringBuilder sb2 = new StringBuilder();
        sb2.append("Pronadjeno: ").append(skockoPct).append("%\n");
        for (int i = 1; i <= 6; i++) {
            int attPct = stats.skockoGames > 0 ? stats.skockoAttemptCounts[i] * 100 / stats.skockoGames : 0;
            sb2.append("P").append(i).append(":").append(attPct).append("% ");
        }
        SkockoRatio.setText(sb2.toString());
        int skockoAvg = stats.skockoGames > 0 ? stats.skockoScoreSum / stats.skockoGames : 0;
        int skockoMx = Math.max(stats.skockoMaxScore, 30);
        SkockoPoints.setText(skockoAvg + " / " + skockoMx);
        skockoAvgProgress.setProgress(Math.min(skockoAvg * 100 / Math.max(skockoMx, 1), 100));
        skockoAvgProgress.setIndicatorColor(getPerformanceColor(skockoPct, goodColor, mediumColor, badColor));

        int spojPct = stats.spojniceTotal > 0 ? stats.spojniceConnected * 100 / stats.spojniceTotal : 0;
        ConnectionsRatio.setText(spojPct + "%");
        connectionsRatioProgress.setProgress(spojPct);
        connectionsRatioProgress.setIndicatorColor(getPerformanceColor(spojPct, goodColor, mediumColor, badColor));

        int spojAvg = stats.spojniceGames > 0 ? stats.spojniceScoreSum / stats.spojniceGames : 0;
        int spojMx = Math.max(stats.spojniceMaxScore, 20);
        ConnectionsPoints.setText(spojAvg + " / " + spojMx);
        connectionsAvgProgress.setProgress(Math.min(spojAvg * 100 / Math.max(spojMx, 1), 100));
        connectionsAvgProgress.setIndicatorColor(getPerformanceColor(spojAvg * 100 / Math.max(spojMx, 1), goodColor, mediumColor, badColor));
    }

    private void displayEmptyStats() {
        textTotalGames.setText("0");
        textWinLoseRatio.setText("0% / 0%");
        winProgress.setProgress(0);
        WhoKnowsRatio.setText("0 / 0");
        textGameStatKoZnaZna.setText("0 / 50");
        whoKnowsAvgProgress.setProgress(0);
        NumbersGameRatio.setText("0%");
        numbersRatioProgress.setProgress(0);
        NumbersGamePoints.setText("0 / 20");
        numbersAvgProgress.setProgress(0);
        StepByStepRatio.setText("0%");
        StepByStepPoints.setText("0 / 25");
        SkockoRatio.setText("0%");
        stepByStepAvgProgress.setProgress(0);
        AssociationsRatio.setText("0% / 0%");
        associationsRatioProgress.setProgress(0);
        AssociationsPoints.setText("0 / 60");
        associationsAvgProgress.setProgress(0);
        SkockoRatio.setText("0%");
        SkockoPoints.setText("0 / 30");
        skockoAvgProgress.setProgress(0);
        ConnectionsRatio.setText("0%");
        connectionsRatioProgress.setProgress(0);
        ConnectionsPoints.setText("0 / 20");
        connectionsAvgProgress.setProgress(0);
    }

    private int getPerformanceColor(int percentage, int goodColor, int mediumColor, int badColor) {
        if (percentage >= 70) return goodColor;
        if (percentage >= 40) return mediumColor;
        return badColor;
    }
}
