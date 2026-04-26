package com.audiosearch.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public class StoreDeduplicator {
    public static void deduplicateStore(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("Store file not found: " + filePath);
            return;
        }

        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(content).getAsJsonObject();

        // Deduplicate metadata
        JsonArray metadata = root.getAsJsonArray("metadata");
        if (metadata == null || metadata.isEmpty()) {
            System.out.println("No metadata found in store");
            return;
        }

        int originalMetadataSize = metadata.size();
        JsonArray deduplicatedMetadata = new JsonArray();
        Set<String> seenMetadataEntries = new HashSet<>();

        for (int i = 0; i < metadata.size(); i++) {
            JsonObject metaObj = metadata.get(i).getAsJsonObject();
            String text = metaObj.get("text").getAsString();
            String sourceFile = metaObj.get("sourceFile").getAsString();
            double start = metaObj.get("start").getAsDouble();
            double end = metaObj.get("end").getAsDouble();

            String key = text + "|" + sourceFile + "|" + start + "|" + end;

            if (!seenMetadataEntries.contains(key)) {
                seenMetadataEntries.add(key);
                deduplicatedMetadata.add(metaObj);
            }
        }

        // Deduplicate embeddings in store
        String storeJson = root.get("store").getAsString();
        JsonObject storeObj = JsonParser.parseString(storeJson).getAsJsonObject();
        JsonArray entries = storeObj.getAsJsonArray("entries");

        int originalEmbeddingSize = entries == null ? 0 : entries.size();
        JsonArray deduplicatedEntries = new JsonArray();
        Set<String> seenEmbeddings = new HashSet<>();

        if (entries != null) {
            for (JsonElement element : entries) {
                JsonObject entry = element.getAsJsonObject();
                String embeddedText = entry.get("embedded").getAsJsonObject().get("text").getAsString();

                // Use embedded text as key (should be unique per segment)
                if (!seenEmbeddings.contains(embeddedText)) {
                    seenEmbeddings.add(embeddedText);
                    deduplicatedEntries.add(entry);
                }
            }
        }

        storeObj.add("entries", deduplicatedEntries);
        root.add("metadata", deduplicatedMetadata);
        root.addProperty("store", storeObj.toString());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(root);
        Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));

        System.out.println("\nStore cleanup completed successfully");
        System.out.println("\nMetadata:");
        System.out.println("  Original entries: " + originalMetadataSize);
        System.out.println("  Deduplicated entries: " + deduplicatedMetadata.size());
        System.out.println("  Removed: " + (originalMetadataSize - deduplicatedMetadata.size()));
        System.out.println("\nEmbeddings:");
        System.out.println("  Original entries: " + originalEmbeddingSize);
        System.out.println("  Deduplicated entries: " + deduplicatedEntries.size());
        System.out.println("  Removed: " + (originalEmbeddingSize - deduplicatedEntries.size()));
    }
}