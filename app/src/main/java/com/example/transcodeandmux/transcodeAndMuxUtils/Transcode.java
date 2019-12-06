//package com.example.transcodeandmux.transcodeAndMuxUtils;
//
//import android.media.MediaCodec;
//import android.media.MediaExtractor;
//import android.media.MediaMuxer;
//
//import com.example.transcodeandmux.utils.TailTimer;
//
//import java.util.List;
//
///**
// * Created by weizheng.huang on 2019-11-13.
// */
//public class Transcode {
//    private TranscodeThread videoTranscodeThread;
//    private TranscodeThread audioTranscodeThread;
//
//    void setVideoTranscodeThread(MediaExtractor extractor, MediaCodec decodec ,
//                                 MediaCodec encodec , MediaMuxer muxer ,
//                                 String MIME  , long TIME_US , long durationTatol , List<TailTimer> tailTimerList){
//        videoTranscodeThread = new TranscodeThread(extractor, encodec, decodec, muxer, MIME, TIME_US, durationTatol,tailTimerList);
//    }
//    void setAudioTranscodeThread( MediaExtractor extractor,  MediaCodec decodec ,
//                                            MediaCodec encodec ,  MediaMuxer muxer ,
//                                           String MIME  , long TIME_US ,long durationTotal, List<TailTimer> tailTimerList){
//        audioTranscodeThread = new TranscodeThread(extractor, encodec, decodec, muxer, MIME, TIME_US, durationTotal,tailTimerList);
//    }
//
//
//    public void start(){
//        videoTranscodeThread.start();
//        audioTranscodeThread.start();
//    }
//}
