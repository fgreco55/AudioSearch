package com.audiosearch;

import com.audiosearch.model.TranscriptionSegment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AudioSplitter {
    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024; // 25 MB limit
    private static final int CHUNK_DURATION_SECONDS = 600; // 10 minutes per chunk

    public static boolean needsSplitting(File audioFile) {
        return audioFile.length() > MAX_FILE_SIZE;
    }

    public static List<TranscriptionSegment> transcribeWithChunking(File audioFile, WhisperTranscriber transcriber) throws IOException {
        if (!needsSplitting(audioFile)) {
            return transcriber.transcribe(audioFile);
        }

        System.out.println("File size (" + formatFileSize(audioFile.length()) + ") exceeds Whisper's 25MB limit.");
        System.out.println("Splitting audio into chunks...");

        if (!isFfmpegAvailable()) {
            throw new IOException(
                "File is too large and ffmpeg is not installed. Please either:\n" +
                "1. Compress the audio file to under 25 MB, or\n" +
                "2. Install ffmpeg to enable automatic chunking: https://ffmpeg.org/download.html"
            );
        }

        List<File> chunks = splitAudioFile(audioFile);
        List<TranscriptionSegment> allSegments = new ArrayList<>();
        double timeOffset = 0;

        for (int i = 0; i < chunks.size(); i++) {
            File chunk = chunks.get(i);
            System.out.println("Transcribing chunk " + (i + 1) + " of " + chunks.size() + "...");

            List<TranscriptionSegment> chunkSegments = transcriber.transcribe(chunk);

            for (TranscriptionSegment segment : chunkSegments) {
                TranscriptionSegment adjustedSegment = new TranscriptionSegment(
                    segment.id(),
                    segment.start() + timeOffset,
                    segment.end() + timeOffset,
                    segment.text()
                );
                allSegments.add(adjustedSegment);
            }

            timeOffset += CHUNK_DURATION_SECONDS;
            chunk.delete();
        }

        System.out.println("All chunks transcribed. Total segments: " + allSegments.size());
        return allSegments;
    }

    private static boolean isFfmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<File> splitAudioFile(File audioFile) throws IOException {
        List<File> chunks = new ArrayList<>();
        String baseName = audioFile.getName().replaceFirst("[.][^.]+$", "");
        File tempDir = new File(audioFile.getParent(), ".audiosearch_temp");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-i", audioFile.getAbsolutePath(),
                "-f", "segment",
                "-segment_time", String.valueOf(CHUNK_DURATION_SECONDS),
                "-c", "copy",
                new File(tempDir, baseName + "_%03d.mp3").getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new IOException("ffmpeg failed to split audio file");
            }

            File[] files = tempDir.listFiles((dir, name) -> name.startsWith(baseName) && name.endsWith(".mp3"));
            if (files != null) {
                for (File file : files) {
                    chunks.add(file);
                }
            }
            Collections.sort(chunks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Audio splitting interrupted", e);
        }

        if (chunks.isEmpty()) {
            throw new IOException("Failed to split audio file");
        }

        return chunks;
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int z = (63 - Long.numberOfLeadingZeros(bytes)) / 10;
        return String.format("%.1f %sB", (double) bytes / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}
