package com.example.aerotutorial;


import java.util.List;

public class PredictionEngine {

    public static PredictionResult predictNextDay(List<Integer> series) {
        int n = series.size();
        if (n < 2) return PredictionResult.invalid("Not enough data");

        double sumX = 0, sumY = 0, sumXX = 0, sumXY = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = series.get(i);
            sumX += x;
            sumY += y;
            sumXX += x * x;
            sumXY += x * y;
        }

        double denom = n * sumXX - sumX * sumX;
        if (Math.abs(denom) < 1e-8) {
            double avg = series.stream().mapToInt(Integer::intValue).average().orElse(Double.NaN);
            return PredictionResult.fromAvg(avg, n);
        }

        double slope = (n * sumXY - sumX * sumY) / denom;
        double intercept = (sumY - slope * sumX) / n;

        double pred = intercept + slope * n; // next day
        pred = Math.max(0, Math.min(500, pred)); // clamp 0-500

        return new PredictionResult(pred, slope, intercept);
    }

    // PredictionResult class
    public static class PredictionResult {
        public final double predicted;
        public final double slope;
        public final double intercept;

        public PredictionResult(double predicted, double slope, double intercept) {
            this.predicted = predicted;
            this.slope = slope;
            this.intercept = intercept;
        }

        public static PredictionResult invalid(String msg) {
            return new PredictionResult(Double.NaN, 0, 0);
        }

        public static PredictionResult fromAvg(double avg, int pointsUsed) {
            return new PredictionResult(avg, 0, 0);
        }
    }

}
