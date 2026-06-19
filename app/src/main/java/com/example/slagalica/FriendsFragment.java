package com.example.slagalica;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.slagalica.service.UserService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendsFragment extends Fragment {

    private static final String FRIENDS_COLLECTION = "friends";

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private UserService userService;

    private TextInputEditText searchInput;
    private MaterialButton searchButton;
    private MaterialButton scanQrButton;
    private MaterialButton galleryQrButton;
    private TextView searchResultText;
    private RecyclerView friendsRecyclerView;
    private TextView emptyText;

    private FriendsAdapter adapter;
    private List<FriendEntry> friendList;
    private ListenerRegistration friendsListener;

    private final ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() == null) {
                    Toast.makeText(requireContext(), "Skeniranje otkazano", Toast.LENGTH_SHORT).show();
                    return;
                }
                String scannedUid = result.getContents();
                lookupScannedUser(scannedUid);
            });

    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != android.app.Activity.RESULT_OK || result.getData() == null) return;
                Uri imageUri = result.getData().getData();
                if (imageUri == null) return;
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                            requireActivity().getContentResolver(), imageUri);
                    String decoded = decodeQrFromBitmap(bitmap);
                    if (decoded != null) {
                        lookupScannedUser(decoded);
                    } else {
                        Toast.makeText(requireContext(), "QR kod nije pronađen na slici",
                                Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Greška pri čitanju slike: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });

    private String decodeQrFromBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
        try {
            Result result = new MultiFormatReader().decode(binaryBitmap);
            return result.getText();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friends, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        userService = new UserService();

        searchInput = view.findViewById(R.id.searchUsernameInput);
        searchButton = view.findViewById(R.id.buttonSearchUser);
        scanQrButton = view.findViewById(R.id.buttonScanQr);
        galleryQrButton = view.findViewById(R.id.buttonGalleryQr);
        searchResultText = view.findViewById(R.id.searchResultText);
        friendsRecyclerView = view.findViewById(R.id.friendsRecyclerView);
        emptyText = view.findViewById(R.id.emptyFriendsText);

        friendList = new ArrayList<>();
        adapter = new FriendsAdapter(friendList, this::onPlayWithFriend);
        friendsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        friendsRecyclerView.setAdapter(adapter);

        searchButton.setOnClickListener(v -> searchUser());
        scanQrButton.setOnClickListener(v -> startQrScan());
        galleryQrButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        });
        listenToFriends();
    }

    private void startQrScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Skeniraj QR kod prijatelja");
        options.setBeepEnabled(false);
        options.setOrientationLocked(true);
        options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity.class);
        qrLauncher.launch(options);
    }

    private void lookupScannedUser(String uid) {
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Morate biti prijavljeni", Toast.LENGTH_SHORT).show();
            return;
        }
        if (uid.equals(currentUser.getUid())) {
            Toast.makeText(requireContext(), "To ste vi!", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(requireContext(), "Korisnik nije pronađen", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String username = doc.getString("username");
                    String avatarUrl = doc.getString("avatarUrl");

                    db.collection("users").document(currentUser.getUid())
                            .collection(FRIENDS_COLLECTION).document(uid)
                            .get()
                            .addOnSuccessListener(friendDoc -> {
                                if (friendDoc.exists()) {
                                    Toast.makeText(requireContext(),
                                            (username != null ? username : "Korisnik") + " je već vaš prijatelj.",
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                showAddFriendDialog(uid, username, avatarUrl);
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void searchUser() {
        String username = searchInput.getText().toString().trim();
        if (username.isEmpty()) {
            Toast.makeText(requireContext(), "Unesite korisničko ime", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUser == null) {
            Toast.makeText(requireContext(), "Morate biti prijavljeni", Toast.LENGTH_SHORT).show();
            return;
        }

        searchResultText.setVisibility(View.GONE);

        db.collection("users")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        searchResultText.setText("Korisnik '" + username + "' nije pronađen.");
                        searchResultText.setVisibility(View.VISIBLE);
                        return;
                    }

                    QueryDocumentSnapshot doc = (QueryDocumentSnapshot) query.getDocuments().get(0);
                    String foundId = doc.getId();
                    String foundUsername = doc.getString("username");

                    if (foundId.equals(currentUser.getUid())) {
                        searchResultText.setText("To ste vi!");
                        searchResultText.setVisibility(View.VISIBLE);
                        return;
                    }

                    db.collection("users").document(currentUser.getUid())
                            .collection(FRIENDS_COLLECTION).document(foundId)
                            .get()
                            .addOnSuccessListener(friendDoc -> {
                                if (friendDoc.exists()) {
                                    searchResultText.setText(foundUsername + " je već vaš prijatelj.");
                                    searchResultText.setVisibility(View.VISIBLE);
                                    return;
                                }

                                showAddFriendDialog(foundId, foundUsername, doc.getString("avatarUrl"));
                            });
                })
                .addOnFailureListener(e -> {
                    searchResultText.setText("Greška pri pretrazi: " + e.getMessage());
                    searchResultText.setVisibility(View.VISIBLE);
                });
    }

    private void showAddFriendDialog(String friendId, String friendUsername, String avatarUrl) {
        new androidx.appcompat.app.AlertDialog.Builder(requireActivity())
                .setTitle("Dodaj prijatelja")
                .setMessage("Da li želite da dodate " + (friendUsername != null ? friendUsername : "ovog korisnika") + " u listu prijatelja?")
                .setPositiveButton("Dodaj", (dialog, which) -> addFriend(friendId, friendUsername, avatarUrl))
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void addFriend(String friendId, String friendUsername, String avatarUrl) {
        Map<String, Object> myFriend = new HashMap<>();
        myFriend.put("friendId", friendId);
        myFriend.put("username", friendUsername);
        myFriend.put("avatarUrl", avatarUrl != null ? avatarUrl : "");
        myFriend.put("addedAt", FieldValue.serverTimestamp());

        Map<String, Object> theirFriend = new HashMap<>();
        theirFriend.put("friendId", currentUser.getUid());
        theirFriend.put("addedAt", FieldValue.serverTimestamp());

        db.collection("users").document(currentUser.getUid())
                .collection(FRIENDS_COLLECTION).document(friendId)
                .set(myFriend)
                .addOnSuccessListener(aVoid -> {
                    db.collection("users").document(friendId)
                            .collection(FRIENDS_COLLECTION)
                            .document(currentUser.getUid())
                            .set(theirFriend);
                    Toast.makeText(requireContext(), (friendUsername != null ? friendUsername : "Korisnik") + " je dodat u prijatelje",
                            Toast.LENGTH_SHORT).show();
                    searchInput.setText("");
                    searchResultText.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Greška: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void listenToFriends() {
        if (currentUser == null) return;

        friendsListener = db.collection("users")
                .document(currentUser.getUid())
                .collection(FRIENDS_COLLECTION)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    friendList.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        String friendId = doc.getId();

                        String username = doc.getString("username");
                        String avatarUrl = doc.getString("avatarUrl");
                        FriendEntry entry = new FriendEntry(friendId, username, avatarUrl);
                        friendList.add(entry);

                        loadFriendProfile(entry);
                    }

                    if (friendList.isEmpty()) {
                        emptyText.setVisibility(View.VISIBLE);
                        friendsRecyclerView.setVisibility(View.GONE);
                    } else {
                        emptyText.setVisibility(View.GONE);
                        friendsRecyclerView.setVisibility(View.VISIBLE);
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void loadFriendProfile(FriendEntry entry) {
        if (entry.friendId == null) return;

        db.collection("users").document(entry.friendId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String freshUsername = doc.getString("username");
                        if (freshUsername != null) {
                            entry.username = freshUsername;
                        }
                        entry.stars = doc.getLong("stars") != null ? doc.getLong("stars") : 0;
                        entry.league = doc.getLong("league") != null ? doc.getLong("league") : 0;
                        entry.avatarUrl = doc.getString("avatarUrl");
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private void onPlayWithFriend(FriendEntry friend) {
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Morate biti prijavljeni", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(requireActivity(), FriendLobbyActivity.class);
        intent.putExtra("friendId", friend.friendId);
        intent.putExtra("friendName", friend.username);
        intent.putExtra("friendAvatar", friend.avatarUrl != null ? friend.avatarUrl : "");
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (friendsListener != null) {
            friendsListener.remove();
        }
    }

    private static class FriendEntry {
        final String friendId;
        String username;
        String avatarUrl;
        long stars;
        long league;

        FriendEntry(String friendId, String username, String avatarUrl) {
            this.friendId = friendId;
            this.username = username;
            this.avatarUrl = avatarUrl;
            this.stars = 0;
            this.league = 0;
        }
    }

    private static class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.ViewHolder> {

        private final List<FriendEntry> items;
        private final OnPlayListener listener;

        interface OnPlayListener {
            void onPlay(FriendEntry friend);
        }

        FriendsAdapter(List<FriendEntry> items, OnPlayListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_friend, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FriendEntry entry = items.get(position);

            holder.usernameText.setText(entry.username != null ? entry.username : "Nepoznato");
            holder.starsText.setText(entry.stars + " zvezda");

            String[] leagueNames = {"Liga 0", "Liga 1", "Liga 2", "Liga 3", "Liga 4", "Liga 5"};
            int leagueIdx = (int) entry.league;
            if (leagueIdx >= 0 && leagueIdx < leagueNames.length) {
                holder.leagueText.setText(leagueNames[leagueIdx]);
            } else {
                holder.leagueText.setText("Liga " + leagueIdx);
            }

            String url = entry.avatarUrl;
            if (url != null && !url.isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(url)
                        .placeholder(R.drawable.default_profile)
                        .error(R.drawable.default_profile)
                        .circleCrop()
                        .into(holder.avatarImage);
            } else {
                holder.avatarImage.setImageResource(R.drawable.default_profile);
            }

            holder.playButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlay(entry);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView avatarImage;
            final TextView usernameText;
            final TextView starsText;
            final TextView leagueText;
            final MaterialButton playButton;

            ViewHolder(View itemView) {
                super(itemView);
                avatarImage = itemView.findViewById(R.id.friendAvatar);
                usernameText = itemView.findViewById(R.id.friendUsername);
                starsText = itemView.findViewById(R.id.friendStars);
                leagueText = itemView.findViewById(R.id.friendLeague);
                playButton = itemView.findViewById(R.id.buttonPlayFriend);
            }
        }
    }
}
