package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button startMatchButton = view.findViewById(R.id.buttonStartMatch);
        startMatchButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), MatchPlayActivity.class);
            intent.putExtra(MatchConstants.EXTRA_PLAYER_ONE_NAME, MatchConstants.DEFAULT_PLAYER_ONE_NAME);
            intent.putExtra(MatchConstants.EXTRA_PLAYER_TWO_NAME, MatchConstants.DEFAULT_PLAYER_TWO_NAME);
            startActivity(intent);
        });

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
}
