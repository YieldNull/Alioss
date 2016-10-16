package com.yieldnull.alioss;

import android.annotation.SuppressLint;
import android.content.Context;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.alibaba.sdk.android.oss.ClientException;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.ServiceException;
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.alibaba.sdk.android.oss.model.PutObjectRequest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * 测试自签名以及STS等授权方式
 * <p>
 * Created by yieldnull on 10/15/16.
 */
@RunWith(AndroidJUnit4.class)
public class OssCredentialProviderTest {

    private static final String BUCKET = "yieldnull-test";
    private static final String ENDPOINT = "http://oss-cn-hangzhou.aliyuncs.com";

    private static final String URL_STS = "http://192.168.1.102/auth/sts";
    private static final String URL_CUSTOM = "http://192.168.1.102/auth/custom";

    private Context context = InstrumentationRegistry.getTargetContext();


    /**
     * 测试STS：在根目录下上传失败
     *
     * @throws Exception
     */
    @Test(expected = ServiceException.class)
    @SuppressLint("HardwareIds")
    public void federationTokenTest() throws Exception {
        OssProfile.DefaultFederationCredentialProvider provider =
                new OssProfile.DefaultFederationCredentialProvider(URL_STS, BUCKET,
                        Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID));

        assertThat(provider.getFederationToken(), notNullValue());

        upload(provider);
    }

    /**
     * 测试STS：在其它目录下上传失败
     *
     * @throws Exception
     */
    @Test(expected = ServiceException.class)
    @SuppressLint("HardwareIds")
    public void federationTokenTest1() throws Exception {
        OssProfile.DefaultFederationCredentialProvider provider =
                new OssProfile.DefaultFederationCredentialProvider(URL_STS, BUCKET,
                        Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID));

        assertThat(provider.getFederationToken(), notNullValue());

        upload(provider, "device1");
    }

    /**
     * 测试STS：在role目录下上传文件成功
     *
     * @throws Exception
     */
    @Test
    @SuppressLint("HardwareIds")
    public void federationTokenTest2() throws Exception {
        String role = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);

        OssProfile.DefaultFederationCredentialProvider provider =
                new OssProfile.DefaultFederationCredentialProvider(URL_STS, BUCKET, role);

        assertThat(provider.getFederationToken(), notNullValue());

        upload(provider, role);
    }

    /**
     * 测试自签名
     *
     * @throws Exception
     */
    @Test
    public void customSignerCredentialTest() throws Exception {
        OssProfile.DefaultCustomSignerCredentialProvider provider =
                new OssProfile.DefaultCustomSignerCredentialProvider(URL_CUSTOM);

        upload(provider);
    }

    /**
     * 测试上传一个文件，不带路径前缀
     *
     * @param provider {@link OSSCredentialProvider}
     * @throws ClientException
     * @throws ServiceException
     */
    private void upload(OSSCredentialProvider provider) throws ClientException, ServiceException {
        upload(provider, null);
    }

    /**
     * 测试上传一个文件
     *
     * @param provider {@link OSSCredentialProvider}
     * @param prefix   路径前缀
     * @throws ClientException
     * @throws ServiceException
     */
    private void upload(OSSCredentialProvider provider, String prefix) throws ClientException, ServiceException {
        byte[] uploadData = new byte[100 * 1024];
        new Random().nextBytes(uploadData);

        OSSClient client = new OSSClient(context, ENDPOINT, provider);

        String path = prefix == null ? provider.getClass().getName() : prefix + "/" + provider.getClass().getName();

        PutObjectRequest put = new PutObjectRequest(BUCKET, path, uploadData);
        client.putObject(put);
    }
}