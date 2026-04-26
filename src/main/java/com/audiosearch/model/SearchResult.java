package com.audiosearch.model;

public record SearchResult(
    String text,
    double startSeconds,
    double endSeconds,
    double score,
    String sourceFile
) {
    @Override
    public String toString() {
        return String.format(
            "[%.3f - %.3f] (score: %.4f) - %s\n  File: %s",
            startSeconds, endSeconds, score, text, sourceFile
        );
    }
}
