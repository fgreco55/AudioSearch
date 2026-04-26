# AudioSearch

A CLI application for semantic search over MP3 audio files using OpenAI's Whisper API and embeddings.

## Features

- Transcribe MP3 files using OpenAI Whisper API with segment-level timestamps
- Generate embeddings for each transcription segment using OpenAI's `text-embedding-3-small` model
- Store embeddings in an in-memory vector store (persisted to JSON)
- Perform semantic search across transcribed segments

## Requirements

- Java 21+ (built with Java 25)
- OpenAI API key with access to:
  - Audio API (Whisper transcription)
  - Embeddings API

## Setup

### 1. Set Your API Key

```bash
export OPENAI_API_KEY=sk-...your-api-key...
```

### 2. Build

```bash
./gradlew build
./gradlew fatJar
```

The fat JAR will be created at `build/libs/audiosearch.jar`.

## Usage

### Index an Audio File

```bash
java -jar build/libs/audiosearch.jar index /path/to/audio.mp3
```

This will:
1. Send the MP3 to OpenAI's Whisper API for transcription
2. Receive transcription segments with timestamps
3. Generate embeddings for each segment
4. Save the embedding store to `embedding-store.json` in the current directory

### Search for Content

```bash
java -jar build/libs/audiosearch.jar search "your query here"
```

This will:
1. Load the embedding store from `embedding-store.json`
2. Generate an embedding for your query
3. Find the top 5 most relevant segments
4. Display results with text, timestamps, and similarity scores

### Specify Custom Store File

```bash
java -jar build/libs/audiosearch.jar index audio.mp3 --store my-store.json
java -jar build/libs/audiosearch.jar search "query" --store my-store.json
```

## Architecture

- **WhisperTranscriber** - Calls OpenAI Whisper API with multipart form upload
- **EmbeddingIndexer** - Generates embeddings and persists the store
- **SemanticSearcher** - Loads the store and performs similarity search
- **AudioSearchApp** - CLI entry point for index/search commands

## File Structure

```
src/main/java/com/audiosearch/
├── AudioSearchApp.java
├── WhisperTranscriber.java
├── EmbeddingIndexer.java
├── SemanticSearcher.java
└── model/
    ├── TranscriptionSegment.java
    └── SearchResult.java
```

## Dependencies

- **LangChain4j** - Embedding model and in-memory vector store
- **OkHttp** - HTTP client for Whisper API
- **Gson** - JSON parsing and serialization
- **SLF4J** - Logging

## Example Output

```
Transcribing audio file: meeting.mp3
Received 45 segments from Whisper
Embedding 45 segments...
Embedding complete. 45 segments indexed.
Store saved to: embedding-store.json

Searching for: "budget approval"

Top 5 results:

1. [234.5 - 251.3] (score: 0.8234) - "The budget was approved by the board"
   File: meeting.mp3

2. [189.2 - 203.4] (score: 0.7912) - "We need to finalize the budget numbers"
   File: meeting.mp3
...
```

## Notes

- Each Whisper segment is indexed as a single chunk
- Segment timestamps are preserved for audio seeking
- The embedding store is persisted as JSON and can be shared/copied
- Search returns top 5 results by default (adjustable in code)
