package com.nio.screenprojection;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaMuxer;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import static android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC;

class Info {
    public Info() {
    }
    long start = 0;
    long end = 0;
}

public class TransCodeWrapper {

    private static final String TAG = TransCodeWrapper.class.getName();
    private static final int AV_GAP = 500; // 500MS

    private Thread inputLoop, outputLoop;
    private ArrayList<Info> fileList;
    private Info currentInfo = new Info(), preInfo = null;
    private boolean bStop = false;

    private MediaExtractor audioExtractor, videoExtractor;
    private int audioTrackIndex, videoTrackIndex;
    private MediaCodec audioDecoder, audioEncoder;
    private MediaCodec videoDecoder, videoEncoder;
    private MediaMuxer mediaMuxer;

    private long audioEncoderInputTimestamp = 0, videoEncoderInputTimestamp = 0;
    private long audioReadTimestamp, videoReadTimestamp;

    public TransCodeWrapper() {
        initExtractor();
        initAudioCodec();
        initVideoCodec();
        initMuxer();

        inputLoop = new Thread(new Runnable() {
            @Override
            public void run() {
                inputLoopInterface();
            }
        });

        outputLoop = new Thread(new Runnable() {
            @Override
            public void run() {
                outputLoopInterface();
            }
        });
    }

    public int start() {
        inputLoop.start();
        outputLoop.start();
        return 0;
    }

    public int stop() {
        return 0;
    }

    private int initExtractor() {
        return 0;
    }

    private int initAudioCodec() {
        return 0;
    }

    private int initVideoCodec() {
        return 0;
    }

    private int initMuxer() {
        return 0;
    }

    private int inputLoopInterface() {

        MediaExtractor extractor = videoExtractor;
        MediaCodec decoder = videoDecoder;
        int readIndex = videoTrackIndex;
        long readTimeStamp;
        boolean needSwitchNext = true, audioDecoderEof = false, videoDecoderEof = false;
        int currentInforIndex = -1;

        while (false != bStop) {

            if (true == needSwitchNext) {
                currentInforIndex ++;
                if (currentInforIndex < fileList.size()) {
                    preInfo = currentInfo;
                    currentInfo.start = fileList.get(currentInforIndex).start;
                    currentInfo.end = fileList.get(currentInforIndex).end;

                    if (0 < currentInfo.start) {
                        audioExtractor.seekTo(currentInfo.start, SEEK_TO_PREVIOUS_SYNC);
                        videoExtractor.seekTo(currentInfo.start, SEEK_TO_PREVIOUS_SYNC);
                    }
                    needSwitchNext = false;
                }
                if (true == needSwitchNext) {
                    if (false == audioDecoderEof) {
                        int index = audioDecoder.dequeueInputBuffer(5);
                        if (0 <= index) {
                            audioDecoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            audioDecoderEof = true;
                        }
                    }
                    if (false == videoDecoderEof) {
                        int index = videoDecoder.dequeueInputBuffer(5);
                        if (0 <= index) {
                            videoDecoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            videoDecoderEof = true;
                        }
                    }
                    if (false == audioDecoderEof || false == videoDecoderEof)
                        continue;
                    else
                        break;
                }
            }

            if (Math.abs(audioReadTimestamp - videoReadTimestamp) > AV_GAP) {
                readIndex = videoTrackIndex == readIndex ? audioTrackIndex : videoTrackIndex;
                extractor = videoExtractor.equals(extractor) ? audioExtractor : videoExtractor;
                extractor.selectTrack(readIndex);
                decoder = videoDecoder.equals(decoder) ? audioDecoder : videoDecoder;
            }

            int index = decoder.dequeueInputBuffer(5);
            if (0 <= index) {
                int size = extractor.readSampleData(decoder.getInputBuffer(index), 0);
                if (0 < size) {
                    readTimeStamp = extractor.getSampleTime();




                    decoder.queueInputBuffer(index, 0, size, readTimeStamp, extractor.getSampleFlags());
                    extractor.advance();

                    if (readIndex == audioTrackIndex)
                        audioReadTimestamp = readTimeStamp;
                    else
                        videoReadTimestamp = readTimeStamp;
                    if(audioReadTimestamp >= currentInfo.end && videoReadTimestamp >= currentInfo.end)
                        needSwitchNext = true;

                } else {
                    Log.e(TAG, "Read sample data error " + size);
                    needSwitchNext = true;
                    continue;
                }
            }
        }

        return 0;
    }

    private int outputLoopInterface() {
        int audioDecoderIndex = MediaCodec.INFO_TRY_AGAIN_LATER, audioEncoderIndex = MediaCodec.INFO_TRY_AGAIN_LATER;
        int videoDecoderIndex = MediaCodec.INFO_TRY_AGAIN_LATER, videoEncoderIndex = MediaCodec.INFO_TRY_AGAIN_LATER;

        while (!bStop) {
            if (MediaCodec.BUFFER_FLAG_END_OF_STREAM != audioDecoderIndex)
                audioDecoderIndex = decoder2Encoder(true);

            if (MediaCodec.BUFFER_FLAG_END_OF_STREAM != videoDecoderIndex)
                videoDecoderIndex = decoder2Encoder(false);

            if (MediaCodec.BUFFER_FLAG_END_OF_STREAM != audioEncoderIndex)
                audioEncoderIndex = encoder2Muxer(true);

            if (MediaCodec.BUFFER_FLAG_END_OF_STREAM != videoEncoderIndex)
                videoEncoderIndex = encoder2Muxer(false);

            if (MediaCodec.BUFFER_FLAG_END_OF_STREAM != audioEncoderIndex && MediaCodec.BUFFER_FLAG_END_OF_STREAM != videoEncoderIndex) {
                Log.d(TAG, "Process finished ");
                break;
            }
        }

        return 0;
    }

    private int decoder2Encoder(boolean audio) {
        // Video decoder to encoder
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int decoderIndex = MediaCodec.INFO_TRY_AGAIN_LATER;
        int encoderIndex = MediaCodec.INFO_TRY_AGAIN_LATER;
        boolean encoderRetry = false;

        MediaCodec decoder = audio ? audioDecoder : videoDecoder;
        MediaCodec encoder = audio ? audioEncoder : videoEncoder;

        while (true) {

            if (!encoderRetry)
                decoderIndex = decoder.dequeueOutputBuffer(bufferInfo, 5);

           if (0 <= decoderIndex || MediaCodec.BUFFER_FLAG_END_OF_STREAM == decoderIndex) {
                if (currentInfo.start <= bufferInfo.presentationTimeUs || MediaCodec.BUFFER_FLAG_END_OF_STREAM == decoderIndex) {
                    encoderIndex = encoder.dequeueInputBuffer(50);
                    if (0 <= encoderIndex) {
                        long gap = bufferInfo.presentationTimeUs - currentInfo.start;
                        if (audio)
                            audioEncoderInputTimestamp += gap;
                        else
                            videoEncoderInputTimestamp += gap;

                        if (MediaCodec.BUFFER_FLAG_END_OF_STREAM == decoderIndex) {
                            encoder.queueInputBuffer(encoderIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            Log.d(TAG, audio ? "Audio" : "Video" + " decoder received EOF");
                            return MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        } else {
                            ByteBuffer encodeBuffer = videoEncoder.getInputBuffer(encoderIndex);
                            // TODO, buffer copy
                            encoder.getOutputBuffer(decoderIndex).wrap(encodeBuffer.array(), bufferInfo.offset, bufferInfo.size);
                            // TODO, encoder input flag.
                            encoder.queueInputBuffer(encoderIndex, bufferInfo.offset, bufferInfo.size, audio ? audioEncoderInputTimestamp : videoEncoderInputTimestamp, bufferInfo.flags);
                        }
                    } else {
                        encoderRetry = true;
                        continue;
                    }
                } else {
                    // TODO, Do nothing, Drop this frame.
                }
                decoder.releaseOutputBuffer(decoderIndex, false);
            }
            break;
        }

        return 0;
    }

    private int encoder2Muxer(boolean audio) {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        MediaCodec encoder = audio ?  audioEncoder : videoEncoder;

        int encoderIndex = encoder.dequeueOutputBuffer(bufferInfo, 5);
        if (MediaCodec.BUFFER_FLAG_END_OF_STREAM == encoderIndex) {
            Log.d(TAG, audio ? "Audio" : "Video" + " encoder received EOF");
            return MediaCodec.BUFFER_FLAG_END_OF_STREAM;
        } else if (0 <= encoderIndex) {
            ByteBuffer encodedData = encoder.getOutputBuffer(encoderIndex);
            mediaMuxer.writeSampleData(audio ? audioTrackIndex : videoTrackIndex, encodedData, bufferInfo);
            encoder.releaseOutputBuffer(encoderIndex, false);
        }
        return 0;
    }
}
