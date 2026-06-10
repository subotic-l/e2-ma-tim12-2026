package com.example.slagalica.data;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * Data-access layer: wraps Firebase Auth and Firestore.
 * Activities should never call Firebase directly – they go through AuthService / UserService.
 */
public class UserRepository {

    private static final String USERS_COLLECTION = "users";

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public UserRepository() {
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
    }

    // ------------------------------------------------------------------ Auth

    /** Creates a Firebase Auth account and returns the AuthResult task. */
    public Task<AuthResult> createAuthUser(String email, String password) {
        return auth.createUserWithEmailAndPassword(email, password);
    }

    /** Signs in with email + password. */
    public Task<AuthResult> signInWithEmail(String email, String password) {
        return auth.signInWithEmailAndPassword(email, password);
    }

    /** Sends a password-reset email to the given address. */
    public Task<Void> sendPasswordResetEmail(String email) {
        return auth.sendPasswordResetEmail(email);
    }

    /**
     * Re-authenticates the current user with their current password, then
     * updates to the new password.
     */
    public Task<Void> changePassword(String currentPassword, String newPassword) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            return Tasks.forException(new Exception("No authenticated user"));
        }
        return user.reauthenticate(
                EmailAuthProvider.getCredential(user.getEmail(), currentPassword))
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return user.updatePassword(newPassword);
                });
    }

    /** Sends an email-verification message to the currently signed-in user. */
    public Task<Void> sendEmailVerification() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return Tasks.forException(new Exception("No authenticated user"));
        }
        return user.sendEmailVerification();
    }

    /** Signs out the current user. */
    public void signOut() {
        auth.signOut();
    }

    /** Returns the currently signed-in user, or null. */
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    // --------------------------------------------------------------- Firestore

    /**
     * Looks up the email for a given username by querying the users collection.
     * Returns a Task that resolves to the email string, or null if not found.
     */
    public Task<String> findEmailByUsername(String username) {
        return db.collection(USERS_COLLECTION)
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) return null;
                    QuerySnapshot snap = task.getResult();
                    if (snap.isEmpty()) return null;
                    DocumentSnapshot doc = snap.getDocuments().get(0);
                    return doc.getString("email");
                });
    }

    // ------------------------------------------------------------ Profile

    /** Fetches the full user profile document from Firestore. */
    public Task<DocumentSnapshot> getUserProfile(String uid) {
        return db.collection(USERS_COLLECTION).document(uid).get();
    }

    // ------------------------------------------------------------- Avatar

    /**
     * Uploads the image at the given Uri to Cloudinary and returns the URL.
     * Uses the Cloudinary Android SDK via MediaManager.
     */
    public Task<String> uploadAvatar(String uid, Uri imageUri) {
        TaskCompletionSource<String> tcs = new TaskCompletionSource<>();

        MediaManager.get().upload(imageUri)
                .option("public_id", "avatar_" + uid)
                .option("folder", "slagalica_avatars")
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {}

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        if (url == null) {
                            url = (String) resultData.get("url");
                        }
                        if (url != null) {
                            tcs.setResult(url);
                        } else {
                            tcs.setException(new Exception("Failed to get URL from Cloudinary"));
                        }
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        tcs.setException(new Exception(
                                "Cloudinary error: " + error.getDescription()));
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        tcs.setException(new Exception(
                                "Cloudinary reschedule: " + error.getDescription()));
                    }
                })
                .dispatch();

        return tcs.getTask();
    }

    /** Updates the avatarUrl field in the user's Firestore document. */
    public Task<Void> updateAvatarUrl(String uid, String avatarUrl) {
        return db.collection(USERS_COLLECTION)
                .document(uid)
                .update("avatarUrl", avatarUrl);
    }

    // --------------------------------------------------------- Initial profile

    /**
     * Saves the initial user profile in Firestore after registration.
     * Includes default values for tokens, stars, league, and avatarUrl.
     */
    public Task<Void> saveUserProfile(String uid, String email,
                                      String username, String region) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("email", email);
        profile.put("username", username);
        profile.put("region", region);
        profile.put("avatarUrl", "");
        profile.put("tokens", 5);
        profile.put("stars", 0);
        profile.put("league", 0);

        return db.collection(USERS_COLLECTION).document(uid).set(profile);
    }
}
