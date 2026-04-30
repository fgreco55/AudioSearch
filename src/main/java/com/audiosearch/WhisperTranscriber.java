package com.audiosearch;

import com.audiosearch.model.TranscriptionSegment;
import com.audiosearch.spi.AudioSplitter;
import com.audiosearch.spi.AudioSplitterFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class WhisperTranscriber {
    private static final String WHISPER_URL = "https://api.openai.com/v1/audio/transcriptions";
    private final String apiKey;
    private final OkHttpClient httpClient;
    private final AudioSplitter audioSplitter;

    public WhisperTranscriber(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.audioSplitter = AudioSplitterFactory.create();
    }

    public List<TranscriptionSegment> transcribe(File audioFile) throws IOException {
        if (!audioFile.exists()) {
            throw new IllegalArgumentException("Audio file not found: " + audioFile.getAbsolutePath());
        }

        // Handle large files by splitting them into chunks
        if (AudioSplitter.needsSplitting(audioFile)) {
            return audioSplitter.split(audioFile, this);
        }

        return transcribeSingleFile(audioFile);
    }

    private List<TranscriptionSegment> transcribeSingleFile(File audioFile) throws IOException {
        RequestBody fileBody = RequestBody.create(audioFile, MediaType.parse("audio/mpeg"));

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.getName(), fileBody)
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("response_format", "verbose_json")
                .build();

        Request request = new Request.Builder()
                .url(WHISPER_URL)
                .header("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error details";
                throw new IOException("Whisper API error: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            return parseSegments(responseBody);
        }
    }

    private List<TranscriptionSegment> parseSegments(String jsonResponse) {
        List<TranscriptionSegment> segments = new ArrayList<>();
        JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
        JsonArray segmentsArray = json.getAsJsonArray("segments");

        for (JsonElement element : segmentsArray) {
            JsonObject segment = element.getAsJsonObject();
            int id = segment.get("id").getAsInt();
            double start = segment.get("start").getAsDouble();
            double end = segment.get("end").getAsDouble();
            String text = segment.get("text").getAsString();

            segments.add(new TranscriptionSegment(id, start, end, text));
        }

        return segments;
    }
}
