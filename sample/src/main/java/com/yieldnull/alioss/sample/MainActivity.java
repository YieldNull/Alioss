package com.yieldnull.alioss.sample;

import android.annotation.SuppressLint;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.yieldnull.alioss.OssProfile;
import com.yieldnull.alioss.OssService;

public class MainActivity extends AppCompatActivity {

    @SuppressLint("HardwareIds")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        OSSCredentialProvider provider=new OssProfile.DefaultCustomSignerCredentialProvider(
//                "http://192.168.1.102/auth/custom");

        OSSCredentialProvider provider = new OssProfile.DefaultFederationCredentialProvider(
                "http://192.168.1.102/auth/sts",
                "yieldnull-test",
                Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));

        OssService.init(new OssProfile.Builder(
                "http://oss-cn-hangzhou.aliyuncs.com",
                "yieldnull-test", provider
        ).build());

        OssService.startService(this);
    }
}
