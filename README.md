# AudioSearch

An interactive CLI application for semantic search across multiple MP3 audio files using OpenAI's Whisper API and embeddings.

## Features

- **Interactive CLI** with commands for file management and searching
- **Multi-file support** - Load multiple audio files cumulatively into one embedding store
- **Transcription** - Convert MP3 files to text using OpenAI's Whisper API with segment-level timestamps
- **Semantic search** - Query across all loaded audio files with relevance scoring
- **Threshold filtering** - Set a minimum relevance score for search results
- **Speech detection** - Automatically skip silence and instrumental sections (no speech)
- **Store management** - View loaded files, check timestamps, deduplicate entries

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
```

### 3. Run

```bash
./gradlew run
```

Or build a fat JAR:
```bash
./gradlew fatJar
java -jar build/libs/audiosearch.jar
```

## Interactive Commands

### /file - Load Audio File
Load an audio file for transcription and indexing. Files are added cumulatively to the embedding store.

```
> /file
Available audio files in src/main/resources:
  1. frank.mp3
  2. johnny-b-goode-chuck-berry.mp3
  3. american-idiot-Greenday.mp3

Enter file name or number (1-3): 1
```

You can specify either:
- **Ordinal number**: `1`, `2`, `3` (from the list shown)
- **Full filename**: `frank.mp3`, `johnny-b-goode-chuck-berry.mp3`

### /search - Semantic Search
Search across all loaded audio files for relevant segments.

```
> /search query text here
```

Or enter interactively:
```
> /search
Enter search query: query text here
```

Returns top 5 results filtered by the current relevance threshold, showing:
- Segment text
- Audio timestamps (HH:MM:SS format)
- Similarity score (0.0-1.0)
- Source file name

### /threshold - Set Relevance Threshold
Set the minimum similarity score for search results (0.0 to 1.0).

```
> /threshold 0.75        # Only return results with 75%+ similarity
> /threshold             # Show current threshold
```

### /topn - Set Maximum Results
Set the maximum number of search results to return.

```
> /topn 10               # Return up to 10 results
> /topn 3                # Return up to 3 results
> /topn                  # Show current maximum
```

Default is 5 results.

### /status - Show Application Status
Display loaded files and current settings.

```
> /status
AudioSearch Status:
────────────────────────────────────────────────────────────────────────────────
Embedding Store: /path/to/embedding-store.json
Relevance Threshold: 0.7500
Maximum Search Results: 10
Loaded Files: 3
  - frank.mp3
  - johnny-b-goode-chuck-berry.mp3
  - american-idiot-Greenday.mp3
```

### /timestamps - View All Segments
Display all indexed segments with their timestamps and source files.

```
> /timestamps
Timestamps for chunks in embedding-store.json:
────────────────────────────────────────────────────────────────────────────────
  1. [00:00:05 - 00:00:12] frank.mp3        Demand specificity at work...
  2. [00:00:13 - 00:00:18] frank.mp3        Ask for the AI Economic Dashboard...
...
```

### /index - Change Embedding Store
Specify a different embedding store file.

```
> /index my-custom-store.json
Embedding store set to: my-custom-store.json
```

### /deduplicate - Clean Up Store
Remove duplicate entries from the embedding store (metadata and embeddings). Useful after interrupted operations.

```
> /deduplicate
Store cleanup completed successfully
```

### /help - Show Available Commands
Display all available commands and options.

```
> /help
```

### /exit or /quit - Exit Application
```
> /exit
Goodbye!
```

## Architecture

```
src/main/java/com/audiosearch/
├── AudioSearchApp.java                    # CLI entry point and command dispatcher
├── WhisperTranscriber.java               # OpenAI Whisper API integration
├── EmbeddingIndexer.java                 # Embeddings generation and storage
├── SemanticSearcher.java                 # Semantic search functionality
├── model/
│   ├── TranscriptionSegment.java
│   └── SearchResult.java
└── commands/                              # Command handling
    ├── CommandHandler.java               # All command implementations
    ├── AppState.java                     # Shared application state
    └── StoreDeduplicator.java            # Store cleanup utility
```

## How It Works

### Loading Files
1. User runs `/file` and selects an audio file
2. **WhisperTranscriber** sends the file to OpenAI's Whisper API
3. Audio is transcribed into segments with timestamps
4. Segments with no speech (silence, instrumental) are filtered out
5. **EmbeddingIndexer** generates embeddings for each segment using `text-embedding-3-small`
6. New embeddings are merged with existing ones in the store
7. All metadata (text, timestamps, source file) is saved

### Searching
1. User runs `/search` with a query
2. **SemanticSearcher** loads the embedding store
3. Query is embedded using the same model
4. Top 5 most similar segments are found
5. Results are filtered by the relevance threshold
6. Results are displayed with source files and timestamps

## Embedding Store Format

The `embedding-store.json` file contains:
- **store**: JSON representation of all embeddings from all loaded files
- **metadata**: Array of segment metadata (text, timestamps, source file)

Multiple files' data are accumulated through merging:
- Old embeddings are preserved
- New embeddings are appended
- Metadata is combined

## Large File Handling

If an audio file exceeds Whisper's 25 MB size limit, AudioSearch will automatically split it into chunks and transcribe each one:

1. **Split into chunks** - Audio is split into 10-minute segments
2. **Transcribe each chunk** - Each chunk is transcribed separately via Whisper API
3. **Preserve timestamps** - Segment timestamps are adjusted to reflect their position in the original audio
4. **Merge results** - All transcribed segments are combined into a single result

### Audio Splitter Implementations

AudioSearch supports two implementations:

#### 1. Pure Java MP3 Frame Parser (Recommended for large files)
- **No external dependencies** - Uses only Java standard library
- **Accurate timestamp preservation** - Tracks actual frame durations, not fixed offsets
- **Works without ffmpeg** - Best for environments where external tools cannot be installed

#### 2. ffmpeg-based Splitter
- **Uses external ffmpeg process** - Requires ffmpeg to be installed
- **Reliable on complex MP3s** - Handles edge cases in MP3 format
- **Fallback option** - Available if issues arise with pure Java parser

### Choosing an Implementation

Specify which splitter to use via startup parameter, environment variable, or auto-detection:

```bash
# Use pure Java (no ffmpeg needed)
AUDIOSEARCH_SPLITTER=java ./gradlew run

# Use ffmpeg explicitly
java -Daudiosearch.splitter=ffmpeg -jar audiosearch.jar

# Auto-detect (uses ffmpeg if available, falls back to Java)
java -jar audiosearch.jar
```

**Priority order:**
1. JVM system property: `-Daudiosearch.splitter=java|ffmpeg`
2. Environment variable: `AUDIOSEARCH_SPLITTER=java|ffmpeg`
3. Auto-detect: checks for ffmpeg availability

### Installing ffmpeg (Optional)

Only required if using the ffmpeg implementation explicitly:

**macOS (Homebrew):**
```bash
brew install ffmpeg
```

**Ubuntu/Debian:**
```bash
sudo apt-get install ffmpeg
```

**Windows:**
Download from https://ffmpeg.org/download.html

## Notes

- Segments with no meaningful speech are automatically excluded during indexing
- The embedding store accumulates data from multiple files (cumulative)
- Each segment preserves its audio timestamp for precise seeking
- Search scores range from 0.0 (no match) to 1.0 (perfect match)
- Results are limited to top 5 matches by default (adjustable with /topn)
- The embedding store is persisted as JSON and can be shared/backed up
- Large audio files (>25 MB) are automatically split and transcribed in chunks with correct timestamps
