package com.example.slagalica;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.data.RegionRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatFragment extends Fragment {

    private static final String ARG_REGION_CODE = "region_code";

    private String regionCode;
    private String regionName;
    private ChatRepository chatRepository;
    private FirebaseUser currentUser;
    private String senderName;

    private TextView chatTitle;
    private RecyclerView messagesRecyclerView;
    private EditText chatInput;
    private ImageButton chatSendButton;
    private View chatBackButton;


    private MessageAdapter adapter;
    private List<MessageEntry> messageList;
    private ListenerRegistration messagesListener;

    public static ChatFragment newInstance(String regionCode) {
        ChatFragment fragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putString(ARG_REGION_CODE, regionCode);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            regionCode = getArguments().getString(ARG_REGION_CODE);
        }
        com.example.slagalica.Region region = RegionRepository.get(regionCode);
        regionName = region != null ? region.getName() : "Nepoznat region";
        chatRepository = new ChatRepository();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        loadSenderName();
    }

    private void loadSenderName() {
        if (currentUser == null) {
            senderName = "Gost";
            return;
        }

        if (currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()) {
            senderName = currentUser.getDisplayName();
            cacheSenderName(senderName);
            return;
        }

        SharedPreferences prefs = requireActivity()
                .getSharedPreferences("chat_prefs", 0);
        senderName = prefs.getString("senderName_" + currentUser.getUid(), null);
        if (senderName != null) return;

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String username = doc.getString("username");
                    if (username != null) {
                        senderName = username;
                        cacheSenderName(username);
                    }
                })
                .addOnFailureListener(e -> {
                    if (senderName == null) senderName = "Korisnik";
                });
    }

    private void cacheSenderName(String name) {
        if (currentUser == null) return;
        SharedPreferences prefs = requireActivity()
                .getSharedPreferences("chat_prefs", 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            prefs.edit().putString("senderName_" + currentUser.getUid(), name).apply();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        chatTitle = view.findViewById(R.id.chatTitle);
        messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView);
        chatInput = view.findViewById(R.id.chatInput);
        chatSendButton = view.findViewById(R.id.chatSendButton);
        chatBackButton = view.findViewById(R.id.chatBackButton);
        chatTitle.setText(regionName + " \u010det");

        messageList = new ArrayList<>();
        adapter = new MessageAdapter(messageList);
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        messagesRecyclerView.setAdapter(adapter);

        chatSendButton.setOnClickListener(v -> sendMessage());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
            chatInput.setOnEditorActionListener((v, actionId, event) -> {
                sendMessage();
                return true;
            });
        }

        chatBackButton.setOnClickListener(v -> closeChat());

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(0, 0, 0, Math.round(ime.bottom * 0.5f));
            return insets;
        });

        listenToMessages();
    }

    private void sendMessage() {
        if (currentUser == null) return;

        String text = chatInput.getText().toString().trim();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            if (text.isEmpty()) return;
        }

        String name = senderName != null ? senderName : "Korisnik";
        chatRepository.sendMessage(regionCode, currentUser.getUid(), name, text);
        chatInput.setText("");
    }

    private void listenToMessages() {
        Query query = chatRepository.getMessagesQuery(regionCode);

        messagesListener = query.addSnapshotListener((snapshots, error) -> {
            if (error != null || snapshots == null) return;

            messageList.clear();
            for (QueryDocumentSnapshot doc : snapshots) {
                String senderId = doc.getString("senderId");
                String senderName = doc.getString("senderName");
                String text = doc.getString("text");
                Timestamp timestamp = doc.getTimestamp("timestamp");

                if (senderId != null && senderName != null && text != null) {
                    boolean isMine = currentUser != null && currentUser.getUid().equals(senderId);
                    messageList.add(new MessageEntry(senderId, senderName, text, timestamp, isMine));
                }
            }

            adapter.notifyDataSetChanged();

            if (!messageList.isEmpty()) {
                messagesRecyclerView.smoothScrollToPosition(messageList.size() - 1);
            }
        });
    }

    private void closeChat() {
        if (getParentFragment() instanceof RegionsFragment) {
            ((RegionsFragment) getParentFragment()).closeChat();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (messagesListener != null) {
            messagesListener.remove();
        }
    }

    private static class MessageEntry {
        final String senderId;
        final String senderName;
        final String text;
        final Timestamp timestamp;
        final boolean isMine;

        MessageEntry(String senderId, String senderName, String text, Timestamp timestamp, boolean isMine) {
            this.senderId = senderId;
            this.senderName = senderName;
            this.text = text;
            this.timestamp = timestamp;
            this.isMine = isMine;
        }
    }

    private static class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

        private final List<MessageEntry> items;
        private static final int VIEW_TYPE_MINE = 0;
        private static final int VIEW_TYPE_OTHER = 1;

        MessageAdapter(List<MessageEntry> items) {
            this.items = items;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).isMine ? VIEW_TYPE_MINE : VIEW_TYPE_OTHER;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_message, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MessageEntry entry = items.get(position);

            holder.senderText.setText(entry.senderName);
            holder.messageText.setText(entry.text);

            if (entry.timestamp != null) {
                Date date = entry.timestamp.toDate();
                SimpleDateFormat sdf;
                long diff = System.currentTimeMillis() - date.getTime();
                if (diff < 86400000) {
                    sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                } else {
                    sdf = new SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault());
                }
                holder.timeText.setText(sdf.format(date));
            } else {
                holder.timeText.setText("\u0160aljem...");
            }

            ViewGroup.LayoutParams lp = holder.wrapper.getLayoutParams();
            if (lp instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) lp;
                if (entry.isMine) {
                    params.gravity = android.view.Gravity.END;
                } else {
                    params.gravity = android.view.Gravity.START;
                }
                holder.wrapper.setLayoutParams(params);
            }

            if (entry.isMine) {
                holder.wrapper.setBackgroundResource(R.drawable.bg_chat_bubble_me);
            } else {
                holder.wrapper.setBackgroundResource(R.drawable.bg_chat_bubble_other);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final LinearLayout wrapper;
            final TextView senderText;
            final TextView messageText;
            final TextView timeText;

            ViewHolder(View itemView) {
                super(itemView);
                wrapper = itemView.findViewById(R.id.messageWrapper);
                senderText = itemView.findViewById(R.id.messageSender);
                messageText = itemView.findViewById(R.id.messageText);
                timeText = itemView.findViewById(R.id.messageTime);
            }
        }
    }
}
