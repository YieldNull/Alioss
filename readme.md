# Alioss

安卓后台上传照片视频到阿里云OSS


```gradle
compile group: 'com.yieldnull', name: 'alioss-img', version: '0.1.1'
```

Sample：

```java

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
```

## 如何授权

参见[授权方式](wiki/Auth_Method.md)，以及[授权服务器](wiki/Auth_Server.md)

## LICENSE

The Apache Software License, Version 2.0