package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.data.RegionRepository;
import com.example.slagalica.service.AuthService;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class RegisterActivity extends AppCompatActivity {

    private AuthService authService;

    private TextInputLayout emailLayout;
    private TextInputLayout usernameLayout;
    private TextInputLayout regionLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout passwordRepeatLayout;

    private TextInputEditText emailInput;
    private TextInputEditText usernameInput;
    private MaterialAutoCompleteTextView regionInput;
    private TextInputEditText passwordInput;
    private TextInputEditText passwordRepeatInput;
    private Button registerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authService = new AuthService();

        emailLayout = findViewById(R.id.register_email_layout);
        usernameLayout = findViewById(R.id.register_username_layout);
        regionLayout = findViewById(R.id.register_region_layout);
        passwordLayout = findViewById(R.id.register_password_layout);
        passwordRepeatLayout = findViewById(R.id.register_password_repeat_layout);

        emailInput = findViewById(R.id.register_email);
        usernameInput = findViewById(R.id.register_username);
        regionInput = findViewById(R.id.register_region);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                RegionRepository.getAllNames()
        );

        regionInput.setAdapter(adapter);
        regionInput.setFocusable(false);
        regionInput.setClickable(true);
        regionInput.setOnClickListener(v -> regionInput.showDropDown());
        passwordInput = findViewById(R.id.register_password);
        passwordRepeatInput = findViewById(R.id.register_password_repeat);
        registerButton = findViewById(R.id.btn_register_submit);

        registerButton.setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        clearErrors();

        String email = getText(emailInput);
        String username = getText(usernameInput);
        String region = regionInput.getText() != null
                ? regionInput.getText().toString()
                : "";
        // Passwords are not trimmed to preserve exactly what the user typed.
        String password = getRawText(passwordInput);
        String passwordRepeat = getRawText(passwordRepeatInput);

        setLoading(true);

        authService.register(email, username, region, password, passwordRepeat)
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    Toast.makeText(this,
                            "Registration successful! Please check your email to verify your account.",
                            Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    String message = e.getMessage();
                    if (message != null && message.contains("email")) {
                        emailLayout.setError(message);
                    } else if (message != null && message.contains("password")) {
                        passwordLayout.setError(message);
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void clearErrors() {
        emailLayout.setError(null);
        usernameLayout.setError(null);
        regionLayout.setError(null);
        passwordLayout.setError(null);
        passwordRepeatLayout.setError(null);
    }

    private void setLoading(boolean loading) {
        registerButton.setEnabled(!loading);
        registerButton.setText(loading ? "Registering…" : "Register");
    }

    private String getText(TextInputEditText input) {
        return input.getText() != null ? input.getText().toString().trim() : "";
    }

    private String getRawText(TextInputEditText input) {
        return input.getText() != null ? input.getText().toString() : "";
    }
}