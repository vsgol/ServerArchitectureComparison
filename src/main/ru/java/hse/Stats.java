package ru.java.hse;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Stats {
    private final Lock lock = new ReentrantLock();
    private boolean working = true;
    private Long sum = 0L;
    private Long count = 0L;

    public void reset() {
        lock.lock();
        try {
            sum = 0L;
            count = 0L;
            working = true;
        } finally {
            lock.unlock();
        }
    }

    public void addMeasurement(long time) {
        lock.lock();
        try {
            if (!working) {
                return;
            }
            sum += time;
            count++;
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        lock.lock();
        try {
            working = false;
        } finally {
            lock.unlock();
        }
    }

    public Long getAverage() {
        lock.lock();
        try {
            if (count == 0) {
                return 0L;
            }
            return sum / count;
        } finally {
            lock.unlock();
        }
    }
}
