package com.audiosearch.model;

public record TranscriptionSegment(
    int id,
    double start,
    double end,
    String text
) {}
