package com.audiosearch.commands;

import com.audiosearch.EmbeddingIndexer;
import com.audiosearch.SemanticSearcher;
import com.audiosearch.WhisperTranscriber;
import com.audiosearch.model.SearchResult;
import com.audiosearch.model.TranscriptionSegment;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class CommandHandler {
    private final AppState appState;

    public CommandHandler(AppState appState) {
        this.appState = appState;
    }

    public void handleIndexCommand(String apiKey, Scanner scanner, String argument) {
        if (argument.isEmpty()) {
            System.out.println("Current embedding-store: " + appState.getCurrentStoreFile());
        } else {
            appState.setCurrentStoreFile(argument);
            System.out.println("Embedding store set to: " + appState.getCurrentStoreFile());
        }
    }

    public void handleFileCommand(String apiKey, Scanner scanner) throws IOException {
        String resourcesDir = "src/main/resources";
        File resourceFolder = new File(resourcesDir);

        if (!resourceFolder.exists()) {
            throw new IOException("Resources folder not found: " + resourcesDir);
        }

        File[] files = resourceFolder.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("No files found in " + resourcesDir);
            return;
        }

        System.out.println("Available audio files in " + resourcesDir + ":");
        java.util.List<File> fileList = new java.util.ArrayList<>();
        int fileIndex = 1;
        for (File f : files) {
            if (f.isFile()) {
                fileList.add(f);
                System.out.println("  " + fileIndex + ". " + f.getName());
                fileIndex++;
            }
        }

        System.out.print("Enter file name or number (1-" + fileList.size() + "): ");
        String input = scanner.nextLine().trim();

        File audio;
        try {
            int number = Integer.parseInt(input);
            if (number < 1 || number > fileList.size()) {
                throw new IOException("Invalid file number. Please enter a number between 1 and " + fileList.size());
            }
            audio = fileList.get(number - 1);
        } catch (NumberFormatException e) {
            audio = new File(resourceFolder, input);
        }

        if (!audio.exists() || !audio.isFile()) {
            throw new IOException("Audio file not found in resources: " + (audio.getName().isEmpty() ? input : audio.getName()));
        }

        if (!audio.getCanonicalPath().startsWith(resourceFolder.getCanonicalPath())) {
            throw new IOException("File must be in " + resourcesDir + " folder");
        }

        System.out.println("Transcribing audio file: " + audio.getName());
        System.out.println("(This may take a while...)");

        WhisperTranscriber transcriber = new WhisperTranscriber(apiKey);
        System.out.println("Starting transcription...");
        List<TranscriptionSegment> segments = transcriber.transcribe(audio);

        System.out.println("Received " + segments.size() + " segments from Whisper");
        System.out.println("Creating embeddings...");

        EmbeddingIndexer indexer;
        File storeFile = new File(appState.getCurrentStoreFile());
        if (storeFile.exists()) {
            indexer = EmbeddingIndexer.fromFile(apiKey, appState.getCurrentStoreFile());
        } else {
            indexer = new EmbeddingIndexer(apiKey);
        }
        indexer.indexSegments(segments, audio.getName());
        System.out.println("Saving to store...");
        boolean shouldMerge = storeFile.exists();
        indexer.saveToFile(appState.getCurrentStoreFile(), shouldMerge);
        System.out.println("File indexed and saved to: " + appState.getCurrentStoreFile());
    }

    public void handleThresholdCommand(String argument) {
        if (argument.isEmpty()) {
            System.out.println("Current relevance threshold: " + String.format("%.4f", appState.getRelevanceThreshold()));
            return;
        }

        try {
            double threshold = Double.parseDouble(argument);
            if (threshold < 0.0 || threshold > 1.0) {
                System.err.println("Threshold must be between 0.0 and 1.0");
                return;
            }
            appState.setRelevanceThreshold(threshold);
            System.out.println("Relevance threshold set to: " + String.format("%.4f", appState.getRelevanceThreshold()));
        } catch (NumberFormatException e) {
            System.err.println("Invalid threshold value. Must be a number between 0.0 and 1.0");
        }
    }

    public void handleStatusCommand() {
        System.out.println("\nAudioSearch Status:");
        System.out.println("─".repeat(80));

        File storeFile = new File(appState.getCurrentStoreFile());
        try {
            System.out.println("Embedding Store: " + storeFile.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("Embedding Store: " + appState.getCurrentStoreFile());
        }
        System.out.println("Relevance Threshold: " + String.format("%.4f", appState.getRelevanceThreshold()));
        if (!storeFile.exists()) {
            System.out.println("No files loaded");
        } else {
            try {
                String content = Files.readString(storeFile.toPath(), StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                JsonArray metadata = root.getAsJsonArray("metadata");

                if (metadata == null || metadata.isEmpty()) {
                    System.out.println("No files loaded");
                } else {
                    Set<String> sourceFiles = new LinkedHashSet<>();
                    for (int i = 0; i < metadata.size(); i++) {
                        JsonObject meta = metadata.get(i).getAsJsonObject();
                        sourceFiles.add(meta.get("sourceFile").getAsString());
                    }
                    System.out.println("Loaded Files: " + sourceFiles.size());
                    for (String file : sourceFiles) {
                        System.out.println("  - " + file);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error reading embedding store: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Error parsing store: " + e.getMessage());
            }
        }
        System.out.println("─".repeat(80));
        System.out.println();
    }

    public void handleSearchCommand(String apiKey, Scanner scanner, String argument) {
        try {
            File storeFile = new File(appState.getCurrentStoreFile());
            if (!storeFile.exists()) {
                System.out.println("No embedding store found at: " + appState.getCurrentStoreFile());
                return;
            }

            String query;
            if (argument.isEmpty()) {
                System.out.print("Enter search query: ");
                query = scanner.nextLine().trim();
            } else {
                query = argument;
            }

            if (query.isEmpty()) {
                System.out.println("Search query cannot be empty");
                return;
            }

            System.out.println("Searching...");
            SemanticSearcher searcher = new SemanticSearcher(apiKey, appState.getCurrentStoreFile());
            List<SearchResult> allResults = searcher.search(query, 5);

            List<SearchResult> filteredResults = new ArrayList<>();
            for (SearchResult result : allResults) {
                if (result.score() >= appState.getRelevanceThreshold()) {
                    filteredResults.add(result);
                }
            }

            if (filteredResults.isEmpty()) {
                System.out.println("No results found above threshold (" + String.format("%.4f", appState.getRelevanceThreshold()) + ")");
                return;
            }

            System.out.println("\nTop " + filteredResults.size() + " results (threshold: " + String.format("%.4f", appState.getRelevanceThreshold()) + "):\n");
            System.out.println("─".repeat(100));

            for (int i = 0; i < filteredResults.size(); i++) {
                SearchResult result = filteredResults.get(i);
                String startTime = secondsToTimestamp(result.startSeconds());
                String endTime = secondsToTimestamp(result.endSeconds());
                System.out.printf("%d. [%s - %s] (score: %.4f) - %s\n", i + 1, startTime, endTime, result.score(), result.sourceFile());
                System.out.println("   " + result.text());
                System.out.println();
            }

            System.out.println("─".repeat(100));
        } catch (IOException e) {
            System.err.println("Error searching embedding store: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error during search: " + e.getMessage());
        }
    }

    public void handleTimestampsCommand() {
        try {
            File storeFile = new File(appState.getCurrentStoreFile());
            if (!storeFile.exists()) {
                System.out.println("No embedding store found at: " + appState.getCurrentStoreFile());
                return;
            }

            String content = Files.readString(storeFile.toPath(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            JsonArray metadata = root.getAsJsonArray("metadata");

            if (metadata == null || metadata.isEmpty()) {
                System.out.println("No chunks in embedding store");
                return;
            }

            System.out.println("\nTimestamps for chunks in " + appState.getCurrentStoreFile() + ":");
            System.out.println("─".repeat(80));

            for (int i = 0; i < metadata.size(); i++) {
                JsonObject meta = metadata.get(i).getAsJsonObject();
                double start = meta.get("start").getAsDouble();
                double end = meta.get("end").getAsDouble();
                String sourceFile = meta.get("sourceFile").getAsString();
                String text = meta.get("text").getAsString();

                String displayText = text;
                if (text.trim().length() == 0) {
                    displayText = "[silence]";
                } else if (text.trim().length() <= 2 && !text.matches(".*[a-zA-Z0-9].*")) {
                    displayText = "[instrumental/music note]";
                } else {
                    int maxDisplay = 100;
                    displayText = text.length() > maxDisplay ? text.substring(0, maxDisplay) + "..." : text;
                }
                System.out.printf("%3d. [%s - %s] %-20s %s%n", i + 1, secondsToTimestamp(start), secondsToTimestamp(end), sourceFile, displayText);
            }

            System.out.println("─".repeat(80));
            System.out.println("Total chunks: " + metadata.size());
            System.out.println();
        } catch (IOException e) {
            System.err.println("Error reading embedding store: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error parsing timestamps: " + e.getMessage());
        }
    }

    private String secondsToTimestamp(double seconds) {
        int hours = (int) (seconds / 3600);
        int minutes = (int) ((seconds % 3600) / 60);
        int secs = (int) (seconds % 60);
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    public void handleDeduplicateCommand() {
        try {
            StoreDeduplicator.deduplicateStore(appState.getCurrentStoreFile());
        } catch (IOException e) {
            System.err.println("Error deduplicating store: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error during deduplication: " + e.getMessage());
        }
    }

    public void printHelp() {
        System.out.println("\nAvailable Commands:");
        System.out.println("  /index         - Specify the embedding-store file path");
        System.out.println("  /file          - Ingest an audio file into the embedding-store");
        System.out.println("  /search        - Search the embedding-store for semantically similar text");
        System.out.println("  /threshold     - Set the minimum relevance threshold (0.0-1.0) for search results");
        System.out.println("  /timestamps    - Display timestamps for all chunks in the embedding-store");
        System.out.println("  /status        - Display loaded files and current threshold value");
        System.out.println("  /deduplicate   - Remove duplicate entries from the embedding-store");
        System.out.println("  /help          - Display this help message");
        System.out.println("  /exit          - Exit the application");
        System.out.println("  /quit          - Exit the application");
        System.out.println();
    }
}