package com.audiosearch.commands;

public class AppState {
    private String currentStoreFile = "embedding-store.json";
    private double relevanceThreshold = 0.5;
    private int topN = 7;

    public String getCurrentStoreFile() {
        return currentStoreFile;
    }

    public void setCurrentStoreFile(String storeFile) {
        this.currentStoreFile = storeFile;
    }

    public double getRelevanceThreshold() {
        return relevanceThreshold;
    }

    public void setRelevanceThreshold(double threshold) {
        this.relevanceThreshold = threshold;
    }

    public int getTopN() {
        return topN;
    }

    public void setTopN(int topN) {
        this.topN = topN;
    }
}
