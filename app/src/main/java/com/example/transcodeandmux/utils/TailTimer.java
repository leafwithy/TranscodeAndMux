package com.example.transcodeandmux.utils;

import android.content.res.AssetFileDescriptor;

import androidx.annotation.Nullable;

/**
 * Created by weizheng.huang on 2019-11-11.
 */
public class TailTimer {
    private long startTime;
    private long endTime;

    private AssetFileDescriptor srcPath;
    private AssetFileDescriptor srcPath2;

    public TailTimer(long startTime, long endTime ,AssetFileDescriptor srcPath , AssetFileDescriptor srcPath2 ){
        this.startTime = startTime;
        this.endTime = endTime;
        this.srcPath = srcPath;
        this.srcPath2 = srcPath2;
    }


    public long getStartTime() {
        return startTime;
    }


    public long getEndTime() {
        return endTime;
    }

    public AssetFileDescriptor getSrcPath() {
        return srcPath;
    }

    public AssetFileDescriptor getSrcPath2() {
        return srcPath2;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return super.equals(obj);
    }

    //    @Override
//    public boolean equals(@Nullable Object obj) {
//        TailTimer tailTimer = (TailTimer) obj;
//        return (this.startTime == tailTimer.startTime) &&
//                (this.endTime == tailTimer.endTime) &&
//                (this.srcPath == tailTimer.srcPath) &&
//                (this.srcPath2 == tailTimer.srcPath2);
//    }
}
