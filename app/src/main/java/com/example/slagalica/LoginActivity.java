package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.service.AuthService;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {

    private AuthService authService;

    private TextInputLayout userLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText userInput;
    private TextInputEditText passwordInput;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authService = new AuthService();

        userLayout = findViewById(R.id.login_user_layout);
        passwordLayout = findViewById(R.id.login_password_layout);
        userInput = findViewById(R.id.login_user);
        passwordInput = findViewById(R.id.login_password);
        loginButton = findViewById(R.id.btn_login_submit);

        loginButton.setOnClickListener(v -> attemptLogin());

        Button forgotPasswordButton = findViewById(R.id.btn_forgot_password);
        forgotPasswordButton.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });
    }

    private void attemptLogin() {
        userLayout.setError(null);
        passwordLayout.setError(null);

        String emailOrUsername = userInput.getText() != null
                ? userInput.getText().toString().trim() : "";
        String password = passwordInput.getText() != null
                ? passwordInput.getText().toString() : "";

        setLoading(true);

        authService.login(emailOrUsername, password)
                .addOnSuccessListener(user -> {
                    setLoading(false);
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    String message = e.getMessage();
                    if (message != null && (message.contains("password")
                            || message.contains("credential"))) {
                        passwordLayout.setError("Incorrect password");
                    } else if (message != null && message.contains("verified")) {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    } else if (message != null && message.contains("username")) {
                        userLayout.setError(message);
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        loginButton.setText(loading ? "Logging in…" : "Login");
    }
}