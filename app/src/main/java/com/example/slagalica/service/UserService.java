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
}
