//package com.example.transcodeandmux.transcodeAndMuxUtils;
//
//import android.media.MediaCodec;
//import android.media.MediaExtractor;
//import android.media.MediaFormat;
//import android.media.MediaMuxer;
//import android.util.Log;
//
//import androidx.annotation.Nullable;
//
//import com.example.transcodeandmux.utils.TailTimer;
//
//import java.nio.ByteBuffer;
//import java.util.List;
//
///**
// * Created by weizheng.huang on 2019-11-13.
// */
//public class TranscodeThread extends Thread {
//    private List<TailTimer> tailTimers;
//    private MediaExtractor extractor;
//    private MediaCodec encodec,decodec;
//
//    private static MediaMuxer muxer;
//    private String MIME;
//    private long presentationTime;
//    private long startTime;
//    private long endTime ;
//    private long TIME_US;
//    private long durationTotal;
//    private int formatIndex;
//    private static int videoTrackIndex = -1;
//    private static int audioTrackIndex = -1;
//    private static boolean isMuxerStarted = false;
//    private boolean isNeedTailed = false;
//    private static int isMuxed = 0;
//
//
//    public TranscodeThread(MediaExtractor extractor, MediaCodec encodec, MediaCodec decodec, MediaMuxer muxer, String MIME, long TIME_US, long durationTotal,@Nullable List<TailTimer> tailTimerList) {
//        this.extractor = extractor;
//        this.encodec = encodec;
//        this.decodec = decodec;
//        this.muxer = muxer;
//        this.MIME = MIME;
//        this.TIME_US = TIME_US;
//        this.formatIndex = extractor.getSampleTrackIndex();
//        this.tailTimers = tailTimerList;
//        this.durationTotal = durationTotal;
//    }
//
//    public void setTailTime(long startTime , long endTime){
//        long s = 0;
//        long e = durationTotal;
//        startTime *= 1000000;
//        endTime *= 1000000;
//        this.startTime = startTime > s ? startTime : s;
//        this.endTime = (endTime < e) && (endTime != 0) ? endTime : e;
//        this.isNeedTailed = (startTime > 0) || (endTime < e);
//    }
//
//    @Override
//    public void run() {
//        for (int i = 0; i < tailTimers.size(); i++) {
//            setTailTime(startTime,endTime);
//            transcodeLoop();
//        }
//        extractor.release();
//        decodec.stop();
//        decodec.release();
//        encodec.stop();
//        encodec.release();
//        Log.v("tag","released codec");
//        isMuxed++;
//        releaseMuxer();
//    }
//
//    private void transcodeLoop(){
//        extractor.selectTrack(formatIndex);
//        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
//        MediaCodec.BufferInfo mediaInfo = new MediaCodec.BufferInfo();
//        MediaCodec.BufferInfo info1 = null;
//        long beginTime = 0;
//        boolean closeExtractor = false;
//
//        if (isNeedTailed){
//            extractor.seekTo(startTime , MediaExtractor.SEEK_TO_CLOSEST_SYNC);
//            beginTime = extractor.getSampleTime();
//        }
//        while(!Thread.interrupted()){
//            if (!closeExtractor){
//                int inputIndex = decodec.dequeueInputBuffer(TIME_US);
//                if (inputIndex >= 0){
//                    ByteBuffer inputBuffer = decodec.getInputBuffer(inputIndex);
//                    int size = extractor.readSampleData(inputBuffer,0);
//                    if (size > 0){
//                        if (extractor.getSampleTime() > endTime && isNeedTailed){
//                            decodec.queueInputBuffer(inputIndex,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                            closeExtractor = true;
//                        }else{
//                            decodec.queueInputBuffer(inputIndex,0,size,extractor.getSampleTime(),extractor.getSampleFlags());
//                            extractor.advance();
//                        }
//                    }else{
//                        decodec.queueInputBuffer(inputIndex,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                        closeExtractor = true;
//                    }
//                }
//            }
//            int outputIndex = decodec.dequeueOutputBuffer(info , TIME_US);
//            if (outputIndex >= 0){
//                ByteBuffer outputBuffer = decodec.getOutputBuffer(outputIndex);
//
//                int inputBufferEncodeIndex = encodec.dequeueInputBuffer(TIME_US);
//                if (inputBufferEncodeIndex >= 0){
//                    ByteBuffer inputEncodeBuffer = encodec.getInputBuffer(inputBufferEncodeIndex);
//                    if (info.size > 0){
//                        inputEncodeBuffer.put(outputBuffer);
//                        encodec.queueInputBuffer(inputBufferEncodeIndex ,0 ,info.size , info.presentationTimeUs,info.flags);
//                    }else{
//                        encodec.queueInputBuffer(inputBufferEncodeIndex,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                    }
//                }
//                decodec.releaseOutputBuffer(outputIndex,false);
//            }
//
//            ////////////encode//////////
//            int outputBufferIndex = encodec.dequeueOutputBuffer(mediaInfo,TIME_US);
//            switch (outputBufferIndex){
//                case MediaCodec.INFO_TRY_AGAIN_LATER:{
//                    //  Log.d("tag","try again Later");
//                    break;
//                }
//                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
//                {
//                    Log.d("tag","format changed " +MIME);
//                    MediaFormat format = encodec.getOutputFormat();
//                    MIME = format.getString(MediaFormat.KEY_MIME);
//                    if (videoTrackIndex < 0 && MIME.startsWith("video"))
//                        videoTrackIndex = muxer.addTrack(format);
//                    if (audioTrackIndex < 0 && MIME.startsWith("audio"))
//                        audioTrackIndex = muxer.addTrack(format);
//
//                    break;
//                }
//                default:
//                {
//                    ByteBuffer outputBuffer = encodec.getOutputBuffer(outputBufferIndex);
//                    if (!isMuxerStarted){
//                        startMuxer();
//                    }
//                    if (mediaInfo.size >= 0 && isMuxerStarted){
//                        info1 = new MediaCodec.BufferInfo();
//                        info1.size = mediaInfo.size;
//                        info1.offset = mediaInfo.offset;
//                        info1.flags = mediaInfo.flags;
//                        info1.presentationTimeUs = mediaInfo.presentationTimeUs + presentationTime  - beginTime;
//                        if (MIME.startsWith("video")) {
//                            muxer.writeSampleData(videoTrackIndex, outputBuffer, info1);
//                        }else{
//                            muxer.writeSampleData(audioTrackIndex, outputBuffer, info1);
//                        }
//                        Log.d("tag",MIME + "muxing");
//                    }
//                    encodec.releaseOutputBuffer(outputBufferIndex , false);
//                }
//            }
//            if ((mediaInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0){
//                Log.d("tag","start release" +  MIME + "encode");
//                extractor.unselectTrack(formatIndex);
//                presentationTime = info1.presentationTimeUs;
//            }
//        }
//    }
//
//
//    private static synchronized void startMuxer(){
//        if ((0 <= audioTrackIndex) && (0 <= videoTrackIndex) && (!isMuxerStarted)){
//            muxer.start();
//            isMuxerStarted = true;
//        }
//    }
//
//    private static synchronized void releaseMuxer(){
//        if (isMuxed == 2){
//            muxer.stop();
//            muxer.release();
//            isMuxed = 0;
//            muxer = null;
//            Log.v("tag","released muxer");
//        }
//    }
//
//
//}
