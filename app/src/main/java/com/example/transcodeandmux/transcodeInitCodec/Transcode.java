package com.example.transcodeandmux.transcodeInitCodec;

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
    /////////private  //////
    private int isMuxed = 0;
    private int videoIndex,videoTrackIndex,audioIndex,audioTrackIndex;
    private boolean isMuxerStarted = false;
    private double durationTotal = 0;
    private long TIME_US = 70000L;
    private long startTime1,startTime2,endTime1,endTime2;
    private boolean isNeedTailed = false;
    private long timeA = 0;
    private long timeV = 0;
    private long startsTime1 = 0;
//    private long startsTime2 = 0;

    /**
     * @param startTime  视频起始解码时间戳
     * @param endTime    视频终止解码时间戳
     */
    public  void setTailTimeVideo(long startTime,long endTime){
        long s = 0;
        long e = (long)durationTotal;
        startTime *= 1000000;
        endTime *= 1000000;
        this.startTime1 = startTime > s ? startTime  : s;
        this.endTime1 = (endTime < e) && (endTime != 0) ? endTime : e;
        this.isNeedTailed = (startTime > 0) || (endTime < e);
    }

    /**
     *
     * @param startTime   音频起始解码时间戳
     * @param endTime     音频终止解码时间戳
     */
    public  void setTailTimeAudio(long startTime , long endTime){
        long s = 0;
        long e = (long)durationTotal;
        startTime *= 1000000;
        endTime *= 1000000;
        this.startTime2 = startTime > s ? startTime  : s;
        this.endTime2 = (endTime < e) && (endTime != 0) ? endTime : e;
        this.isNeedTailed = (startTime > 0) || (endTime < e);

    }


    Transcode(int videoIndex ,int audioIndex ,int videoTrackIndex ,int audioTrackIndex , long durationTotal){
        this.videoIndex = videoIndex;
        this.audioIndex = audioIndex;
        this.videoTrackIndex = videoTrackIndex;
        this.audioTrackIndex = audioTrackIndex;
        this.durationTotal = durationTotal;
    }

    /**
     *  pauseAudio 控制音频比视频慢，保证音频线程每一段可以取到同一段视频的基准也就是第一帧关键帧
     */
    private boolean pauseAudio = true;


    public synchronized void releaseMuxer(MediaMuxer muxer){
        isMuxed++;
        if (isMuxed == 2){
            isMuxed++;
            muxer.stop();
            muxer.release();
            Log.v("tag","released muxer");
        }
    }

    /**
     * 裁剪视频：
     * seek的部分我纠结过，想来想去只有这么seek，即便丢帧也不会出现绿屏的情况，因为第一帧不是关键帧就会绿屏
     * 其次，因为视频的帧，不只有关键帧，还有前向、双向预测帧，所以不能先prev_sync再一个个advance读下一个帧
     * close_sync的模式是不确定的，要么前要么后，或许直接使用next_sync更好点
     * readEOS出现的原因，我debug的时候，发现进入到endTime那里的次数超出了一次，差不多会有三次进入，这是没必要的。
     * 转码：
     * 难度在理解解码的outputBuffer与编码的inputBuffer的关系上，一开始我还傻傻的用队列去存，后来发现没这必要，
     * 直接塞到编码器无疑效率更高。
     *
     */
    public void inputLoop(MediaExtractor extractor , MediaCodec decodec ,MediaCodec encodec) {
        //////////video decode///////////////
        extractor.selectTrack(videoIndex);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        startsTime1 = extractor.getSampleTime();
        if (isNeedTailed) {
            extractor.seekTo(startTime1, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            if (extractor.getSampleTime() < startTime1) {
                extractor.seekTo(startTime1,MediaExtractor.SEEK_TO_NEXT_SYNC);
            }
            startsTime1 = extractor.getSampleTime();

        }
        boolean readEOS = false;
        while (true) {
            if (!readEOS) {
                int inputIndex = decodec.dequeueInputBuffer(TIME_US);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = decodec.getInputBuffer(inputIndex);
                    assert inputBuffer != null;
                    int size = readSampleData(extractor, inputBuffer);
                    if (size > 0) {
                        if (extractor.getSampleTime() > endTime1 && isNeedTailed) {
                            decodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            readEOS = true;
                        } else {

                            decodec.queueInputBuffer(inputIndex, 0, size, extractor.getSampleTime(), extractor.getSampleFlags());
                            extractor.advance();
                        }
                    } else {
                        decodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        readEOS = true;
                    }
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

    /**
     * 裁剪：
     * output_format_changed这里注意以下，第一帧关键帧会给出编码器出来的正确的格式信息,这个才是muxer需要的
     * 有人会看到media.presentationTimeUs这里为什么要大于等于statrtTime
     * 这里我在音频那里同样写了，就是为了保证音频和视频的起始时间戳相同，这样只要解码编码过程正确，DST无误，那么音视频就必定同步。
     * info1为什么要有的原因也是为了每一段的播放最终时间戳作为上一段的起始播放时间戳
     *
     */
    public  void outputLoop(MediaCodec encodec , MediaMuxer muxer){
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
                    pauseAudio = false;
                    break;
                }
                default: {

                    ByteBuffer outputBuffer = encodec.getOutputBuffer(outputBufferIndex);
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

    /**
     * 裁剪：
     * 以视频时间戳为基准，直接使用视频的起始时间戳seek
     * 其余同视频，最后的sleep是为了保证muxer启动前减少丢帧，因为明显两个线程同时跑，format_changed必定不同时
     * 总会有一个线程先跑完那段，然后开始转码，所以会丢帧
     * 而在后续的编码里，也有一段sleep操作减少丢帧，效果是显著的
     *
     */
    public  void audioInputLoop(MediaExtractor audioExtractor , MediaCodec audioDecodec , MediaCodec audioEncodec){

        audioExtractor.selectTrack(audioIndex);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
//        startsTime2 = audioExtractor.getSampleTime();
//        if (isNeedTailed) {
//            audioExtractor.seekTo(startTime2, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
//            if (audioExtractor.getSampleTime() < startTime2){
//                audioExtractor.seekTo(startTime2,MediaExtractor.SEEK_TO_NEXT_SYNC);
//            }
//            startsTime2 = audioExtractor.getSampleTime();
//        }
        boolean readEOS = false;
        while(pauseAudio){
            try {
                Thread.sleep(0L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (isNeedTailed){
            audioExtractor.seekTo(startsTime1,MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            if (audioExtractor.getSampleTime() < startTime2){
                audioExtractor.seekTo(startsTime1,MediaExtractor.SEEK_TO_NEXT_SYNC);
            }
        }
        while (true) {
            if (!readEOS) {
                int inputIndex = audioDecodec.dequeueInputBuffer(TIME_US);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = audioDecodec.getInputBuffer(inputIndex);
                    assert inputBuffer != null;
                    int size = readSampleData(audioExtractor, inputBuffer);
                    if (size >= 0) {
                        if (audioExtractor.getSampleTime() > endTime2 && isNeedTailed) {
                            audioDecodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            readEOS = true;
                        } else {

                            //              Log.d("tag","video decode...");

                            audioDecodec.queueInputBuffer(inputIndex, 0, size, audioExtractor.getSampleTime(), audioExtractor.getSampleFlags());
                            audioExtractor.advance();
                        }
                    } else {
                        audioDecodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        readEOS = true;
                    }

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
                pauseAudio = true;
                break;
            }
            if(!isMuxerStarted){
                try {

                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    /**
     * 同视频，取准起始解码时间戳
     */
    public void audioOutputLoop(MediaCodec audioEncodec ,MediaMuxer muxer){

        MediaCodec.BufferInfo mediaInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo info1 = null;
        final long time  = timeA;

        while(pauseAudio){
            try {
                Thread.sleep(0L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
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
                    break;
                }
                default: {

                    ByteBuffer outputBuffer = audioEncodec.getOutputBuffer(outputBufferIndex);
                    Log.v("timeStamps1",mediaInfo.presentationTimeUs + " size " + mediaInfo.size + " startTime " + startTime);
                    // 现在情况是，视频的起始时间戳变了，音频无法在起始时间戳确定时准确捕捉。
                    // 容易出现的是，startsTime1 变大或变小，只能是音频在视频后边才能确认正确。
                    // 否则的话时间戳混乱。
                    // 要求达到：当视频时间戳改变时，每一段的音频第一帧比视频第一帧慢，就能够捕捉正确。
                    if (!isMuxerStarted && mediaInfo.size >= 0){
                        try {
                            Thread.sleep(100L);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        startTime = startsTime1;
                    }
                    if (mediaInfo.size >= 0  && mediaInfo.presentationTimeUs >= startTime && isMuxerStarted) {
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

                    audioEncodec.releaseOutputBuffer(outputBufferIndex, false);
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
