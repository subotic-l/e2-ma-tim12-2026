package com.example.slagalica;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NumbersGame {
    public final int targetNumber;       // 1..999
    public final List<Integer> numbers;  // 6 numbers

    public NumbersGame(int targetNumber, List<Integer> numbers) {
        this.targetNumber = targetNumber;
        this.numbers = numbers;
    }

    public static NumbersGame createRandom() {
        Random random = new Random();
        int target = random.nextInt(999) + 1;

        List<Integer> nums = new ArrayList<>();
        // 4 single-digit numbers
        for (int i = 0; i < 4; i++) {
            nums.add(random.nextInt(9) + 1);
        }
        // one from 10, 15, 20
        int[] medium = {10, 15, 20};
        nums.add(medium[random.nextInt(medium.length)]);
        // one from 25, 50, 75, 100
        int[] large = {25, 50, 75, 100};
        nums.add(large[random.nextInt(large.length)]);

        return new NumbersGame(target, nums);
    }
}