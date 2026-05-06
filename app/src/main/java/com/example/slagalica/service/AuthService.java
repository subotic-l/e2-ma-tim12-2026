package com.example.slagalica.service;

import android.util.Patterns;

import com.example.slagalica.data.UserRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseUser;

/**
 * Business-logic layer: validates inputs, orchestrates registration / login /
 * password flows.  Activities call this class; it delegates I/O to UserRepository.
 */
public class AuthService {

    private final UserRepository repository;

    public AuthService() {
        this.repository = new UserRepository();
    }

    // ---------------------------------------------------------------- Register

    /**
     * Validates inputs, creates the Firebase Auth user, saves the profile in
     * Firestore, and sends an email-verification message.
     *
     * @return Task<Void> that succeeds when all three steps are done.
     */
    public Task<Void> register(String email, String username,
                               String region, String password,
                               String passwordRepeat) {

        // --- client-side validation ---
        String validationError = validateRegistration(
                email, username, region, password, passwordRepeat);
        if (validationError != null) {
            return Tasks.forException(new IllegalArgumentException(validationError));
        }

        // --- create auth account ---
        return repository.createAuthUser(email, password)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();

                    AuthResult result = task.getResult();
                    FirebaseUser user = result.getUser();
                    if (user == null) throw new Exception("User creation failed");

                    String uid = user.getUid();

                    // Save profile and send verification in parallel via chaining
                    Task<Void> saveProfile = repository.saveUserProfile(
                            uid, email, username, region);
                    Task<Void> sendVerification = repository.sendEmailVerification();

                    return Tasks.whenAll(saveProfile, sendVerification);
                });
    }

    // ------------------------------------------------------------------ Login

    /**
     * Accepts either an e-mail address or a username + password.
     * If a username is given it is resolved to an e-mail via Firestore first.
     * Returns the signed-in {@link FirebaseUser} task.
     * Fails if the account's email is not yet verified.
     */
    public Task<FirebaseUser> login(String emailOrUsername, String password) {
        if (emailOrUsername == null || emailOrUsername.trim().isEmpty()) {
            return Tasks.forException(
                    new IllegalArgumentException("Email or username is required"));
        }
        if (password == null || password.isEmpty()) {
            return Tasks.forException(
                    new IllegalArgumentException("Password is required"));
        }

        String trimmed = emailOrUsername.trim();

        // Determine if input looks like an e-mail address.
        if (Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
            return signInAndVerify(trimmed, password);
        }

        // Username: resolve to email first.
        return repository.findEmailByUsername(trimmed)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    String email = task.getResult();
                    if (email == null) {
                        throw new IllegalArgumentException(
                                "No account found for that username");
                    }
                    return signInAndVerify(email, password);
                });
    }

    // --------------------------------------------------------- Password reset

    /** Sends a password-reset email.  Input must be a valid email address. */
    public Task<Void> sendPasswordReset(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Tasks.forException(
                    new IllegalArgumentException("Email is required"));
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            return Tasks.forException(
                    new IllegalArgumentException("Enter a valid email address"));
        }
        return repository.sendPasswordResetEmail(email.trim());
    }

    // --------------------------------------------------------- Password change

    /**
     * Changes the password for the currently authenticated user.
     * Requires the old password for re-authentication.
     */
    public Task<Void> changePassword(String currentPassword,
                                     String newPassword,
                                     String newPasswordRepeat) {
        if (currentPassword == null || currentPassword.isEmpty()) {
            return Tasks.forException(
                    new IllegalArgumentException("Current password is required"));
        }
        if (newPassword == null || newPassword.length() < 6) {
            return Tasks.forException(
                    new IllegalArgumentException(
                            "New password must be at least 6 characters"));
        }
        if (!newPassword.equals(newPasswordRepeat)) {
            return Tasks.forException(
                    new IllegalArgumentException("New passwords do not match"));
        }
        return repository.changePassword(currentPassword, newPassword);
    }

    // ---------------------------------------------------------------- Helpers

    public FirebaseUser getCurrentUser() {
        return repository.getCurrentUser();
    }

    public void signOut() {
        repository.signOut();
    }

    // ---------------------------------------------------------------- Private

    private Task<FirebaseUser> signInAndVerify(String email, String password) {
        return repository.signInWithEmail(email, password)
                .continueWith(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    FirebaseUser user = task.getResult().getUser();
                    if (user == null) throw new Exception("Sign-in failed");
                    if (!user.isEmailVerified()) {
                        repository.signOut();
                        throw new IllegalStateException(
                                "Email not verified. Check your inbox and verify "
                                        + "your email before logging in.");
                    }
                    return user;
                });
    }

    private String validateRegistration(String email, String username,
                                        String region, String password,
                                        String passwordRepeat) {
        if (email == null || email.trim().isEmpty()) return "Email is required";
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            return "Enter a valid email address";
        }
        if (username == null || username.trim().isEmpty()) return "Username is required";
        if (region == null || region.trim().isEmpty()) return "Region is required";
        if (password == null || password.length() < 6) {
            return "Password must be at least 6 characters";
        }
        if (!password.equals(passwordRepeat)) return "Passwords do not match";
        return null;
    }
}
