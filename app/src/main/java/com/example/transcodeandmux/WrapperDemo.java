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
import java.util.List;

/**
 * Created by weizheng.huang on 2019-10-18.
 */
public class WrapperDemo {
    /////////private  //////
    private List<TailTimer> fileList;
    private MediaExtractor extractor,audioExtractor;
    private MediaCodec decodec,encodec,audioDecodec,audioEncodec;
    private Transcode transcode;
    private MediaMuxer muxer;
    private String filePath;
    private AssetFileDescriptor srcFilePath = null;
    private AssetFileDescriptor srcFilePath2 = null;
    private int videoIndex  = -1;
    private int audioIndex = -1;
    private double assignSizeRate = 1.0;
    private double durationTotal = 0;


    WrapperDemo(String filePath , List<TailTimer> fileList){
        this.filePath = filePath;
        this.fileList = fileList;
        initVideoExtractor();
        initAudioExtractor();
        initMediaMuxer();
        initTranscode();

    }
    private Thread inputThread = new Thread(new Runnable() {
        @Override
        public void run() {
            if (fileList != null){
                for (int i = 0; i < fileList.size(); i++){
                    initVideoExtractor();
                    initVideoDecodec();
                    TailTimer tailTimer = fileList.get(i);
                    transcode.setTailTimeVideo(tailTimer.getStartTime(),tailTimer.getEndTime());
                    transcode.inputLoop(extractor  , decodec , encodec);
                    Log.d("tag","执行到fileListVideo的"+i);

                }
            }else{
                transcode.inputLoop(extractor , decodec ,encodec);
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
                    transcode.outputLoop(encodec , muxer);
                    Log.d("tag","执行到fileListVideo的"+i);

                }
            }else{
                transcode.outputLoop(encodec , muxer);
            }
            encodec.stop();
            encodec.release();
            Log.v("tag", "released encode");
            transcode.releaseMuxer(muxer);
        }
    });
    private Thread audioInputThread = new Thread(new Runnable() {
        @Override
        public void run() {
            if (fileList != null){
                for (int i = 0; i < fileList.size(); i++){
                    initAudioExtractor();
                    initAudioDecodec();
                    TailTimer tailTimer = fileList.get(i);
                    transcode.setTailTimeAudio(tailTimer.getStartTime(),tailTimer.getEndTime());
                    ////考虑在内部初始化

                    transcode.audioInputLoop(audioExtractor , audioDecodec , audioEncodec);
                    Log.d("tag","执行到fileListAudio的"+i);

                }
            }else{
                transcode.audioInputLoop(audioExtractor , audioDecodec , audioEncodec);
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
                    transcode.audioOutputLoop(audioEncodec , muxer);
                    Log.d("tag","执行到fileListVideo的"+i);


                }
            }else{
                transcode.audioOutputLoop(audioEncodec , muxer);
            }
            audioEncodec.stop();
            audioEncodec.release();
            Log.v("tag","released audio encode");
            transcode.releaseMuxer(muxer);
        }
    });

    private  void initVideoExtractor(){
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
            assert formatType != null;
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
    private  void initAudioExtractor(){
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
            assert formatType != null;
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
    private  void initTranscode(){
        if (transcode == null){
            transcode = new Transcode(audioIndex , videoIndex , durationTotal);
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

    private  void initAudioDecodec(){
        try {
            audioDecodec = MediaCodec.createDecoderByType(audioFormatType);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat format = audioExtractor.getTrackFormat(audioIndex);
        audioDecodec.configure(format,null,null,0);
        audioDecodec.start();
    }
    private  void initAudioEncodec(){
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
    private void initVideoDecodec(){
        try {
            decodec = MediaCodec.createDecoderByType(videoFormatType);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat format = extractor.getTrackFormat(videoIndex);
        decodec.configure(format,null,null,0);
        decodec.start();

    }
    private void initVideoEncodec(){
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
    private void initMediaMuxer(){

        try {
            muxer = new MediaMuxer(filePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /////////public //////////
    void startTranscode(){

        inputThread.start();
        outputThread.start();
        audioInputThread.start();
        audioOutputThread.start();
    }








}
