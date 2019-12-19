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
    private int audioTrackIndex = -1 ;
    private int videoTrackIndex = -1 ;
    private int videoIndex  = -1;
    private int audioIndex = -1;
    private double assignSizeRate = 1.0;
    private long durationTotal = 0;

    private String videoFormatType = null;
    private String audioFormatType = null;
    private int width = -1;
    private int height = -1;
    private int frameRate = -1 ;
    private int bitRate = -1;
    private int audioBitRate = -1 ;
    private int sampleRate = -1;
    private int channelCount = -1;
//    private long startsTime2 = 0;

    /**
     * @param startTime  视频起始解码时间戳
     * @param endTime    视频终止解码时间戳
     */
    private  void setTailTimeVideo(long startTime,long endTime){
        assert transcode != null;
        transcode.setTailTimeVideo(startTime,endTime);
    }

    /**
     *
     * @param startTime   音频起始解码时间戳
     * @param endTime     音频终止解码时间戳
     */
    private  void setTailTimeAudio(long startTime , long endTime){
        assert transcode != null;
        transcode.setTailTimeAudio(startTime,endTime);
    }


    WrapperDemo(String filePath , List<TailTimer> fileList){
        this.filePath = filePath;
        this.fileList = fileList;
        initMediaMuxer();
        initVideoExtractor();
        initAudioExtractor();
        initVideoDecodec();
        initVideoEncodec();
        initAudioDecodec();
        initAudioEncodec();
        initTranscode();
    }

    /**
     * 以下总是先初始化codec再执行操作的原因是因为，后一段使用codec的时候，因为第一段接受了EOS指令导致codec停止工作，所以需要重置
     * 而我这里没有重置，而是新建一个
     * 替代方案：
     *      codec.reset()
     *      codec.configure(format, null, null, 0|MediaCodec.CONFIGURE_FLAG_ENCODE)
     *      codec.start()
     *  以上方案是根据codec状态得到的解决方案，
     *  下面是解释：
     *      codec(uninitiated) -> codec(configured)  -> codec(flushed) -> codec(running) -> codec(EOS) -> codec(released)
     *      这是完整的状态，其中存在的闭环:
     *      codec(running) -> codec(flushed)
     *      codec(EOS) -> codec(flushed)
     *      codec(EOS) -> codec(uninitiated) -> codec(configured) -> codec(flushed) -> codec(running) -> codec(EOS)
     *      有几个状态转换需要调用的方法，就说比较陌生的地方，比如codec(flushed) 调用了dequeueInputBuffer() 就会进入到codec(running)
     *      那running 或 EOS 回到 flushed 就调用flush()即可，不过这里我试过，没用。
     *      所以我就用了第三个闭环，EOS 回到 uninitiated 就调用了reset()，然后就是重置codec正常使用。
     */

    private Thread inputThread = new Thread(new Runnable() {
        @Override
        public void run() {
            if (fileList != null){
                for (int i = 0; i < fileList.size(); i++){
                    initVideoDecodec();
                    TailTimer tailTimer = fileList.get(i);
                    setTailTimeVideo(tailTimer.getStartTime(),tailTimer.getEndTime());
                    transcode.inputLoop(extractor,decodec,encodec);
                    Log.d("tag","执行到fileListVideo的"+i);
                }
            }else{
                transcode.inputLoop(extractor,decodec,encodec);
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
                    transcode.outputLoop(encodec,muxer);
                    Log.d("tag","执行到fileListVideo的"+i);

                }
            }else{
                transcode.outputLoop(encodec,muxer);
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

                    initAudioDecodec();
                    TailTimer tailTimer = fileList.get(i);
                    setTailTimeAudio(tailTimer.getStartTime(),tailTimer.getEndTime());
                    transcode.audioInputLoop(audioExtractor,audioDecodec,audioEncodec);
                    Log.d("tag","执行到fileListAudio的"+i);

                }
            }else{
                transcode.audioInputLoop(audioExtractor,audioDecodec,audioEncodec);
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

                    transcode.audioOutputLoop(audioEncodec,muxer);
                    Log.d("tag","执行到fileListAudio的"+i);


                }
            }else{
                transcode.audioOutputLoop(audioEncodec,muxer);
            }
            audioEncodec.stop();
            audioEncodec.release();
            Log.v("tag","released audio encode");
            transcode.releaseMuxer(muxer);
        }
    });
    private  void initTranscode(){
        if (transcode == null){
            transcode = new Transcode(videoIndex,audioIndex,videoTrackIndex,audioTrackIndex,durationTotal);
        }
    }
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
                audioFormatType = formatType;
                audioBitRate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            }
        }
    }



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

    /**
     * bitrate必须有
     */
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

    /**
     * bitrate、colorformat、iframeInterval、frameRate必须有
     */
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
