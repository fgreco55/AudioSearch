package com.audiosearch.spi;

public class AudioSplitterFactory {
    private static AudioSplitter instance;

    public static AudioSplitter create() {
        if (instance != null) {
            return instance;
        }

        String choice = System.getProperty("audiosearch.splitter",
                        System.getenv().getOrDefault("AUDIOSEARCH_SPLITTER", "auto"));

        instance = switch (choice.toLowerCase()) {
            case "ffmpeg" -> {
                System.out.println("Using ffmpeg audio splitter");
                yield new FfmpegAudioSplitter();
            }
            case "java" -> {
                System.out.println("Using pure Java audio splitter");
                yield new Mp3FrameAudioSplitter();
            }
            default -> {
                if (isFfmpegAvailable()) {
                    System.out.println("ffmpeg detected. Using ffmpeg audio splitter");
                    yield new FfmpegAudioSplitter();
                } else {
                    System.out.println("ffmpeg not found. Using pure Java audio splitter");
                    yield new Mp3FrameAudioSplitter();
                }
            }
        };

        return instance;
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
}
