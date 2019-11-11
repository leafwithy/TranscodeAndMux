package com.example.transcodeandmux;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

/**
 * Created by weizheng.huang on 2019-11-11.
 */
public class MainActivity extends Activity {
    private TranscodeWrapperDemo transcodeWrapperDemo;
    private AssetFileDescriptor srcPath ;
    private AssetFileDescriptor srcPath2 ;
    private String dstPath = Environment.getExternalStorageDirectory().getAbsolutePath() +"/shape1.mp4";
    private Button transcodeBtn;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout);
        transcodeBtn = findViewById(R.id.transcodeBtn);
        srcPath = getResources().openRawResourceFd(R.raw.shape_of_my_heart);
        srcPath2 = getResources().openRawResourceFd(R.raw.shape_of_my_heart2);

        transcodeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (verifyPermission(MainActivity.this)){
                    initTranscode();
                    transcodeWrapperDemo.startTranscode();
                }

            }
        });
    }

    private void initTranscode(){
        transcodeWrapperDemo = new TranscodeWrapperDemo(dstPath,srcPath,srcPath2);
        transcodeWrapperDemo.setAssignSize(1.0);
        transcodeWrapperDemo.init();

    }

    private boolean verifyPermission(Activity activity){
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, 1);
        }
        return permission == PackageManager.PERMISSION_GRANTED;

    }
}
