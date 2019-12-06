//package com.example.transcodeandmux;
//
//import android.content.res.AssetFileDescriptor;
//import android.media.MediaCodec;
//import android.media.MediaCodecInfo;
//import android.media.MediaExtractor;
//import android.media.MediaFormat;
//import android.media.MediaMuxer;
//
//import com.example.transcodeandmux.transcodeAndMuxUtils.Transcode;
//import com.example.transcodeandmux.transcodeAndMuxUtils.TranscodeBuilder;
//import com.example.transcodeandmux.utils.TailTimer;
//
//import java.io.IOException;
//import java.text.DecimalFormat;
//import java.util.List;
//
///**
// * Created by weizheng.huang on 2019-10-18.
// */
//public class TranscodeWrapperDemo2 {
//    /////////private  //////
//    private List<TailTimer> fileList;
//    private TranscodeBuilder transcodeBuilder;
//    private Transcode transcode;
//    private MediaExtractor extractor,audioExtractor;
//    private MediaCodec decodec,encodec,audioDecodec,audioEncodec;
//    private MediaMuxer muxer;
//    private String filePath = null;
//    private AssetFileDescriptor srcFilePath = null;
//    private AssetFileDescriptor srcFilePath2 = null;
//
//    private String videoFormatType = null;
//    private String audioFormatType = null;
//    private double assignSizeRate = 1.0;
//    private long TIME_US = 50000l;
//    private long durationTotal;
//    private int videoIndex;
//    private int audioIndex;
//    private int width = -1;
//    private int height = -1;
//    private int frameRate = -1 ;
//    private int bitRate = -1;
//    private int audioBitRate = -1 ;
//    private int sampleRate = -1;
//    private int channelCount = -1;
//
//
//    public void setAssignSize(Double rate) {
//        if (rate > 100 && rate < 0) {
//            throw new IllegalArgumentException("文件大小比例不合适");
//        } else {
//            assignSizeRate = Double.valueOf(new DecimalFormat(".0000").format(rate));
//        }
//    }
//
//
//    public TranscodeWrapperDemo2(String filePath , List<TailTimer> fileList){
//        this.filePath = filePath;
//        this.fileList = fileList;
//    }
//
//
//    private void initVideoExtractor(){
//        if (fileList != null){
//            srcFilePath = fileList.get(0).getSrcPath();
//        }
//        extractor = new MediaExtractor();
//        try{
//            extractor.setDataSource(srcFilePath);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//    private void initAudioExtractor(){
//        if (fileList != null){
//            srcFilePath2 = fileList.get(0).getSrcPath2();
//        }
//        audioExtractor = new MediaExtractor();
//        try{
//            audioExtractor.setDataSource(srcFilePath2);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//    private void initVideoCodec(){
//        for (int i = 0; i < extractor.getTrackCount(); i++){
//            MediaFormat format = extractor.getTrackFormat(i);
//            String formatType = format.getString(MediaFormat.KEY_MIME);
//            if (formatType.startsWith("video")){
//                videoIndex = i;
//                try {
//                    decodec = MediaCodec.createDecoderByType(formatType);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                videoFormatType = formatType;
//                frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
//                bitRate = format.getInteger(MediaFormat.KEY_BIT_RATE);
//                width = format.getInteger(MediaFormat.KEY_WIDTH);
//                height = format.getInteger(MediaFormat.KEY_HEIGHT);
//                durationTotal = format.getLong(MediaFormat.KEY_DURATION);
//                decodec.configure(format,null,null,0);
//                decodec.start();
//            }
//        }
//        MediaFormat videoFormat = MediaFormat.createVideoFormat(videoFormatType, width, height);
//
//        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
//        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE,(int)(bitRate * assignSizeRate));
//        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
//        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,frameRate / 2 );
//
//
//        try {
//            encodec = MediaCodec.createEncoderByType(videoFormatType);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        encodec.configure(videoFormat, null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
//        encodec.start();
//    }
//    private void initAudioCodec(){
//        for (int i = 0; i < extractor.getTrackCount(); i++){
//            MediaFormat format = extractor.getTrackFormat(i);
//            String formatType = format.getString(MediaFormat.KEY_MIME);
//            if (formatType.startsWith("audio")){
//                audioIndex = i;
//                try {
//                    audioDecodec = MediaCodec.createDecoderByType(formatType);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                audioFormatType = formatType;
//                audioBitRate = format.getInteger(MediaFormat.KEY_BIT_RATE);
//                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
//                channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
//                audioDecodec.configure(format,null,null,0);
//                audioDecodec.start();
//            }
//        }
//        MediaFormat audioFormat = MediaFormat.createAudioFormat(audioFormatType, sampleRate, channelCount);
//        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE,(int)(audioBitRate * assignSizeRate));
//        try {
//            audioEncodec = MediaCodec.createEncoderByType(audioFormatType);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        audioEncodec.configure(audioFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
//        audioEncodec.start();
//    }
//    private void initVideoThread(){
//        initVideoExtractor();
//        initVideoCodec();
//        transcodeBuilder.buildVideoTranscodeThread(extractor,decodec,encodec,muxer,videoFormatType,TIME_US,videoIndex);
//    }
//    private void initAudioThread(){
//        initAudioExtractor();
//        initAudioCodec();
//        transcodeBuilder.buildAudioTranscodeThread(audioExtractor,audioDecodec,audioEncodec,muxer,audioFormatType,TIME_US,audioIndex);
//    }
//
//    public void initMediaMuxer(){
//
//        try {
//            muxer = new MediaMuxer(filePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//    /////////public //////////
//    public boolean startTranscode(){
//        transcode.start();
//        return true;
//    }
//
//    public void initTranscode(){
//        transcodeBuilder = new TranscodeBuilder();
//        initVideoThread();
//        initAudioThread();
//        transcode = transcodeBuilder.build();
//    }
//
//
//
//
//
//
//}
