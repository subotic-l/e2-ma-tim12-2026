package com.example.slagalica;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class ProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        Button logoutButton = findViewById(R.id.buttonLogout);
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(ProfileActivity.this, WelcomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        ImageView qrCodeImage = findViewById(R.id.qrCodeImage);
        String qrLink = getIntent().getStringExtra("qr_link");
        if (qrLink == null || qrLink.isEmpty()) {
            qrLink = "https://www.example.com";
        }
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