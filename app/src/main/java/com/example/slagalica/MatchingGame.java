package com.example.slagalica;

import java.util.List;

public class MatchingGame {
    public String instructions;
    public List<String> leftItems;
    public List<String> rightItems;
    public List<Integer> correctMatches;

    public MatchingGame(String instructions, List<String> leftItems, List<String> rightItems, List<Integer> correctMatches) {
        this.instructions = instructions;
        this.leftItems = leftItems;
        this.rightItems = rightItems;
        this.correctMatches = correctMatches;
    }

    public boolean isCorrectMatch(int leftIndex, int rightIndex) {
        return correctMatches.get(leftIndex) == rightIndex;
    }
}
