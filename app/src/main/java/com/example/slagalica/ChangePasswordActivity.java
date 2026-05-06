package com.example.slagalica;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.service.AuthService;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ChangePasswordActivity extends AppCompatActivity {

    private AuthService authService;

    private TextInputLayout currentPasswordLayout;
    private TextInputLayout newPasswordLayout;
    private TextInputLayout newPasswordRepeatLayout;

    private TextInputEditText currentPasswordInput;
    private TextInputEditText newPasswordInput;
    private TextInputEditText newPasswordRepeatInput;
    private Button changeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        authService = new AuthService();

        currentPasswordLayout = findViewById(R.id.change_current_password_layout);
        newPasswordLayout = findViewById(R.id.change_new_password_layout);
        newPasswordRepeatLayout = findViewById(R.id.change_new_password_repeat_layout);

        currentPasswordInput = findViewById(R.id.change_current_password);
        newPasswordInput = findViewById(R.id.change_new_password);
        newPasswordRepeatInput = findViewById(R.id.change_new_password_repeat);
        changeButton = findViewById(R.id.btn_change_password_submit);

        changeButton.setOnClickListener(v -> attemptChangePassword());
    }

    private void attemptChangePassword() {
        currentPasswordLayout.setError(null);
        newPasswordLayout.setError(null);
        newPasswordRepeatLayout.setError(null);

        String currentPassword = getText(currentPasswordInput);
        String newPassword = getText(newPasswordInput);
        String newPasswordRepeat = getText(newPasswordRepeatInput);

        setLoading(true);

        authService.changePassword(currentPassword, newPassword, newPasswordRepeat)
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    Toast.makeText(this,
                            "Password changed successfully.",
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    String message = e.getMessage();
                    if (message != null && message.contains("current")) {
                        currentPasswordLayout.setError(message);
                    } else if (message != null && message.contains("new password")) {
                        newPasswordLayout.setError(message);
                    } else if (message != null && message.contains("match")) {
                        newPasswordRepeatLayout.setError(message);
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        changeButton.setEnabled(!loading);
        changeButton.setText(loading ? "Changing…" : "Change Password");
    }

    private String getText(TextInputEditText input) {
        return input.getText() != null ? input.getText().toString() : "";
    }
}
