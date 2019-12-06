//package com.example.transcodeandmux.transcodeAndMuxUtils;
//
//import android.media.MediaCodec;
//import android.media.MediaExtractor;
//import android.media.MediaMuxer;
//
//import androidx.annotation.Nullable;
//
///**
// * Created by weizheng.huang on 2019-11-13.
// */
//public class TranscodeBuilder {
//    private Transcode transcode = new Transcode();
//
//    public void buildVideoTranscodeThread(@Nullable MediaExtractor extractor, @Nullable MediaCodec decodec ,
//                                          @Nullable MediaCodec encodec , @Nullable MediaMuxer muxer ,
//                                          String MIME  , long TIME_US , int formatIndex){
//        transcode.setVideoTranscodeThread(extractor, decodec, encodec, muxer, MIME, TIME_US, formatIndex);
//    }
//    public void buildAudioTranscodeThread(@Nullable MediaExtractor extractor, @Nullable MediaCodec decodec ,
//                                          @Nullable MediaCodec encodec , @Nullable MediaMuxer muxer ,
//                                          String MIME  , long TIME_US , int formatIndex){
//        transcode.setAudioTranscodeThread(extractor, decodec, encodec, muxer, MIME, TIME_US, formatIndex);
//    }
//
//    public Transcode build(){
//        return transcode;
//    }
//}
