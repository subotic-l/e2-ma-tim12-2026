package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.service.UserService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomeFragment extends Fragment {

    private UserService userService;
    private TextView textTokenCount;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userService = new UserService();
        textTokenCount = view.findViewById(R.id.textTokenCount);
        loadTokenCount();

        Button startMatchButton = view.findViewById(R.id.buttonStartMatch);
        startMatchButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), MatchPlayActivity.class);
            intent.putExtra(MatchConstants.EXTRA_PLAYER_ONE_NAME, MatchConstants.DEFAULT_PLAYER_ONE_NAME);
            intent.putExtra(MatchConstants.EXTRA_PLAYER_TWO_NAME, MatchConstants.DEFAULT_PLAYER_TWO_NAME);
            startActivity(intent);
        });

        Button onlineMatchButton = view.findViewById(R.id.buttonOnlineMatch);
        onlineMatchButton.setOnClickListener(v -> {
            userService.hasEnoughTokens().addOnSuccessListener(enough -> {
                if (!enough) {
                    Toast.makeText(requireContext(),
                            "Nemate dovoljno tokena za online partiju.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                showNameDialog();
            }).addOnFailureListener(e -> {
                Toast.makeText(requireContext(),
                        "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        });

        Button tournamentButton = view.findViewById(R.id.buttonTournament);
        tournamentButton.setOnClickListener(v -> showNameDialog(TournamentLobbyActivity.class));

        view.findViewById(R.id.buttonStartWhoKnows).setOnClickListener(v ->
                startActivity(new Intent(requireActivity(), WhoKnowsKnows.class)));

        view.findViewById(R.id.buttonStartMatchingGame).setOnClickListener(v ->
                startActivity(new Intent(requireActivity(), MatchingGameActivity.class)));

        view.findViewById(R.id.buttonStartStepByStep).setOnClickListener(v ->
                startActivity(new Intent(requireActivity(), StepByStepActivity.class)));

        view.findViewById(R.id.buttonStartNumbersGame).setOnClickListener(v ->
                startActivity(new Intent(requireActivity(), NumbersGameActivity.class)));

        view.findViewById(R.id.buttonStartSkocko).setOnClickListener(v ->
                startActivity(new Intent(requireActivity(), SkockoGameActivity.class)));

        view.findViewById(R.id.buttonStartAsocijacije).setOnClickListener(v ->
                startActivity(new Intent(requireActivity(), AsocijacijeGameActivity.class)));
    }

    private void loadTokenCount() {
        userService.loadProfile().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                Long tokens = doc.getLong("tokens");
                textTokenCount.setText(String.valueOf(tokens != null ? tokens : 0));
            }
        }).addOnFailureListener(e -> {
            textTokenCount.setText("?");
        });
    }

    private void showNameDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireActivity());
        builder.setTitle("Ime igrača");
        builder.setMessage("Unesite ime koje će protivnik videti:");
        final android.widget.EditText input = new android.widget.EditText(requireActivity());
        input.setHint("Ime");
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        final String[] avatarHolder = {""};
        if (user != null) {
            FirebaseFirestore.getInstance()
                    .collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists() && doc.getString("username") != null) {
                            input.setText(doc.getString("username"));
                        }
                        if (doc.exists() && doc.getString("avatarUrl") != null) {
                            avatarHolder[0] = doc.getString("avatarUrl");
                        }
                    });
        } else {
            input.setText("Igrač");
        }
        builder.setView(input);
        builder.setPositiveButton("Potvrdi", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = "Igrač";
            Intent intent = new Intent(requireActivity(), MatchLobbyActivity.class);
            intent.putExtra("playerName", name);
            if (user != null) {
                intent.putExtra("playerId", user.getUid());
                intent.putExtra("avatarUrl", avatarHolder[0]);
            }
            startActivity(intent);
        });
        builder.setNegativeButton("Otkaži", null);
        builder.show();
    }
}
