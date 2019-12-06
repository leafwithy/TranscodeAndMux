package com.example.transcodeandmux;

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import com.example.transcodeandmux.utils.TailTimer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Created by weizheng.huang on 2019-10-18.
 */
public class TranscodeWrapperDemo3 {
    /////////private  //////
    private List<TailTimer> fileList;
    private MediaExtractor extractor,audioExtractor;

    private MediaCodec decodec,encodec,audioDecodec,audioEncodec;

    private MediaMuxer muxer;
    private String filePath = null;
    private AssetFileDescriptor srcFilePath = null;
    private AssetFileDescriptor srcFilePath2 = null;
    private int isMuxed = 0;
    private int audioTrackIndex = -1 ;
    private int videoTrackIndex = -1 ;
    private int videoIndex  = -1;
    private int audioIndex = -1;
    private boolean isMuxerStarted = false;
    private boolean pauseVideoTranscode = false;
    private boolean pauseAudioTranscode = false;
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
    private long startsTime1 = 0;
    private long startsTime2 = 0;
    private long audioTimeStamps = 0;
    private long videoTimeStamps = 0;



    public void setTailTimeVideo(long startTime,long endTime){
        long s = 0;
        long e = (long)durationTotal;
        startTime *= 1000000;
        endTime *= 1000000;
        this.startTime1 = startTime > s ? startTime  : s;
        this.endTime1 = (endTime < e) && (endTime != 0) ? endTime : e;
        this.isNeedTailed = (startTime > 0) || (endTime < e);
    }
    public void setTailTimeAudio(long startTime , long endTime){
        long s = 0;
        long e = (long)durationTotal;
        startTime *= 1000000;
        endTime *= 1000000;
        this.startTime2 = startTime > s ? startTime  : s;
        this.endTime2 = (endTime < e) && (endTime != 0) ? endTime : e;
        this.isNeedTailed = (startTime > 0) || (endTime < e);

    }
//
//    public synchronized  void setPauseTranscode(boolean TRUEORFALSE){
//        pauseTranscode = TRUEORFALSE;
//    }

    public void setAssignSize(Double rate) {
        if (rate > 100 && rate < 0) {
            throw new IllegalArgumentException("文件大小比例不合适");
        } else {
            assignSizeRate = Double.valueOf(new DecimalFormat(".0000").format(rate));
        }
    }


    public TranscodeWrapperDemo3(String filePath , List<TailTimer> fileList){
        this.filePath = filePath;
        this.fileList = fileList;
        initMediaMuxer();
        initVideoExtractor();
        initAudioExtractor();
        initVideoDecodec();
        initVideoEncodec();
        initAudioDecodec();
        initAudioEncodec();
    }

    private Thread inputThread = new Thread(new Runnable() {
        @Override
        public void run() {
            if (fileList != null){
                for (int i = 0; i < fileList.size(); i++){

                    initVideoDecodec();
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
        }
    });

    private Thread outputThread = new Thread(new Runnable() {
        @Override
        public void run() {
            if (fileList != null){
                for (int i = 0; i < fileList.size(); i++){
                    initVideoEncodec();
                    outputLoop();
                    Log.d("tag","执行到fileListVideo的"+i);

                }
            }else{
                outputLoop();
            }
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
                    initAudioDecodec();
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

        }
    });
    private Thread audioOutputThread = new Thread(new Runnable() {
        @Override
        public void run() {
            if (fileList != null){
                for (int i = 0; i < fileList.size(); i++){

                    initAudioEncodec();
                    audioOutputLoop();
                    Log.d("tag","执行到fileListVideo的"+i);


                }
            }else{
                audioOutputLoop();
            }
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
    public void initVideoExtractor(){
        if (fileList != null){
            srcFilePath = fileList.get(0).getSrcPath();
        }
        extractor = new MediaExtractor();
        try {
            extractor.setDataSource(srcFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
            }
        }
    }
    public void initAudioExtractor(){
        if (fileList != null){
            srcFilePath2 = fileList.get(0).getSrcPath();
        }
        audioExtractor = new MediaExtractor();
        try{
            audioExtractor.setDataSource(srcFilePath2);
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (int i = 0; i < audioExtractor.getTrackCount(); i++){
            MediaFormat format = audioExtractor.getTrackFormat(i);
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
            }
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

    public void initAudioDecodec(){
        try {
            audioDecodec = MediaCodec.createDecoderByType(audioFormatType);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat format = audioExtractor.getTrackFormat(audioIndex);
        audioDecodec.configure(format,null,null,0);
        audioDecodec.start();
    }
    public void initAudioEncodec(){
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
    public void initVideoDecodec(){
        try {
            decodec = MediaCodec.createDecoderByType(videoFormatType);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat format = extractor.getTrackFormat(videoIndex);
        decodec.configure(format,null,null,0);
        decodec.start();

    }
    public void initVideoEncodec(){
        MediaFormat videoFormat = MediaFormat.createVideoFormat(videoFormatType, width, height);

        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE,(int)(bitRate * assignSizeRate));
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1 );


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
                    videoTimeStamps = extractor.getSampleTime();
                    if (videoTimeStamps > endTime1 && isNeedTailed ) {
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

    private void outputLoop(){
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
                        startMuxer();
                    }
                    startTime = startsTime1;
                    break;
                }
                default: {
                    ByteBuffer outputBuffer = encodec.getOutputBuffer(outputBufferIndex);

                    if (!isMuxerStarted && mediaInfo.size >= 0 ){
                        try {
                            Thread.sleep(160l);
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

    private void audioInputLoop() {

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
                    audioTimeStamps = audioExtractor.getSampleTime();
                    if (audioTimeStamps > endTime2 && isNeedTailed) {
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

                    Thread.sleep(200l);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private void audioOutputLoop(){

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
                        startMuxer();
                    }
                    startTime = startsTime1;
                    break;
                }
                default: {
                    ByteBuffer outputBuffer = audioEncodec.getOutputBuffer(outputBufferIndex);
                    Log.v("timeStamps1",mediaInfo.presentationTimeUs + " size " + mediaInfo.size);

                    if (!isMuxerStarted && mediaInfo.size >= 0 ){
                        try {
                            Thread.sleep(200l);
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


    private synchronized void startMuxer(){
        if ( 0 <= audioTrackIndex && 0<= videoTrackIndex && !isMuxerStarted){

            muxer.start();
            isMuxerStarted = true;
        }
    }

    private synchronized int readSampleData(MediaExtractor extractor , ByteBuffer buffer){
        return extractor.readSampleData(buffer,0);
    }
    /////////public //////////
    public boolean startTranscode(){

        inputThread.start();
        outputThread.start();
        audioInputThread.start();
        audioOutputThread.start();
        return true;
    }








}
