package com.example.slagalica.service;

import android.net.Uri;

import com.example.slagalica.data.UserRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Business-logic layer for user profile operations.
 * Activities call this class; it delegates I/O to UserRepository.
 */
public class UserService {

    private final UserRepository repository;

    public UserService() {
        this.repository = new UserRepository();
    }

    /** Returns the currently authenticated Firebase user, or null. */
    public FirebaseUser getCurrentUser() {
        return repository.getCurrentUser();
    }

    /**
     * Fetches the profile document for the currently signed-in user.
     * Returns null if no user is signed in.
     */
    public Task<DocumentSnapshot> loadProfile() {
        FirebaseUser user = repository.getCurrentUser();
        if (user == null) {
            return Tasks.forException(new IllegalStateException("No authenticated user"));
        }
        return repository.getUserProfile(user.getUid());
    }

    /**
     * Uploads the chosen avatar image to Cloudinary and updates the
     * avatarUrl field in Firestore.
     */
    public Task<String> uploadAndUpdateAvatar(Uri imageUri) {
        FirebaseUser user = repository.getCurrentUser();
        if (user == null) {
            return Tasks.forException(new IllegalStateException("No authenticated user"));
        }
        String uid = user.getUid();

        return repository.uploadAvatar(uid, imageUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    String url = task.getResult();
                    return repository.updateAvatarUrl(uid, url)
                            .continueWith(t -> url);
                });
    }

    // ------------------------------------------------------------- Tokens / Stars

    /** Deducts 1 token from the authenticated user's balance. */
    public Task<Void> deductToken() {
        FirebaseUser user = repository.getCurrentUser();
        if (user == null) {
            return Tasks.forException(new IllegalStateException("No authenticated user"));
        }
        return repository.deductToken(user.getUid());
    }

    /** Fetches a user profile document by uid directly. */
    public Task<DocumentSnapshot> getProfileByUid(String uid) {
        return repository.getUserProfile(uid);
    }

    /**
     * Calculates the stars delta for a match outcome.
     * @return positive delta for winner, negative for loser
     */
    public static int calculateStarsDelta(int score, boolean iWon) {
        int base = iWon ? 10 : -10;
        int bonus = score / 40;
        return base + bonus;
    }

    /**
     * Processes match rewards for the authenticated user.
     * Should only be called once per match (idempotency handled by caller).
     */
    public Task<Void> processMatchRewards(int myScore, boolean iWon) {
        FirebaseUser user = repository.getCurrentUser();
        if (user == null) {
            return Tasks.forException(new IllegalStateException("No authenticated user"));
        }
        int delta = calculateStarsDelta(myScore, iWon);
        return repository.applyMatchRewards(user.getUid(), delta);
    }

    /** Returns true if the authenticated user has at least 1 token. */
    public Task<Boolean> hasEnoughTokens() {
        FirebaseUser user = repository.getCurrentUser();
        if (user == null) {
            return Tasks.forResult(false);
        }
        return repository.getUserProfile(user.getUid())
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) return false;
                    Long tokens = task.getResult().getLong("tokens");
                    return tokens != null && tokens >= 1;
                });
    }
}
