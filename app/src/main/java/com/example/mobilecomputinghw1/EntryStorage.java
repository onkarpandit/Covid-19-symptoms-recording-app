package com.example.mobilecomputinghw1;

import java.util.Date;
import java.util.concurrent.CopyOnWriteArrayList;

class EntryStorage {
    private final CopyOnWriteArrayList<DataPoint<Integer>> measurements = new CopyOnWriteArrayList<>();
    private int minimum = Integer.MAX_VALUE;
    private int maximum = Integer.MIN_VALUE;
    private final int rollingSize = 4;

    void add(int measurement) {
        DataPoint<Integer> measurementWithDate = new DataPoint<>(new Date(), measurement);

        measurements.add(measurementWithDate);
        if (measurement < minimum) minimum = measurement;
        if (measurement > maximum) maximum = measurement;
    }

    CopyOnWriteArrayList<DataPoint<Float>> getStdValues() {
        CopyOnWriteArrayList<DataPoint<Float>> stdValues = new CopyOnWriteArrayList<>();

        for (int i = 0; i < measurements.size(); i++) {
            int sum = 0;
            for (int rollingAverageCounter = 0; rollingAverageCounter < rollingSize; rollingAverageCounter++) {
                sum += measurements.get(Math.max(0, i - rollingAverageCounter)).measurement;
            }

            DataPoint<Float> stdValue =
                    new DataPoint<>(
                            measurements.get(i).timestamp,
                            ((float)sum / rollingSize - minimum ) / (maximum - minimum));
            stdValues.add(stdValue);
        }

        return stdValues;
    }

    CopyOnWriteArrayList<DataPoint<Integer>> getLastStdValues(int count) {
        if (count < measurements.size()) {
            return  new CopyOnWriteArrayList<>(measurements.subList(measurements.size() - 1 - count, measurements.size() - 1));
        } else {
            return measurements;
        }
    }

    Date getLastTimestamp() {
        return measurements.get(measurements.size() - 1).timestamp;
    }
}
