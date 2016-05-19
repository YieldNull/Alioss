package com.yieldnull.alioss.sample;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.yieldnull.alioss.OssService;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        OssService.startService(this);
    }
}
