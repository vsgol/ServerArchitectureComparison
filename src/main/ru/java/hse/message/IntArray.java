package ru.java.hse.message;

public record IntArray(int id, int[] data) {
    public static IntArray sort(IntArray source) {
        int[] data = source.data();
        for (int j = 0; j < data.length - 1; j++) {
            for (int i = 0; i < data.length - 1; i++) {
                if (data[i] > data[i + 1]) {
                    int t = data[i + 1];
                    data[i + 1] = data[i];
                    data[i] = t;
                }
            }
        }
        return new IntArray(source.id, data);
    }
}
