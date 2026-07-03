package com.example.slagalica.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.Executors;

public class FcmHelper {

    private static final String TAG = "FcmHelper";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String PREFS_NAME = "fcm_helper_prefs";
    private static final String KEY_ACCESS_TOKEN = "fcm_access_token";
    private static final String KEY_TOKEN_EXPIRY = "fcm_token_expiry";

    private static Context appContext;
    private static String projectId;
    private static String clientEmail;
    private static PrivateKey privateKey;

    public static void initialize(Context context) {
        appContext = context.getApplicationContext();
        try {
            InputStream is = context.getAssets().open("firebase_service_account.json");
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONObject json = new JSONObject(sb.toString());
            projectId = json.getString("project_id");
            clientEmail = json.getString("client_email");
            Log.d(TAG, "Loaded service account: " + clientEmail + " project=" + projectId);

            String pkPem = json.getString("private_key");
            String pkPemClean = pkPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] pkBytes = Base64.decode(pkPemClean, Base64.DEFAULT);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            privateKey = kf.generatePrivate(spec);
            Log.d(TAG, "Private key loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "initialize FAILED: " + e.getMessage(), e);
        }
    }

    public static void sendFriendInvitationPush(Context context, String targetUid,
                                                 String fromName, String fromId,
                                                 String invitationId) {
        if (projectId == null || clientEmail == null || privateKey == null) {
            Log.w(TAG, "sendFriendInvitationPush skipped - not initialized");
            return;
        }

        Log.d(TAG, "sendFriendInvitationPush to uid=" + targetUid + " from=" + fromName);

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(targetUid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Log.w(TAG, "Target user doc not found");
                        return;
                    }
                    String token = doc.getString("fcmToken");
                    if (token == null || token.isEmpty()) {
                        Log.w(TAG, "Target user has no fcmToken");
                        return;
                    }
                    Log.d(TAG, "Found FCM token for " + targetUid + ": " + token.substring(0, Math.min(20, token.length())) + "...");

                    Executors.newSingleThreadExecutor().execute(() -> {
                        try {
                            String accessToken = getAccessToken(context);
                            Log.d(TAG, "Got OAuth access token");

                            JSONObject messageData = new JSONObject();
                            messageData.put("type", "friend_invitation");
                            messageData.put("fromName", fromName);
                            messageData.put("fromId", fromId);
                            messageData.put("invitationId", invitationId);
                            messageData.put("fromAvatar", "");

                            JSONObject notification = new JSONObject();
                            notification.put("title", "Poziv za partiju");
                            notification.put("body", fromName + " vas poziva na prijateljsku partiju Slagalice!");

                            JSONObject message = new JSONObject();
                            message.put("token", token);
                            message.put("notification", notification);
                            message.put("data", messageData);

                            JSONObject root = new JSONObject();
                            root.put("message", message);

                            String endpoint = "https://fcm.googleapis.com/v1/projects/"
                                    + projectId + "/messages:send";
                            Log.d(TAG, "Sending FCM to " + endpoint);

                            URL url = new URL(endpoint);
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                            conn.setDoOutput(true);

                            OutputStream os = conn.getOutputStream();
                            os.write(root.toString().getBytes(StandardCharsets.UTF_8));
                            os.close();

                            int code = conn.getResponseCode();
                            String respBody = "";
                            BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(conn.getErrorStream() != null
                                            ? conn.getErrorStream() : conn.getInputStream(),
                                            StandardCharsets.UTF_8));
                            StringBuilder rb = new StringBuilder();
                            String l;
                            while ((l = reader.readLine()) != null) rb.append(l);
                            reader.close();
                            respBody = rb.toString();
                            conn.disconnect();

                            Log.d(TAG, "FCM response code=" + code + " body=" + respBody);
                        } catch (Exception e) {
                            Log.e(TAG, "sendFriendInvitationPush error: " + e.getMessage(), e);
                        }
                    });
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to read target user: " + e.getMessage(), e));
    }

    public static void sendChatPush(String targetUid, String senderName, String messageText, String regionCode) {
        if (projectId == null || clientEmail == null || privateKey == null) {
            Log.w(TAG, "sendChatPush skipped - not initialized");
            return;
        }

        Log.d(TAG, "sendChatPush to uid=" + targetUid + " from=" + senderName);

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(targetUid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Log.w(TAG, "Target user doc not found");
                        return;
                    }
                    String token = doc.getString("fcmToken");
                    if (token == null || token.isEmpty()) {
                        Log.w(TAG, "Target user has no fcmToken");
                        return;
                    }

                    Executors.newSingleThreadExecutor().execute(() -> {
                        try {
                            String accessToken = getAccessToken(appContext);

                            JSONObject notification = new JSONObject();
                            notification.put("title", senderName);
                            notification.put("body", messageText);

                            JSONObject messageData = new JSONObject();
                            messageData.put("type", "chat_message");
                            messageData.put("channelId", "chat");
                            messageData.put("title", senderName);
                            messageData.put("body", messageText);
                            messageData.put("regionCode", regionCode);

                            JSONObject message = new JSONObject();
                            message.put("token", token);
                            message.put("notification", notification);
                            message.put("data", messageData);

                            JSONObject root = new JSONObject();
                            root.put("message", message);

                            String endpoint = "https://fcm.googleapis.com/v1/projects/"
                                    + projectId + "/messages:send";

                            URL url = new URL(endpoint);
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                            conn.setDoOutput(true);

                            OutputStream os = conn.getOutputStream();
                            os.write(root.toString().getBytes(StandardCharsets.UTF_8));
                            os.close();

                            int code = conn.getResponseCode();
                            BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(conn.getErrorStream() != null
                                            ? conn.getErrorStream() : conn.getInputStream(),
                                            StandardCharsets.UTF_8));
                            StringBuilder rb = new StringBuilder();
                            String l;
                            while ((l = reader.readLine()) != null) rb.append(l);
                            reader.close();
                            String respBody = rb.toString();
                            conn.disconnect();

                            Log.d(TAG, "sendChatPush FCM response code=" + code + " body=" + respBody);
                        } catch (Exception e) {
                            Log.e(TAG, "sendChatPush error: " + e.getMessage(), e);
                        }
                    });
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to read target user: " + e.getMessage(), e));
    }

    private static String getAccessToken(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0);
        String cached = prefs.getString(KEY_ACCESS_TOKEN, null);
        if (cached != null && System.currentTimeMillis() < expiry) {
            Log.d(TAG, "Using cached access token");
            return cached;
        }

        long now = System.currentTimeMillis() / 1000;
        long exp = now + 3600;

        JSONObject header = new JSONObject();
        header.put("alg", "RS256");
        header.put("typ", "JWT");

        JSONObject payload = new JSONObject();
        payload.put("iss", clientEmail);
        payload.put("scope", FCM_SCOPE);
        payload.put("aud", TOKEN_URL);
        payload.put("exp", exp);
        payload.put("iat", now);

        String b64Header = Base64.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        String b64Payload = Base64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        String signingInput = b64Header + "." + b64Payload;

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        byte[] signature = sig.sign();
        String b64Signature = Base64.encodeToString(signature,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);

        String jwt = signingInput + "." + b64Signature;

        String body = "grant_type=" + java.net.URLEncoder.encode(
                "urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8")
                + "&assertion=" + java.net.URLEncoder.encode(jwt, "UTF-8");

        URL url = new URL(TOKEN_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);

        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes(StandardCharsets.UTF_8));
        os.close();

        int code = conn.getResponseCode();
        BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                        StandardCharsets.UTF_8));
        StringBuilder resp = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) resp.append(line);
        br.close();
        conn.disconnect();

        if (code < 200 || code >= 300) {
            Log.e(TAG, "OAuth token exchange failed: " + code + " " + resp);
            throw new Exception("OAuth failed: " + resp);
        }

        JSONObject result = new JSONObject(resp.toString());
        String accessToken = result.getString("access_token");
        Log.d(TAG, "Got new OAuth access token, expires in " + result.optInt("expires_in", 0) + "s");

        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putLong(KEY_TOKEN_EXPIRY, (now + 3500) * 1000)
                .apply();

        return accessToken;
    }
}
