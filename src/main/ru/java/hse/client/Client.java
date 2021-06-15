package ru.java.hse.client;

import ru.java.hse.Utils;
import ru.java.hse.message.IntArray;

import java.io.IOException;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

public class Client implements Callable<Void> {
    private final int port;
    private final int arraySize;
    private final int timeDelta;
    private final int countRequests;

    public Client(int port, int arraySize, int timeDelta, int countRequests) {
        this.port = port;
        this.arraySize = arraySize;
        this.timeDelta = timeDelta;
        this.countRequests = countRequests;
    }

    @Override
    public Void call() {
        int[][] data = new int[countRequests][];
        try (Socket socket = new Socket("localhost", port)) {
            Thread requestsThread = new Thread(() -> {
                try {
                    for (int currentId = 0; currentId < countRequests; currentId++) {
                        data[currentId] = generateArray();
                        IntArray array = new IntArray(currentId, data[currentId]);
                        long startMillis = System.currentTimeMillis();
                        Utils.writeArray(socket.getOutputStream(), array);
                        long endMillis = System.currentTimeMillis();

                        Thread.sleep(Math.max(timeDelta - endMillis + startMillis, 0));
                    }
                } catch (IOException | InterruptedException ignored) {
                }
            });
            requestsThread.start();
            for (int i = 0; i < countRequests; i++) {
                IntArray result = Utils.readArray(socket.getInputStream());
                if (!checkData(result.data())) {
                    throw new IllegalStateException("Массив должен быть отсортирован");
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private int[] generateArray() {
        Random r = new Random();
        return IntStream.generate(r::nextInt).limit(arraySize).toArray();
    }

    private boolean checkData(int[] sortedData) {
        boolean isOk = true;
        for (int i = 1; i < sortedData.length; i++) {
            if (sortedData[i - 1] > sortedData[i]) {
                isOk = false;
                break;
            }
        }
        return isOk;
    }
}
