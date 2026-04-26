package com.audiosearch;

import com.audiosearch.model.SearchResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class SemanticSearcher {
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final List<SegmentMetadataRecord> segmentMetadata;

    public record SegmentMetadataRecord(String text, double start, double end, String sourceFile) {}

    public SemanticSearcher(String apiKey, String storePath) throws IOException {
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName("text-embedding-3-small")
                .build();
        this.segmentMetadata = new ArrayList<>();
        this.embeddingStore = loadStore(storePath);
    }

    private EmbeddingStore<TextSegment> loadStore(String storePath) throws IOException {
        File file = new File(storePath);
        if (!file.exists()) {
            throw new IOException("Embedding store not found: " + storePath);
        }

        String jsonString = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();

        // Load metadata
        JsonArray metadataArray = root.getAsJsonArray("metadata");
        for (JsonElement element : metadataArray) {
            JsonObject obj = element.getAsJsonObject();
            segmentMetadata.add(new SegmentMetadataRecord(
                    obj.get("text").getAsString(),
                    obj.get("start").getAsDouble(),
                    obj.get("end").getAsDouble(),
                    obj.get("sourceFile").getAsString()
            ));
        }

        // Load store
        String storeJson = root.get("store").getAsString();
        return InMemoryEmbeddingStore.fromJson(storeJson);
    }

    public List<SearchResult> search(String query, int maxResults) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .build();

        var searchResult = embeddingStore.search(request);
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

        List<SearchResult> results = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            String matchedText = match.embedded().text();
            SegmentMetadataRecord metadata = findMetadataByText(matchedText);

            if (metadata != null) {
                results.add(new SearchResult(
                        metadata.text(),
                        metadata.start(),
                        metadata.end(),
                        match.score(),
                        metadata.sourceFile()
                ));
            }
        }

        return results;
    }

    private SegmentMetadataRecord findMetadataByText(String text) {
        for (SegmentMetadataRecord metadata : segmentMetadata) {
            if (metadata.text().equals(text)) {
                return metadata;
            }
        }
        return null;
    }
}
