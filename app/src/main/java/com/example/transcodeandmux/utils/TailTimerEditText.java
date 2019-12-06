package com.example.transcodeandmux.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.transcodeandmux.R;

/**
 * Created by weizheng.huang on 2019-11-12.
 */
public class TailTimerEditText extends LinearLayout {
    public EditText secondEdit;
    public EditText minuteEdit;
    TextView minuteView;
    TextView secondView;
    public EditText secondEdit2;
    public EditText minuteEdit2;
    TextView minuteView2;
    TextView secondView2;

    public TailTimerEditText(Context context) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.tailtimer_edit,this,true);
        secondEdit = findViewById(R.id.secondEdit);
        secondView = findViewById(R.id.secondView);
        minuteView = findViewById(R.id.minuteView);
        minuteEdit = findViewById(R.id.minuteEdit);
        secondEdit2 = findViewById(R.id.secondEdit2);
        secondView2 = findViewById(R.id.secondView2);
        minuteView2 = findViewById(R.id.minuteView2);
        minuteEdit2 = findViewById(R.id.minuteEdit2);
        secondView.setText("秒");
        minuteView.setText("分");
        secondView2.setText("秒");
        minuteView2.setText("分");
    }

    public TailTimerEditText(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

    }

    public TailTimerEditText(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
}
