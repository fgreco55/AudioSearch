package com.audiosearch;

import com.audiosearch.model.TranscriptionSegment;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonParser;

public class EmbeddingIndexer {
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final List<SegmentMetadata> segmentMetadata;
    private String existingStoreJson;

    public record SegmentMetadata(String text, double start, double end, String sourceFile) {}

    public EmbeddingIndexer(String apiKey) {
        this(apiKey, new InMemoryEmbeddingStore<>(), new ArrayList<>());
    }

    private EmbeddingIndexer(String apiKey, EmbeddingStore<TextSegment> store, List<SegmentMetadata> metadata) {
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName("text-embedding-3-small")
                .build();
        this.embeddingStore = store;
        this.segmentMetadata = metadata;
    }

    public static EmbeddingIndexer fromFile(String apiKey, String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            return new EmbeddingIndexer(apiKey);
        }

        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(content).getAsJsonObject();

        List<SegmentMetadata> metadata = new ArrayList<>();

        JsonArray metadataArray = root.getAsJsonArray("metadata");
        if (metadataArray != null) {
            for (JsonElement element : metadataArray) {
                JsonObject obj = element.getAsJsonObject();
                metadata.add(new SegmentMetadata(
                        obj.get("text").getAsString(),
                        obj.get("start").getAsDouble(),
                        obj.get("end").getAsDouble(),
                        obj.get("sourceFile").getAsString()
                ));
            }
        }

        EmbeddingIndexer indexer = new EmbeddingIndexer(apiKey, new InMemoryEmbeddingStore<>(), metadata);
        indexer.existingStoreJson = root.get("store").getAsString();
        return indexer;
    }

    public void indexSegments(List<TranscriptionSegment> segments, String sourceFileName) {
        System.out.println("Embedding " + segments.size() + " segments...");

        int skipped = 0;
        for (TranscriptionSegment segment : segments) {
            String text = segment.text();

            if (!hasSignificantSpeech(text)) {
                skipped++;
                continue;
            }

            TextSegment textSegment = TextSegment.from(text);
            Embedding embedding = embeddingModel.embed(text).content();
            embeddingStore.add(embedding, textSegment);
            segmentMetadata.add(new SegmentMetadata(text, segment.start(), segment.end(), sourceFileName));
        }

        System.out.println("Embedding complete. " + (segments.size() - skipped) + " segments indexed.");
        if (skipped > 0) {
            System.out.println("Skipped " + skipped + " segments with no speech.");
        }
    }

    private boolean hasSignificantSpeech(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String trimmed = text.trim();

        if (trimmed.length() <= 2 && !trimmed.matches(".*[a-zA-Z0-9].*")) {
            return false;
        }

        return true;
    }

    public void saveToFile(String filePath) throws IOException {
        saveToFile(filePath, false);
    }

    public void saveToFile(String filePath, boolean append) throws IOException {
        File file = new File(filePath);
        JsonArray metadataArray = new JsonArray();
        String storeJson = ((InMemoryEmbeddingStore<TextSegment>) embeddingStore).serializeToJson();

        if (append && existingStoreJson != null) {
            storeJson = mergeStoreJsons(existingStoreJson, storeJson);
        }

        for (SegmentMetadata metadata : segmentMetadata) {
            JsonObject metaObj = new JsonObject();
            metaObj.addProperty("text", metadata.text());
            metaObj.addProperty("start", metadata.start());
            metaObj.addProperty("end", metadata.end());
            metaObj.addProperty("sourceFile", metadata.sourceFile());
            metadataArray.add(metaObj);
        }

        JsonObject root = new JsonObject();
        root.addProperty("store", storeJson);
        root.add("metadata", metadataArray);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(root);
        Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
        System.out.println("Store saved to: " + filePath);
    }

    private String mergeStoreJsons(String existingStoreJson, String newStoreJson) {
        JsonObject existingStore = JsonParser.parseString(existingStoreJson).getAsJsonObject();
        JsonObject newStore = JsonParser.parseString(newStoreJson).getAsJsonObject();

        JsonArray existingEntries = existingStore.getAsJsonArray("entries");
        JsonArray newEntries = newStore.getAsJsonArray("entries");

        if (existingEntries != null && newEntries != null) {
            for (JsonElement entry : newEntries) {
                existingEntries.add(entry);
            }
        }

        return existingStore.toString();
    }

    public EmbeddingStore<TextSegment> getStore() {
        return embeddingStore;
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    public List<SegmentMetadata> getSegmentMetadata() {
        return segmentMetadata;
    }
}
