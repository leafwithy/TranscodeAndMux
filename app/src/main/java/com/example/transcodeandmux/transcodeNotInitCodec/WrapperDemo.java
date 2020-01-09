package com.example.transcodeandmux.transcodeNotInitCodec;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import com.example.transcodeandmux.utils.TailTimer;

import java.io.IOException;
import java.util.List;


public class WrapperDemo {

    private static final String TAG = WrapperDemo.class.getName();

    private List<TailTimer> fileList;
    private String dstFilePath;

    private MediaExtractor audioExtractor, videoExtractor;
    private int audioTrackIndex = -1;
    private int videoTrackIndex = -1;
    private int videoIndex = -1;
    private int audioIndex = -1;
    private MediaCodec audioDecoder, audioEncoder;
    private MediaCodec videoDecoder, videoEncoder;
    private MediaMuxer mediaMuxer;
    private Thread inputLoop , outputLoop ;

    private int width,height,bitRate,frameRate;
    private int iframeInterval = 1;
    private int sampleRate,audioBitRate,channelCount;
    private String audioFormatType,videoFormatType;
    private boolean pauseTime = false;

    private Transcode transcode ;

    public WrapperDemo(String dstFilePath , List<TailTimer> fileList) throws IOException{
        this.dstFilePath = dstFilePath;
        this.fileList = fileList;
        initExtractor();
        initAudioCodec();
        initVideoCodec();
        initMuxer();
        initTranscode();


        inputLoop = new Thread(new Runnable() {
            @Override
            public void run() {
                transcode.inputLoopInterface(videoExtractor, audioExtractor , videoDecoder , audioDecoder);
                while(!pauseTime){
                    try {
                        Thread.sleep(0L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                audioExtractor.release();
                videoExtractor.release();
                Log.v(TAG , "extractor released");
                audioDecoder.stop();
                audioDecoder.release();
                Log.v(TAG,"released audioDecode");
                videoDecoder.stop();
                videoDecoder.release();
                Log.v(TAG,"released videoDecode");


            }
        });

        outputLoop = new Thread(new Runnable() {
            @Override
            public void run() {
                transcode.outputLoopInterface(audioDecoder, videoDecoder, audioEncoder , videoEncoder , mediaMuxer);
                audioEncoder.stop();
                videoEncoder.stop();
                audioEncoder.release();
                videoEncoder.release();
                mediaMuxer.stop();
                mediaMuxer.release();
                Log.v(TAG,"released mediaMuxer");
            }
        });
    }

    public int startTranscode() {
        inputLoop.start();
        outputLoop.start();
        return 0;
    }

    public int stop() {
        return 0;
    }

    private int initTranscode(){
        if (transcode == null){
                transcode = new Transcode(fileList,audioIndex , audioTrackIndex , videoIndex , videoTrackIndex);
        }
        return 0;
    }

    private int initExtractor() throws IOException{
        if (videoExtractor == null){
            videoExtractor = new MediaExtractor();
        }
        if (audioExtractor == null){
            audioExtractor = new MediaExtractor();
        }
        videoExtractor.setDataSource(fileList.get(0).getSrcPath());
        audioExtractor.setDataSource(fileList.get(0).getSrcPath());
        for (int i = 0; i < videoExtractor.getTrackCount(); i++){
            MediaFormat format = videoExtractor.getTrackFormat(i);
            String formatType = format.getString(MediaFormat.KEY_MIME);
            if (formatType.startsWith("audio")){
                audioIndex = i;
                audioFormatType = formatType;
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
//                audioBitRate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                audioBitRate = sampleRate * channelCount * 4;
                audioDecoder = MediaCodec.createDecoderByType(formatType);

                audioDecoder.configure(format,null,null,0);
                audioDecoder.start();
                continue;
            }
            if (formatType.startsWith("video")){
                videoIndex = i;
                videoFormatType = formatType;
                width = format.getInteger(MediaFormat.KEY_WIDTH);
                height = format.getInteger(MediaFormat.KEY_HEIGHT);
                frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
//                bitRate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                bitRate = width * height * 4;
                videoDecoder = MediaCodec.createDecoderByType(formatType);
                videoDecoder.configure(format,null,null,0);
                videoDecoder.start();
            }
        }
        return 1;
    }

    private int initAudioCodec() throws IOException {
        if (audioEncoder == null)
            audioEncoder = MediaCodec.createEncoderByType(audioFormatType);

        MediaFormat audioEncFormat = MediaFormat.createAudioFormat(audioFormatType, sampleRate , channelCount);
        audioEncFormat.setInteger(MediaFormat.KEY_BIT_RATE,audioBitRate);

        audioEncoder.configure(audioEncFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioEncoder.start();
        return 1;
    }

    private int initVideoCodec() throws IOException{
        if (videoEncoder == null)
            videoEncoder = MediaCodec.createEncoderByType(videoFormatType);

        MediaFormat videoEncFormat = MediaFormat.createVideoFormat(videoFormatType, width , height);
        videoEncFormat.setInteger(MediaFormat.KEY_BIT_RATE,bitRate);
        videoEncFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        videoEncFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,iframeInterval);
        videoEncFormat.setInteger(MediaFormat.KEY_FRAME_RATE,frameRate);

        videoEncoder.configure(videoEncFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        videoEncoder.start();
        return 1;
    }

    private int initMuxer() throws IOException{
        if (mediaMuxer == null)
            mediaMuxer = new MediaMuxer(dstFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        return 1;
    }

}
