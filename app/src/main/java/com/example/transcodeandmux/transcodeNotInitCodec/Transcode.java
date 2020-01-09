package com.example.transcodeandmux.transcodeNotInitCodec;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaMuxer;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.transcodeandmux.utils.TailTimer;

import java.nio.ByteBuffer;
import java.util.List;


public class Transcode {

    private static final String TAG = Transcode.class.getName();
    private static final int AV_GAP = 500000; // 500MS

    private List<TailTimer> fileList;
    private long startTime = 0;
    private long endTime = 0;
    private long TIMEUS = 70000L;

    private int audioTrackIndex = -1;
    private int videoTrackIndex = -1;
    private int videoIndex = -1;
    private int audioIndex = -1;

    private long audioEncoderInputTimestamp = 0, videoEncoderInputTimestamp = 0;
    private long audioReadTimestamp, videoReadTimestamp;

    private boolean pauseTime = false;
    private boolean isMuxerStarted = false;
    private long gapaA = 0;
    private long gapaV = 0;
    private long gapbA = 0;
    private long gapbV = 0;
    public Transcode( List<TailTimer> fileList , int audioIndex , int audioTrackIndex , int videoIndex , int videoTrackIndex){
        this.fileList = fileList;
        this.audioIndex = audioIndex ;
        this.audioTrackIndex = audioTrackIndex;
        this.videoIndex = videoIndex;
        this.videoTrackIndex = videoTrackIndex;


    }

    private long timeA = 0 ;
    private long timeV = 0 ;

    void inputLoopInterface(@Nullable MediaExtractor videoExtractor, @Nullable MediaExtractor audioExtractor, @Nullable MediaCodec videoDecoder, @Nullable MediaCodec audioDecoder) {

        MediaExtractor extractor = videoExtractor;
        MediaCodec decoder = videoDecoder;
        int readIndex = videoIndex;
        boolean  audioDecoderEof, videoDecoderEof;
        boolean readAudioEOF = false;
        boolean readVideoEOF = false;
        for (int i = 0; i < fileList.size() ; i++) {
            startTime = fileList.get(i).getStartTime() * 1000000;
            endTime = fileList.get(i).getEndTime() * 1000000;
            audioExtractor.selectTrack(audioIndex);
            videoExtractor.selectTrack(videoIndex);
//            if (0 <= startTime) {
//                videoExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
//                if (videoExtractor.getSampleTime() < startTime){
//                    videoExtractor.seekTo(startTime,MediaExtractor.SEEK_TO_NEXT_SYNC);
//                }
//                audioExtractor.seekTo(videoExtractor.getSampleTime(), MediaExtractor.SEEK_TO_CLOSEST_SYNC);
//                if (audioExtractor.getSampleTime() < videoExtractor.getSampleTime()){
//                    audioExtractor.seekTo(videoExtractor.getSampleTime(),MediaExtractor.SEEK_TO_NEXT_SYNC);
//                }
//            }
            //TODO 时间戳控制，多秒不少秒，选择起始时间的前一个关键帧作为起始帧
            videoExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            if (videoExtractor.getSampleTime() > startTime){
                videoExtractor.seekTo(startTime,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }
            audioExtractor.seekTo(videoExtractor.getSampleTime(), MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            if (audioExtractor.getSampleTime() > startTime){
                audioExtractor.seekTo(startTime,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }
            startTime = videoExtractor.getSampleTime() < audioExtractor.getSampleTime() ? videoExtractor.getSampleTime() : audioExtractor.getSampleTime();
            audioDecoderEof = false;
            videoDecoderEof = false;
            readAudioEOF = false;
            readVideoEOF = false;
            Log.e(TAG , "start read index " + i);
            //debug: 观察第二段是否能读到数据，结果：可以
//            ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 1024);
//            Log.e(TAG , "time " + audioExtractor.getSampleTime() + " size " + audioExtractor.readSampleData(byteBuffer,0));
            while(true) {


                if (Math.abs(audioReadTimestamp - videoReadTimestamp) > AV_GAP && !audioDecoderEof && !videoDecoderEof) {
                    readIndex = videoIndex == readIndex ? audioIndex : videoIndex;
                    extractor = videoExtractor == extractor ? audioExtractor : videoExtractor;
                    decoder = videoDecoder == decoder ? audioDecoder : videoDecoder;
                }
                if (audioDecoderEof) {
                    readIndex = videoIndex;
                    extractor = videoExtractor;
                    decoder = videoDecoder;
                }
                if (videoDecoderEof) {
                    readIndex = audioIndex;
                    extractor = audioExtractor;
                    decoder = audioDecoder;
                }

                int inputDecIndex = decoder.dequeueInputBuffer(TIMEUS);
                if (0 <= inputDecIndex) {
                    int size = extractor.readSampleData(decoder.getInputBuffer(inputDecIndex), 0);
                    if (0 < size) {


                        if (decoder == audioDecoder){
                            gapaA = timeA + audioExtractor.getSampleTime() - startTime;
                            audioReadTimestamp += gapaA - gapbA;
                            gapbA = gapaA;
                        }else{
                            gapaV = timeV + videoExtractor.getSampleTime() - startTime;
                            videoReadTimestamp += gapaV - gapbV;
                            gapbV = gapaV;
                        }
//                        Log.e(TAG , "presentationTimeUs " + videoExtractor.getSampleTime() + "startTime " + startTime + " endTime " + endTime);
//                        Log.e(TAG , "gapaV " + gapaV + "gapbA " + gapbV + "totalPre " + videoReadTimestamp);

                        Log.e(TAG , "gapaA " + gapaA + "gapbA " + gapbA + "totalPre " + audioReadTimestamp);
                        decoder.queueInputBuffer(inputDecIndex, 0, size, (decoder == audioDecoder ? audioReadTimestamp : videoReadTimestamp), extractor.getSampleFlags());
                        Log.e(TAG, "presentationTIMEUs " + ( decoder == audioDecoder ? "audio " + audioReadTimestamp : "video " + videoReadTimestamp)+ " size " + size);
//                        Log.e(TAG, (videoDecoder == decoder ? "video " : "audio ") + "decoding");
                        extractor.advance();
                        if (audioExtractor.getSampleTime() >= endTime) {
                            audioDecoderEof = true;
                            Log.e(TAG ,"AUDIO EOF");
                        }
                        if (videoExtractor.getSampleTime() >= endTime) {

                            videoDecoderEof = true;
                            Log.e(TAG , "VIDEO EOF");
                        }

                    } else {
                        Log.e(TAG, "Read sample data error " + size);

                        if (decoder == audioDecoder) {
                            timeA = audioReadTimestamp;
                            audioDecoderEof = true;
                        } else {
                            timeV = videoReadTimestamp;
                            videoDecoderEof = true;
                        }
                    }
                }
                //查看是否应该跳出循环
                if (i == fileList.size() - 1) {
//                    Log.e(TAG , "startFinalBreak");
                    if (!readAudioEOF) {
                        if (audioDecoderEof) {
                            int index = audioDecoder.dequeueInputBuffer(TIMEUS);
                            if (0 <= index) {
                                audioDecoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                readAudioEOF = true;
                                Log.d(TAG, "send audioDec EOF ");
                            }
                        }
                    }
                    if (!readVideoEOF) {
                        if (videoDecoderEof) {
                            int index = videoDecoder.dequeueInputBuffer(TIMEUS);
                            if (0 <= index) {
                                videoDecoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                readVideoEOF = true;
                                Log.d(TAG, "send videoDec EOF ");
                            }
                        }
                    }
                }

                if (!audioDecoderEof || !videoDecoderEof) {

                    continue;
                }else {
                    Log.d(TAG , (audioDecoder == decoder ? "audio" : "video") + " break");
                    videoExtractor.unselectTrack(videoIndex);
                    audioExtractor.unselectTrack(audioIndex);
                    timeA = audioReadTimestamp;
                    timeV = videoReadTimestamp;
                    break;
                }

            }
        }
    }

    void outputLoopInterface(@Nullable MediaCodec audioDecoder, @Nullable MediaCodec videoDecoder, @Nullable MediaCodec audioEncoder, @Nullable MediaCodec videoEncoder, @Nullable MediaMuxer mediaMuxer) {
        int audioDecoderIndex = MediaCodec.INFO_TRY_AGAIN_LATER, audioEncoderIndex = MediaCodec.INFO_TRY_AGAIN_LATER;
        int videoDecoderIndex = MediaCodec.INFO_TRY_AGAIN_LATER, videoEncoderIndex = MediaCodec.INFO_TRY_AGAIN_LATER;
        while (true) {
            if (MediaCodec.BUFFER_FLAG_END_OF_STREAM != audioDecoderIndex)
                audioDecoderIndex = decoder2Encoder(true , audioDecoder,videoDecoder, audioEncoder , videoEncoder);


            if (MediaCodec.BUFFER_FLAG_END_OF_STREAM != videoDecoderIndex)
                videoDecoderIndex = decoder2Encoder(false , audioDecoder,videoDecoder, audioEncoder , videoEncoder);

            if (MediaCodec.BUFFER_FLAG_END_OF_STREAM != audioEncoderIndex)
                audioEncoderIndex = encoder2Muxer(true , audioEncoder , videoEncoder , mediaMuxer);

            if (MediaCodec.BUFFER_FLAG_END_OF_STREAM != videoEncoderIndex)
                videoEncoderIndex = encoder2Muxer(false ,  audioEncoder , videoEncoder , mediaMuxer);

            if (MediaCodec.BUFFER_FLAG_END_OF_STREAM == audioDecoderIndex && MediaCodec.BUFFER_FLAG_END_OF_STREAM == videoDecoderIndex){
                pauseTime = true;
                Log.d(TAG , "start release decode and extractor ");
            }
            if (MediaCodec.BUFFER_FLAG_END_OF_STREAM == audioEncoderIndex && MediaCodec.BUFFER_FLAG_END_OF_STREAM == videoEncoderIndex) {
                Log.d(TAG, "Process finished ");
                break;
            }
        }

    }

    private int decoder2Encoder(boolean audio , MediaCodec audioDecoder , MediaCodec videoDecoder , MediaCodec audioEncoder , MediaCodec videoEncoder) {
        // Video decoder to encoder
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int decoderIndex ;
        int encoderIndex ;

        MediaCodec decoder = audio ? audioDecoder : videoDecoder;
        MediaCodec encoder = audio ? audioEncoder : videoEncoder;


        decoderIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEUS);

        if (0 <= decoderIndex) {
            ByteBuffer decodeBuffer = decoder.getOutputBuffer(decoderIndex);
            encoderIndex = encoder.dequeueInputBuffer(TIMEUS);

            if (0 <= encoderIndex) {
                ByteBuffer encodeBuffer = encoder.getInputBuffer(encoderIndex);
//                    //PST计算出现问题，当第一段结束后，DST一旦重置，就会计算失误。
//                    if (audio) {
//                        gapA = bufferInfo.presentationTimeUs - gapA;
//                        audioEncoderInputTimestamp += gapA ;
//                        gapA = bufferInfo.presentationTimeUs;
//                    } else {
//                        gapV = bufferInfo.presentationTimeUs - gapV;
//                        videoEncoderInputTimestamp += gapV;
//                        gapV = bufferInfo.presentationTimeUs;
//                    }
                if (audio){
                    audioEncoderInputTimestamp = bufferInfo.presentationTimeUs;
                }else{
                    videoEncoderInputTimestamp = bufferInfo.presentationTimeUs;
                }
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    encoder.queueInputBuffer(encoderIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    Log.d(TAG, (audio ? "Audio" : "Video") + " decoder received EOF");
                    return MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                } else {

                    encodeBuffer.put(decodeBuffer);
                    if (bufferInfo.size >= 0) {
                        encoder.queueInputBuffer(encoderIndex, bufferInfo.offset, bufferInfo.size, audio ? audioEncoderInputTimestamp : videoEncoderInputTimestamp, bufferInfo.flags);
//                        Log.d(TAG, "presentationTIMEUs " + bufferInfo.presentationTimeUs + " size " + bufferInfo.size);
                    }
//                        Log.e(TAG,(audio ? "audio " : "video ") + "encoding");
                    decoder.releaseOutputBuffer(decoderIndex, false);
                }

        }
    }
        return 0;

}

    private int encoder2Muxer(boolean audio ,  MediaCodec audioEncoder ,  MediaCodec videoEncoder ,   MediaMuxer mediaMuxer) {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        MediaCodec encoder = audio ?  audioEncoder : videoEncoder;

        int encoderIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEUS);
        if ((MediaCodec.BUFFER_FLAG_END_OF_STREAM & bufferInfo.flags) != 0) {
            Log.d(TAG, (audio ? "Audio" : "Video") + " encoder received EOF");
            return MediaCodec.BUFFER_FLAG_END_OF_STREAM;
        }
        switch (encoderIndex) {
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                break;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED: {
                if (audio) {
                    if (audioTrackIndex < 0) {
                        audioTrackIndex = mediaMuxer.addTrack(encoder.getOutputFormat());
                    }
                }else{
                    if (videoTrackIndex < 0){
                        videoTrackIndex = mediaMuxer.addTrack(encoder.getOutputFormat());
                    }
                }
                Log.e(TAG , (audio ? "audio " : "video ") + " encoder format changed");
                if ( !isMuxerStarted && 0 <= audioTrackIndex && 0 <= videoTrackIndex ) {
                    mediaMuxer.start();
                    isMuxerStarted = true;
                }
                break;
            }
            default: {

                ByteBuffer encodedData = encoder.getOutputBuffer(encoderIndex);
                if (isMuxerStarted) {
                    mediaMuxer.writeSampleData(audio ? audioTrackIndex : videoTrackIndex, encodedData, bufferInfo);
//                    Log.d(TAG,( audio ? "audio" : "video" ) + " presentationTimeUs " + bufferInfo.presentationTimeUs + " size " + bufferInfo.size);
//                    Log.e(TAG,( audio ? "audio" : "video" )+ " muxing");
                }
                encoder.releaseOutputBuffer(encoderIndex, false);
            }
        }
        return 0;
    }
}
