package org.algoyog.algos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GCPerformanceTest {

    private static final int NUM_OBJECTS = 10_000_00;
    private static final int OBJECT_SIZE = 1024; // bytes

    public static void main(String[] args) {
        List<byte[]> memoryHog = new ArrayList<>();
        Random random = new Random();

        long start = System.nanoTime();

        for (int i = 0; i < NUM_OBJECTS; i++) {
            memoryHog.add(new byte[OBJECT_SIZE]);
            if (memoryHog.size() > 100_000) {
                memoryHog.remove(random.nextInt(memoryHog.size()));
            }
        }

        long end = System.nanoTime();
        System.out.printf("Execution Time: %.2f seconds%n", (end - start) / 1_000_000_000.0);
    }
}
