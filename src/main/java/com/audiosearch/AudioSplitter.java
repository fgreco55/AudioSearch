package com.audiosearch;

/**
 * @deprecated Use {@link com.audiosearch.spi.AudioSplitter} instead.
 * This class has been refactored into a pluggable architecture in the spi package.
 * The factory {@link com.audiosearch.spi.AudioSplitterFactory} selects between:
 * - {@link com.audiosearch.spi.FfmpegAudioSplitter} - uses external ffmpeg
 * - {@link com.audiosearch.spi.Mp3FrameAudioSplitter} - pure Java MP3 frame parser
 */
@Deprecated(since = "1.0", forRemoval = true)
public class AudioSplitter {
    // This class is deprecated. Use com.audiosearch.spi.AudioSplitter instead.
}
