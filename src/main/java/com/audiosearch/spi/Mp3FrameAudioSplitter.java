package com.audiosearch.spi;

import com.audiosearch.WhisperTranscriber;
import com.audiosearch.model.TranscriptionSegment;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Mp3FrameAudioSplitter implements AudioSplitter {
    private static final int CHUNK_DURATION_SECONDS = 600;
    private static final int[] MPEG1_BITRATES = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320};
    private static final int[] MPEG2_BITRATES = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160};
    private static final int[] SAMPLE_RATES_MPEG1 = {44100, 48000, 32000};
    private static final int[] SAMPLE_RATES_MPEG2 = {22050, 24000, 16000};
    private static final int[] SAMPLE_RATES_MPEG25 = {11025, 12000, 8000};

    @Override
    public List<TranscriptionSegment> split(File audioFile, WhisperTranscriber transcriber) throws IOException {
        if (!AudioSplitter.needsSplitting(audioFile)) {
            return transcriber.transcribe(audioFile);
        }

        System.out.println("File size (" + formatFileSize(audioFile.length()) + ") exceeds Whisper's 25MB limit.");
        System.out.println("Splitting audio into chunks using pure Java MP3 parsing...");

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

            timeOffset += getChunkDuration(chunk);
            chunk.delete();
        }

        System.out.println("All chunks transcribed. Total segments: " + allSegments.size());
        return allSegments;
    }

    private List<File> splitAudioFile(File audioFile) throws IOException {
        List<File> chunks = new ArrayList<>();
        String baseName = audioFile.getName().replaceFirst("[.][^.]+$", "");
        File tempDir = new File(audioFile.getParent(), ".audiosearch_temp");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        byte[] fileBytes = readFileBytes(audioFile);
        int frameStartOffset = skipId3v2Tag(fileBytes);

        List<Integer> frameOffsets = new ArrayList<>();
        int offset = frameStartOffset;
        while (offset < fileBytes.length - 4) {
            int header = ((fileBytes[offset] & 0xFF) << 24) |
                        ((fileBytes[offset + 1] & 0xFF) << 16) |
                        ((fileBytes[offset + 2] & 0xFF) << 8) |
                        (fileBytes[offset + 3] & 0xFF);

            if ((header & 0xFFE00000) == 0xFFE00000) {
                frameOffsets.add(offset);
                int frameSize = calculateFrameSize(header);
                if (frameSize > 0) {
                    offset += frameSize;
                } else {
                    offset++;
                }
            } else {
                offset++;
            }
        }

        if (frameOffsets.isEmpty()) {
            throw new IOException("No valid MP3 frames found in file");
        }

        double currentChunkDuration = 0;
        int chunkStartFrame = 0;
        int chunkNumber = 0;

        for (int i = 0; i < frameOffsets.size(); i++) {
            int frameOffset = frameOffsets.get(i);
            int header = ((fileBytes[frameOffset] & 0xFF) << 24) |
                        ((fileBytes[frameOffset + 1] & 0xFF) << 16) |
                        ((fileBytes[frameOffset + 2] & 0xFF) << 8) |
                        (fileBytes[frameOffset + 3] & 0xFF);
            double frameDuration = calculateFrameDuration(header);
            currentChunkDuration += frameDuration;

            boolean isLastFrame = (i == frameOffsets.size() - 1);
            if (currentChunkDuration >= CHUNK_DURATION_SECONDS || isLastFrame) {
                int nextFrameOffset = isLastFrame ? fileBytes.length : frameOffsets.get(i + 1);
                File chunkFile = writeChunk(fileBytes, frameStartOffset, frameOffsets.get(chunkStartFrame),
                                           nextFrameOffset, tempDir, baseName, chunkNumber);
                chunks.add(chunkFile);
                chunkNumber++;
                chunkStartFrame = i + 1;
                currentChunkDuration = 0;
            }
        }

        return chunks;
    }

    private int skipId3v2Tag(byte[] fileBytes) throws IOException {
        if (fileBytes.length >= 10 &&
            fileBytes[0] == 'I' && fileBytes[1] == 'D' && fileBytes[2] == '3') {
            int flags = fileBytes[5];
            int sizeBytes = ((fileBytes[6] & 0x7F) << 21) |
                           ((fileBytes[7] & 0x7F) << 14) |
                           ((fileBytes[8] & 0x7F) << 7) |
                           (fileBytes[9] & 0x7F);
            return 10 + sizeBytes;
        }
        return 0;
    }

    private int calculateFrameSize(int header) {
        int mpegVersion = (header >> 19) & 0x3;
        int bitRateIndex = (header >> 12) & 0xF;
        int sampleRateIndex = (header >> 10) & 0x3;
        int padding = (header >> 9) & 0x1;

        if (bitRateIndex == 0 || bitRateIndex == 15) return -1;
        if (sampleRateIndex == 3) return -1;

        int[] bitRates = (mpegVersion == 3) ? MPEG1_BITRATES : MPEG2_BITRATES;
        int bitRate = bitRates[bitRateIndex] * 1000;
        int sampleRate = getSampleRate(mpegVersion, sampleRateIndex);

        return (144 * bitRate / sampleRate) + padding;
    }

    private double calculateFrameDuration(int header) {
        int mpegVersion = (header >> 19) & 0x3;
        int samplesPerFrame = (mpegVersion == 3) ? 1152 : 576;
        int sampleRateIndex = (header >> 10) & 0x3;
        int sampleRate = getSampleRate(mpegVersion, sampleRateIndex);
        return samplesPerFrame / (double) sampleRate;
    }

    private int getSampleRate(int mpegVersion, int sampleRateIndex) {
        return switch (mpegVersion) {
            case 3 -> SAMPLE_RATES_MPEG1[sampleRateIndex];
            case 2 -> SAMPLE_RATES_MPEG2[sampleRateIndex];
            case 0 -> SAMPLE_RATES_MPEG25[sampleRateIndex];
            default -> 44100;
        };
    }

    private double getChunkDuration(File chunkFile) throws IOException {
        byte[] fileBytes = readFileBytes(chunkFile);
        int frameStartOffset = skipId3v2Tag(fileBytes);
        double duration = 0;
        int offset = frameStartOffset;

        while (offset < fileBytes.length - 4) {
            int header = ((fileBytes[offset] & 0xFF) << 24) |
                        ((fileBytes[offset + 1] & 0xFF) << 16) |
                        ((fileBytes[offset + 2] & 0xFF) << 8) |
                        (fileBytes[offset + 3] & 0xFF);

            if ((header & 0xFFE00000) == 0xFFE00000) {
                duration += calculateFrameDuration(header);
                int frameSize = calculateFrameSize(header);
                if (frameSize > 0) {
                    offset += frameSize;
                } else {
                    offset++;
                }
            } else {
                offset++;
            }
        }

        return duration;
    }

    private File writeChunk(byte[] fileBytes, int id3End, int chunkStart, int chunkEnd,
                          File tempDir, String baseName, int chunkNumber) throws IOException {
        File chunkFile = new File(tempDir, String.format("%s_%03d.mp3", baseName, chunkNumber));

        try (FileOutputStream fos = new FileOutputStream(chunkFile)) {
            if (id3End > 0) {
                fos.write(fileBytes, 0, id3End);
            }
            fos.write(fileBytes, chunkStart, chunkEnd - chunkStart);
        }

        return chunkFile;
    }

    private byte[] readFileBytes(File file) throws IOException {
        byte[] fileBytes = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            int bytesRead = bis.read(fileBytes);
            if (bytesRead != fileBytes.length) {
                throw new IOException("Could not read entire file");
            }
        }
        return fileBytes;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int z = (63 - Long.numberOfLeadingZeros(bytes)) / 10;
        return String.format("%.1f %sB", (double) bytes / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}
