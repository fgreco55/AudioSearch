package com.audiosearch.spi;

import com.audiosearch.service.WhisperTranscriber;
import com.audiosearch.model.TranscriptionSegment;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface AudioSplitter {
    static final long MAX_FILE_SIZE = 25 * 1024 * 1024; // 25 MB limit

    List<TranscriptionSegment> split(File audioFile, WhisperTranscriber transcriber) throws IOException;

    static boolean needsSplitting(File audioFile) {
        return audioFile.length() > MAX_FILE_SIZE;
    }
}
