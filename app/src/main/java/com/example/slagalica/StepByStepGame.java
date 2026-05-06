package com.example.slagalica;

import java.util.List;

public class StepByStepGame {
    public final List<String> clues;   // 7 clues, ordered hardest -> easiest
    public final String answer;

    public StepByStepGame(List<String> clues, String answer) {
        this.clues = clues;
        this.answer = answer;
    }

    public int maxSteps() {
        return clues.size();
    }
}