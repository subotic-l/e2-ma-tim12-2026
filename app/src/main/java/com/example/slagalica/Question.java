package com.example.slagalica;

import java.util.List;

public class Question {
    public String questionText;
    public List<String> answers;
    public int correctAnswerIndex;

    public Question(String questionText, List<String> answers, int correctAnswerIndex) {
        this.questionText = questionText;
        this.answers = answers;
        this.correctAnswerIndex = correctAnswerIndex;
    }
}
