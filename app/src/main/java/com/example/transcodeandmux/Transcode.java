package com.example.transcodeandmux;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Created by weizheng.huang on 2019-10-18.
 */
public class Transcode {
    private int isMuxed = 0;
    private int audioTrackIndex = -1 ;
    private int videoTrackIndex = -1 ;
    private int videoIndex  = -1;
    private int audioIndex = -1;
    private boolean isMuxerStarted = false;
    private double durationTotal = 0;
    private long TIME_US = 70000L;
    private long startTime1 = 0;
    private long endTime1 = 0;
    private long startTime2 = 0;
    private long endTime2 = 0;
    private boolean isNeedTailed = false;
    private long timeA = 0;
    private long timeV = 0;
    private long startsTime1 = 0;
    private long startsTime2 = 0;



    void setTailTimeVideo(long startTime,long endTime){
        long s = 0;
        long e = (long)durationTotal;
        startTime *= 1000000;
        endTime *= 1000000;
        this.startTime1 = startTime > s ? startTime  : s;
        this.endTime1 = (endTime < e) && (endTime != 0) ? endTime : e;
        this.isNeedTailed = (startTime > 0) || (endTime < e);
    }
    void setTailTimeAudio(long startTime , long endTime){
        long s = 0;
        long e = (long)durationTotal;
        startTime *= 1000000;
        endTime *= 1000000;
        this.startTime2 = startTime > s ? startTime  : s;
        this.endTime2 = (endTime < e) && (endTime != 0) ? endTime : e;
        this.isNeedTailed = (startTime > 0) || (endTime < e);

    }

    /**
     *
     * @param audioIndex   extractor分离的音轨信道
     * @param videoIndex   extractor分离的视轨信道
     * @param durationTotal 视频总时长/us
     */
    Transcode(int audioIndex , int videoIndex , double durationTotal){
       this.audioIndex = audioIndex;
       this.videoIndex = videoIndex;
       this.durationTotal = durationTotal;
    }
    synchronized void releaseMuxer(MediaMuxer muxer){
        isMuxed++;
        if (isMuxed == 2){
            isMuxed++;
            muxer.stop();
            muxer.release();
            Log.v("tag","released muxer");
        }
    }
    void inputLoop(MediaExtractor extractor ,MediaCodec decodec , MediaCodec encodec) {
        //////////video decode///////////////
        extractor.selectTrack(videoIndex);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        startsTime1 = extractor.getSampleTime();
        if (isNeedTailed && startTime1 > 0 ) {
            extractor.seekTo(startTime1, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            if (extractor.getSampleTime() < startTime1) {
                extractor.seekTo(startTime1,MediaExtractor.SEEK_TO_NEXT_SYNC);
            }
            startsTime1 = extractor.getSampleTime();
        }
        while (true) {
            int inputIndex = decodec.dequeueInputBuffer(TIME_US);
            if (inputIndex >= 0) {
                ByteBuffer inputBuffer = decodec.getInputBuffer(inputIndex);
                assert inputBuffer != null;
                int size = readSampleData(extractor,inputBuffer);
                if (size > 0) {
                    if (extractor.getSampleTime() > endTime1 && isNeedTailed ) {
                        decodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {

                        decodec.queueInputBuffer(inputIndex, 0, size, extractor.getSampleTime(), MediaCodec.BUFFER_FLAG_KEY_FRAME);
                        extractor.advance();
                    }
                } else {
                    decodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
            }
            int outputIndex = decodec.dequeueOutputBuffer(info, TIME_US);

            if (outputIndex >= 0) {
                ByteBuffer outputBuffer = decodec.getOutputBuffer(outputIndex);

                int inputBufferEncodeIndex = encodec.dequeueInputBuffer(TIME_US);
                if (inputBufferEncodeIndex >= 0) {
                    ByteBuffer inputEncodeBuffer = encodec.getInputBuffer(inputBufferEncodeIndex);
                    if (info.size >= 0) {
                        //              Log.d("tag","into video encode...");
                        assert outputBuffer != null;
                        assert inputEncodeBuffer != null;
                        inputEncodeBuffer.put(outputBuffer);
                        encodec.queueInputBuffer(inputBufferEncodeIndex,0,info.size,info.presentationTimeUs,info.flags);


                    } else {
                        encodec.queueInputBuffer(inputBufferEncodeIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                }
                decodec.releaseOutputBuffer(outputIndex, true);
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 ) {
                Log.d("tag", "start release video Decode");
                break;

            }
        }
    }
    void outputLoop(MediaCodec encodec , MediaMuxer muxer){
        MediaCodec.BufferInfo mediaInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo info1 = null;
        final long time = timeV;
        long startTime = startsTime1;
        ////////////video encode/////////////////////
        while(true) {
            int outputBufferIndex = encodec.dequeueOutputBuffer(mediaInfo, TIME_US);
            switch (outputBufferIndex) {
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED: {
                    Log.d("tag", "video format changed");
                    MediaFormat format = encodec.getOutputFormat();
                    if (videoTrackIndex < 0)
                        videoTrackIndex = muxer.addTrack(format);
                    if (!isMuxerStarted) {
                        startMuxer(muxer);
                    }
                    startTime = startsTime1;
                    break;
                }
                default: {
                    ByteBuffer outputBuffer = encodec.getOutputBuffer(outputBufferIndex);

                    if (!isMuxerStarted && mediaInfo.size >= 0 ){
                        try {
                            Thread.sleep(160L);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        startTime = startsTime1;

                    }
                    if (mediaInfo.size >= 0  && mediaInfo.presentationTimeUs >= startTime && isMuxerStarted) {
                        info1 = new MediaCodec.BufferInfo();
                        info1.size = mediaInfo.size;
                        info1.offset = mediaInfo.offset;
                        info1.presentationTimeUs = time + mediaInfo.presentationTimeUs - startTime ;
                        info1.flags = mediaInfo.flags;
                        assert outputBuffer != null;
                        muxer.writeSampleData(videoTrackIndex, outputBuffer, info1);
//                        Log.d("tag", "video muxing");

                    }

                    encodec.releaseOutputBuffer(outputBufferIndex, true);
                }
            }
            if ((mediaInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0  ) {
                Log.d("tag", "start release video Encode");
                assert info1 != null;
                timeV = info1.presentationTimeUs;
                break;
            }
        }
    }
    void audioInputLoop(MediaExtractor audioExtractor , MediaCodec audioDecodec , MediaCodec audioEncodec) {

        audioExtractor.selectTrack(audioIndex);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        startsTime2 = audioExtractor.getSampleTime();
        if (isNeedTailed && startTime2 > 0) {
            audioExtractor.seekTo(startTime2, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            if (audioExtractor.getSampleTime() < startTime2){
                audioExtractor.seekTo(startTime2,MediaExtractor.SEEK_TO_NEXT_SYNC);
            }
            startsTime2 = audioExtractor.getSampleTime();
        }

        while (true) {
            int inputIndex = audioDecodec.dequeueInputBuffer(TIME_US);
            if (inputIndex >= 0) {
                ByteBuffer inputBuffer = audioDecodec.getInputBuffer(inputIndex);
                assert inputBuffer != null;
                int size = readSampleData(audioExtractor , inputBuffer);
                if (size >= 0) {
                    if (audioExtractor.getSampleTime() > endTime2 && isNeedTailed) {
                        audioDecodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {

                        //              Log.d("tag","video decode...");

                        audioDecodec.queueInputBuffer(inputIndex, 0, size, audioExtractor.getSampleTime(), audioExtractor.getSampleFlags());
                        audioExtractor.advance();
                    }
                } else {
                    audioDecodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }

            }
            int outputIndex = audioDecodec.dequeueOutputBuffer(info, TIME_US);
            if (outputIndex >= 0) {
                ByteBuffer outputBuffer = audioDecodec.getOutputBuffer(outputIndex);

                int inputBufferEncodeIndex = audioEncodec.dequeueInputBuffer(TIME_US);
                if (inputBufferEncodeIndex >= 0) {
                    ByteBuffer inputEncodeBuffer = audioEncodec.getInputBuffer(inputBufferEncodeIndex);
                    if (info.size >= 0) {
                        assert inputEncodeBuffer != null;
                        assert outputBuffer != null;
                        inputEncodeBuffer.put(outputBuffer);
                        audioEncodec.queueInputBuffer(inputBufferEncodeIndex, 0, info.size, info.presentationTimeUs , info.flags);
                    } else {
                        audioEncodec.queueInputBuffer(inputBufferEncodeIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                }
                audioDecodec.releaseOutputBuffer(outputIndex, true);
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {

                Log.d("tag", "start release audio Decode");
                break;
            }
            if(!isMuxerStarted){
                try {

                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    void audioOutputLoop(MediaCodec audioEncodec , MediaMuxer muxer){

        MediaCodec.BufferInfo mediaInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo info1 = null;
        final long time  = timeA;
        long startTime = startsTime1;
        while(true) {
            int outputBufferIndex = audioEncodec.dequeueOutputBuffer(mediaInfo, TIME_US);
            switch (outputBufferIndex) {
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED: {
                    Log.d("tag", "audio format changed");
                    MediaFormat format = audioEncodec.getOutputFormat();
                    if (audioTrackIndex < 0)
                        audioTrackIndex = muxer.addTrack(format);
                    if (!isMuxerStarted) {
                        startMuxer(muxer);
                    }
                    startTime = startsTime1;
                    break;
                }
                default: {
                    ByteBuffer outputBuffer = audioEncodec.getOutputBuffer(outputBufferIndex);
                    Log.v("timeStamps1",mediaInfo.presentationTimeUs + " size " + mediaInfo.size);

                    if (!isMuxerStarted && mediaInfo.size >= 0 ){
                        try {
                            Thread.sleep(200L);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        startTime = startsTime1;
                    }
                    if (mediaInfo.size > 0  && mediaInfo.presentationTimeUs >= startTime && isMuxerStarted) {
                        Log.v("timeStamps2",mediaInfo.presentationTimeUs + "");
                        info1 = new MediaCodec.BufferInfo();
                        info1.size = mediaInfo.size;
                        info1.offset = mediaInfo.offset;
                        info1.presentationTimeUs = time + mediaInfo.presentationTimeUs - startTime ;
//                        Log.v("timeStamps2",info1.presentationTimeUs + "");
                        info1.flags = mediaInfo.flags;
                        assert outputBuffer != null;
                        muxer.writeSampleData(audioTrackIndex, outputBuffer, info1);
//                        Log.d("tag", "audio muxing");
                    }

                    audioEncodec.releaseOutputBuffer(outputBufferIndex, true);
                }
            }
            if ((mediaInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0  ) {

                Log.d("tag", "start release audio Encode");
                assert info1 != null;
                timeA = info1.presentationTimeUs;
                break;
            }
        }
    }


    private synchronized void startMuxer(MediaMuxer muxer){
        if ( 0 <= audioTrackIndex && 0<= videoTrackIndex && !isMuxerStarted){

            muxer.start();
            isMuxerStarted = true;
        }
    }

    private synchronized int readSampleData(MediaExtractor extractor , ByteBuffer buffer){
        return extractor.readSampleData(buffer,0);
    }

}
