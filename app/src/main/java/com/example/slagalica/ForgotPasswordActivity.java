package com.example.slagalica;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.service.AuthService;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ForgotPasswordActivity extends AppCompatActivity {

    private AuthService authService;

    private TextInputLayout emailLayout;
    private TextInputEditText emailInput;
    private Button sendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        authService = new AuthService();

        emailLayout = findViewById(R.id.forgot_email_layout);
        emailInput = findViewById(R.id.forgot_email);
        sendButton = findViewById(R.id.btn_send_reset);

        sendButton.setOnClickListener(v -> attemptSendReset());
    }

    private void attemptSendReset() {
        emailLayout.setError(null);

        String email = emailInput.getText() != null
                ? emailInput.getText().toString().trim() : "";

        sendButton.setEnabled(false);
        sendButton.setText("Sending…");

        authService.sendPasswordReset(email)
                .addOnSuccessListener(unused -> {
                    sendButton.setEnabled(true);
                    sendButton.setText("Send Reset Email");
                    Toast.makeText(this,
                            "Password reset email sent. Check your inbox.",
                            Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    sendButton.setEnabled(true);
                    sendButton.setText("Send Reset Email");
                    String message = e.getMessage();
                    if (message != null && message.contains("email")) {
                        emailLayout.setError(message);
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
