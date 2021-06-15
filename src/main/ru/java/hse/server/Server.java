package ru.java.hse.server;

import ru.java.hse.Stats;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Server {
    private final Map<IntegerPair, Long> startsMeasurement = new ConcurrentHashMap<>();
    private final Stats statistic = new Stats();
    protected int currentClientId = 0;

    private record IntegerPair(int first, int second) {}

    abstract public void start(int port) throws ServerException;

    abstract public void shutdown();

    protected void startMeasure(int id, int clientId) {
        startsMeasurement.put(new IntegerPair(id, clientId), System.currentTimeMillis());
    }

    protected void endMeasure(int id, int clientId) {
        long start = startsMeasurement.get(new IntegerPair(id, clientId));
        statistic.addMeasurement(System.currentTimeMillis() - start);
    }

    public void reset() {
        startsMeasurement.clear();
        statistic.reset();
        currentClientId = 0;
    }

    protected void stopCollectingStatistics() {
        statistic.stop();
    }

    public long getAverageTime() {
        return statistic.getAverage();
    }

}
