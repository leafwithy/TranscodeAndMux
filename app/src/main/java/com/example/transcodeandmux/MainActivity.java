package com.example.transcodeandmux;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.example.transcodeandmux.utils.TailTimer;
import com.example.transcodeandmux.utils.TailTimerEditText;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by weizheng.huang on 2019-11-11.
 */
public class MainActivity extends Activity {
    private TransCodeWrapper1 transcode;
//    private WrapperDemo transcode ;
    private AssetFileDescriptor srcPath ;
    private AssetFileDescriptor srcPath2 ;
    private String dstPath = Environment.getExternalStorageDirectory().getAbsolutePath() +"/shape1.mp4";
    private Button transcodeBtn;
    private EditText countEditText;
    private LinearLayout iContentView ;
    private Button refreshBtn;
    private Button deleteBtn;
    private List<TailTimerEditText> tailTimerEditTextList = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout);
        transcodeBtn = findViewById(R.id.transcodeBtn);
        srcPath = getResources().openRawResourceFd(R.raw.videoplayback);
        srcPath2 = getResources().openRawResourceFd(R.raw.shape_of_my_heart2);
        countEditText = findViewById(R.id.countOfTailTime);
        iContentView = findViewById(R.id.contentView);
        refreshBtn = findViewById(R.id.refreshBtn);
        deleteBtn = findViewById(R.id.deleteBtn);
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addTailTimer();
            }
        });
        deleteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteTailTimer();
            }
        });

        transcodeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (verifyPermission(MainActivity.this)){
                    initTranscode();
                    transcode.startTranscode();
                }

            }
        });
    }
    private void addTailTimer(){
        int count = Integer.valueOf(countEditText.getText().toString());
        for (int i = 0; i < count; i++){
            TailTimerEditText text = new TailTimerEditText(this);
            text.secondEdit.setText("0");
            text.minuteEdit.setText("0");
            text.secondEdit2.setText("0");
            text.minuteEdit2.setText("0");
            tailTimerEditTextList.add(text);
            iContentView.addView(text);
        }
        getWindow().getDecorView().invalidate();
    }

    private void deleteTailTimer(){
        iContentView.removeAllViews();
        tailTimerEditTextList.removeAll(tailTimerEditTextList);

    }

    private void initTranscode(){
        List<TailTimer> fileList = new ArrayList<>();
        for (int i = 0; i < tailTimerEditTextList.size(); i++) {
            TailTimerEditText text = tailTimerEditTextList.get(i);
            long startTime = Integer.valueOf(text.minuteEdit.getText().toString()) * 60 + Integer.valueOf(text.secondEdit.getText().toString());
            long endTime = Integer.valueOf(text.minuteEdit2.getText().toString()) * 60 + Integer.valueOf(text.secondEdit2.getText().toString());
            fileList.add(new TailTimer(startTime,endTime,srcPath,srcPath2));
        }

//        transcode = new WrapperDemo(dstPath,fileList);
        try {
            transcode = new TransCodeWrapper1(dstPath,fileList);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean verifyPermission(Activity activity){
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, 1);
        }
        return permission == PackageManager.PERMISSION_GRANTED;

    }
}
