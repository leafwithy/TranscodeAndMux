package com.example.transcodeandmux;

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Created by weizheng.huang on 2019-10-18.
 */
public class TranscodeWrapperDemo {
    /////////private  //////
    private List<TailTimer> fileList;
    private MediaExtractor extractor,audioExtractor;

    private MediaCodec decodec,encodec,audioDecodec,audioEncodec;

    private MediaMuxer muxer;
    private String filePath = null;
    private AssetFileDescriptor srcFilePath = null;
    private AssetFileDescriptor srcFilePath2 = null;
    private int isMuxed = 0;
    private int isInputed = 0;
    private int audioTrackIndex = -1 ;
    private int videoTrackIndex = -1 ;
    private int videoIndex  = -1;
    private int audioIndex = -1;
    private boolean isMuxerStarted = false;
    private boolean pauseTranscode = false;
    private double assignSizeRate = 1.0;
    private double durationTotal = 0;
    private long TIME_US = 70000l;
    private long startTime1 = 0;
    private long endTime1 = 0;
    private long startTime2 = 0;
    private long endTime2 = 0;
    private boolean isNeedTailed = false;
    private long timeA = 0;
    private long timeV = 0;


    public void setTailTimeVideo(long startTime,long endTime){
        long s = 0;
        long e = (long)durationTotal;
        this.startTime1 = startTime > s ? startTime * 1000000 : s;
        this.endTime1 = (endTime < e) && (endTime != 0) ? endTime * 1000000: e;
        this.isNeedTailed = (startTime > 0) && (endTime < e);
    }
    public void setTailTimeAudio(long startTime , long endTime){
        long s = 0;
        long e = (long)durationTotal;
        this.startTime2 = startTime > s ? startTime * 1000000 : s;
        this.endTime2 = (endTime < e) && (endTime != 0) ? endTime * 1000000: e;
        this.isNeedTailed = (startTime > 0) && (endTime < e);

    }

    public synchronized  void setPauseTranscode(boolean TRUEORFALSE){
        pauseTranscode = TRUEORFALSE;
    }

    public void setAssignSize(Double rate) {
        if (rate > 100 && rate < 0) {
            throw new IllegalArgumentException("文件大小比例不合适");
        } else {
            assignSizeRate = Double.valueOf(new DecimalFormat(".0000").format(rate));
        }
    }

    public TranscodeWrapperDemo(String filePath, AssetFileDescriptor srcFilePath, AssetFileDescriptor srcFilePath2) {
        this.filePath = filePath;
        this.srcFilePath = srcFilePath;
        this.srcFilePath2 = srcFilePath2;
    }

    public TranscodeWrapperDemo(String filePath , List<TailTimer> fileList){
        this.filePath = filePath;
        this.fileList = fileList;
    }

    private Thread inputThread = new Thread(new Runnable() {
        @Override
        public void run() {
            if (fileList != null){
                for (int i = 0; i < fileList.size(); i++){
                    initVideoExtractor();
                    initVideoCodec();
                    TailTimer tailTimer = fileList.get(i);
                    setTailTimeVideo(tailTimer.getStartTime(),tailTimer.getEndTime());
                    inputLoop();
                    Log.d("tag","执行到fileListVideo的"+i);

                }
            }else{
                inputLoop();
            }

            extractor.release();
            decodec.stop();
            decodec.release();
            Log.v("tag","released decode");
            encodec.stop();
            encodec.release();
            Log.v("tag", "released encode");
            isMuxed++;
            releaseMuxer();
        }
    });

    private Thread audioInputThread = new Thread(new Runnable() {
        @Override
        public void run() {
            if (fileList != null){
                for (int i = 0; i < fileList.size(); i++){
                    initAudioExtractor();
                    initAudioCodec();
                    TailTimer tailTimer = fileList.get(i);
                    setTailTimeAudio(tailTimer.getStartTime(),tailTimer.getEndTime());
                    ////考虑在内部初始化
                    audioInputLoop();
                    Log.d("tag","执行到fileListAudio的"+i);
                }
            }else{
                audioInputLoop();
            }

            audioExtractor.release();
            audioDecodec.stop();
            audioDecodec.release();
            Log.v("tag","released audioDecode");
            audioEncodec.stop();
            audioEncodec.release();
            Log.v("tag","released audio encode");
            isMuxed++;
            releaseMuxer();
        }
    });
    private synchronized void releaseMuxer(){
        if (isMuxed == 2){
            isMuxed++;
            muxer.stop();
            muxer.release();
            Log.v("tag","released muxer");
        }
    }
    private void initVideoExtractor(){
        if (fileList != null){
            srcFilePath = fileList.get(0).getSrcPath();
        }
        extractor = new MediaExtractor();
        try{
            extractor.setDataSource(srcFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void initAudioExtractor(){
        if (fileList != null){
            srcFilePath2 = fileList.get(0).getSrcPath2();
        }
        audioExtractor = new MediaExtractor();
        try{
            audioExtractor.setDataSource(srcFilePath2);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private String videoFormatType = null;
    private String audioFormatType = null;
    private int width = -1;
    private int height = -1;
    private int frameRate = -1 ;
    private int bitRate = -1;
    private int audioBitRate = -1 ;
    private int sampleRate = -1;
    private int channelCount = -1;

    public void initAudioCodec(){
        for (int i = 0; i < extractor.getTrackCount(); i++){
            MediaFormat format = extractor.getTrackFormat(i);
            String formatType = format.getString(MediaFormat.KEY_MIME);
            if (formatType.startsWith("audio")){
                audioIndex = i;
                try {
                    audioDecodec = MediaCodec.createDecoderByType(formatType);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                audioFormatType = formatType;
                audioBitRate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                audioDecodec.configure(format,null,null,0);
                audioDecodec.start();
            }
        }
        MediaFormat audioFormat = MediaFormat.createAudioFormat(audioFormatType, sampleRate, channelCount);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE,(int)(audioBitRate * assignSizeRate));
        try {
            audioEncodec = MediaCodec.createEncoderByType(audioFormatType);
        } catch (IOException e) {
            e.printStackTrace();
        }
        audioEncodec.configure(audioFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioEncodec.start();
    }

    public void initVideoCodec(){
        for (int i = 0; i < extractor.getTrackCount(); i++){
            MediaFormat format = extractor.getTrackFormat(i);
            String formatType = format.getString(MediaFormat.KEY_MIME);
            if (formatType.startsWith("video")){
                videoIndex = i;
                try {
                    decodec = MediaCodec.createDecoderByType(formatType);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                videoFormatType = formatType;
                frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                bitRate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                width = format.getInteger(MediaFormat.KEY_WIDTH);
                height = format.getInteger(MediaFormat.KEY_HEIGHT);
                durationTotal = format.getLong(MediaFormat.KEY_DURATION);
                decodec.configure(format,null,null,0);
                decodec.start();
            }
        }
        MediaFormat videoFormat = MediaFormat.createVideoFormat(videoFormatType, width, height);

        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE,(int)(bitRate * assignSizeRate));
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,frameRate );


        try {
            encodec = MediaCodec.createEncoderByType(videoFormatType);
        } catch (IOException e) {
            e.printStackTrace();
        }
        encodec.configure(videoFormat, null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        encodec.start();
    }
    public void initMediaMuxer(){

        try {
            muxer = new MediaMuxer(filePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void inputLoop() {
        //////////video decode///////////////
        extractor.selectTrack(videoIndex);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo mediaInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo info1 = null;
        final long time = timeV;
        boolean closeExtractor = false;
        if (isNeedTailed) {
            extractor.seekTo(startTime1, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        }
        long beginTime = extractor.getSampleTime();
        while (true) {
            if (!closeExtractor) {
                int inputIndex = decodec.dequeueInputBuffer(TIME_US);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = decodec.getInputBuffer(inputIndex);
                    int size = extractor.readSampleData(inputBuffer, 0);
                    if (size > 0) {
                        if (extractor.getSampleTime() > endTime1 && isNeedTailed) {
                            decodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            closeExtractor = true;
                        } else {
                            decodec.queueInputBuffer(inputIndex, 0, size, extractor.getSampleTime(), extractor.getSampleFlags());
                            extractor.advance();
                        }
                    } else {
                        decodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        closeExtractor = true;
                    }
                }
            }
            int outputIndex = decodec.dequeueOutputBuffer(info, TIME_US);

            if (outputIndex >= 0) {
                ByteBuffer outputBuffer = decodec.getOutputBuffer(outputIndex);

                int inputBufferEncodeIndex = encodec.dequeueInputBuffer(TIME_US);
                if (inputBufferEncodeIndex >= 0) {
                    ByteBuffer inputEncodeBuffer = encodec.getInputBuffer(inputBufferEncodeIndex);
                    if (info.size > 0) {
                        //              Log.d("tag","into video encode...");
                        inputEncodeBuffer.put(outputBuffer);
                        encodec.queueInputBuffer(inputBufferEncodeIndex, 0, info.size, info.presentationTimeUs, info.flags);
                    } else {
                        encodec.queueInputBuffer(inputBufferEncodeIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                }
                decodec.releaseOutputBuffer(outputIndex, false);
            }

            ////////////video encode/////////////////////

            int outputBufferIndex = encodec.dequeueOutputBuffer(mediaInfo, TIME_US);
            switch (outputBufferIndex) {
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED: {
                    Log.d("tag", "video format changed");
                    MediaFormat format = encodec.getOutputFormat();
                    if (videoTrackIndex < 0)
                        videoTrackIndex = muxer.addTrack(format);
                    break;
                }
                default: {
                    ByteBuffer outputBuffer = encodec.getOutputBuffer(outputBufferIndex);
                    if (!isMuxerStarted) {
                        startMuxer();
                    }
                    if (mediaInfo.size > 0 && mediaInfo.presentationTimeUs > 0 && isMuxerStarted) {
                        info1 = new MediaCodec.BufferInfo();
                        info1.size = mediaInfo.size;
                        info1.offset = mediaInfo.offset;
                        info1.presentationTimeUs = time + mediaInfo.presentationTimeUs - beginTime;
                        info1.flags = mediaInfo.flags;
                        muxer.writeSampleData(videoTrackIndex, outputBuffer, info1);
                        Log.d("tag", "video muxing");

                    }

                    encodec.releaseOutputBuffer(outputBufferIndex, false);
                }
            }
            if ((mediaInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d("tag", "start release video Encode");
                extractor.unselectTrack(videoIndex);
                timeV = info1.presentationTimeUs;
                break;
            }
            while (pauseTranscode) {
                try {
                    Thread.sleep(10l);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void audioInputLoop() {
        audioExtractor.selectTrack(audioIndex);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo mediaInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo info1 = null;

        final long time  = timeA;
        boolean closeExtractor = false;
        if (isNeedTailed) {
            audioExtractor.seekTo(startTime2, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        }
        long beginTime = audioExtractor.getSampleTime();
        while (true) {
            if (!closeExtractor) {
                int inputIndex = audioDecodec.dequeueInputBuffer(TIME_US);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = audioDecodec.getInputBuffer(inputIndex);
                    int size = audioExtractor.readSampleData(inputBuffer, 0);
                    if (size > 0) {
                        if (audioExtractor.getSampleTime() > endTime2 && isNeedTailed) {
                            audioDecodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            closeExtractor = true;
                        } else {

                            //              Log.d("tag","video decode...");
                            audioDecodec.queueInputBuffer(inputIndex, 0, size, audioExtractor.getSampleTime(), audioExtractor.getSampleFlags());
                            audioExtractor.advance();
                        }
                    } else {
                        audioDecodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        closeExtractor = true;
                    }

                }
            }
            int outputIndex = audioDecodec.dequeueOutputBuffer(info, TIME_US);
            if (outputIndex >= 0) {
                ByteBuffer outputBuffer = audioDecodec.getOutputBuffer(outputIndex);

                int inputBufferEncodeIndex = audioEncodec.dequeueInputBuffer(TIME_US);
                if (inputBufferEncodeIndex >= 0) {
                    ByteBuffer inputEncodeBuffer = audioEncodec.getInputBuffer(inputBufferEncodeIndex);
                    if (info.size > 0) {
                        //              Log.d("tag","into video encode...");
                        inputEncodeBuffer.put(outputBuffer);
                        audioEncodec.queueInputBuffer(inputBufferEncodeIndex, 0, info.size, info.presentationTimeUs, info.flags);
                    } else {
                        audioEncodec.queueInputBuffer(inputBufferEncodeIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                }
                audioDecodec.releaseOutputBuffer(outputIndex, false);
            }

            int outputBufferIndex = audioEncodec.dequeueOutputBuffer(mediaInfo, TIME_US);
            switch (outputBufferIndex) {
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED: {
                    Log.d("tag", "audio format changed");
                    MediaFormat format = audioEncodec.getOutputFormat();
                    if (audioTrackIndex < 0)
                        audioTrackIndex = muxer.addTrack(format);
                    break;
                }
                default: {
                    ByteBuffer outputBuffer = audioEncodec.getOutputBuffer(outputBufferIndex);
                    if (!isMuxerStarted) {
                        startMuxer();
                    }
                    if (mediaInfo.size > 0 && mediaInfo.presentationTimeUs > 0 && isMuxerStarted ) {
                        info1 = new MediaCodec.BufferInfo();
                        info1.size = mediaInfo.size;
                        info1.offset = mediaInfo.offset;
                        info1.presentationTimeUs = time + mediaInfo.presentationTimeUs - beginTime;
                        Log.d("presentationTIMEUS",info1.presentationTimeUs + "");
                        info1.flags = mediaInfo.flags;
                        muxer.writeSampleData(audioTrackIndex, outputBuffer, info1);
                        Log.d("tag", "audio muxing");
                    }

                    audioEncodec.releaseOutputBuffer(outputBufferIndex, false);
                }
            }
            if ((mediaInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {

                Log.d("tag", "start release audio Encode");
                audioExtractor.unselectTrack(audioIndex);
                timeA = info1.presentationTimeUs;
                break;
            }
            while (pauseTranscode) {
                try {
                    Thread.sleep(10l);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private synchronized void startMuxer(){
        if ( 0 <= audioTrackIndex && 0<= videoTrackIndex && !isMuxerStarted){

            muxer.start();
            isMuxerStarted = true;
        }
    }
    /////////public //////////
    public boolean startTranscode(){

        inputThread.start();
        audioInputThread.start();
        return true;
    }






}
