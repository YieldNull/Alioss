package com.yieldnull.alioss;

import android.app.AlarmManager;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSCustomSignerCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationToken;
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Oss配置
 * <p>
 * Created by yieldnull on 10/14/16.
 */
public class OssProfile {
    final String endpoint;
    final String bucket;

    final OSSCredentialProvider credentialProvider;

    final long maxFileSize;
    final List<String> dirExclude;
    final List<String> dirInclude;

    final long alarmInterval;
    final String urlTestOnline;


    public OssProfile(Builder builder) {
        endpoint = builder.endpoint;
        bucket = builder.bucket;

        credentialProvider = builder.credentialProvider;

        maxFileSize = builder.maxFileSize;

        alarmInterval = builder.alarmInterval;
        urlTestOnline = builder.urlTestOnline;

        dirExclude = builder.dirExclude;
        dirInclude = builder.dirInclude;
    }

    public static class Builder {
        final String bucket;
        final String endpoint;
        final OSSCredentialProvider credentialProvider;

        long maxFileSize = 1024 * 1024 * 500;
        long alarmInterval = AlarmManager.INTERVAL_HALF_HOUR;

        String urlTestOnline = "http://www.baidu.com";

        List<String> dirExclude = new ArrayList<>();
        List<String> dirInclude = new ArrayList<>();

        /**
         * 指定区域，bucket以及访问凭证获取方式
         *
         * @param endpoint           OSS服务所在区域
         * @param bucket             存储文件的bucket
         * @param credentialProvider 访问控制，有三种内置的凭证提供方式，
         *                           {@link DefaultPlainTextAKSKCredentialProvider},明文access_key_id以及access_key_secret；
         *                           {@link DefaultFederationCredentialProvider},使用STS服务来获取临时凭证；
         *                           {@link DefaultCustomSignerCredentialProvider}，自签名。
         *                           详见 https://help.aliyun.com/document_detail/32046.html
         * @see OSSCredentialProvider
         */
        public Builder(@NonNull String endpoint, @NonNull String bucket,
                       @NonNull OSSCredentialProvider credentialProvider) {
            this.endpoint = endpoint;
            this.bucket = bucket;
            this.credentialProvider = credentialProvider;
        }


        public Builder setAlarmInterval(long alarmInterval) {
            this.alarmInterval = alarmInterval;
            return this;
        }

        public Builder setUrlTestOnline(String urlTestOnline) {
            this.urlTestOnline = urlTestOnline;
            return this;
        }

        public Builder setMaxFileSize(long maxFileSize) {
            this.maxFileSize = maxFileSize;
            return this;
        }

        public Builder setDirExclude(String path) {
            dirExclude.add(path);
            return this;
        }

        public Builder setDirExclude(List<String> paths) {
            dirExclude.addAll(paths);
            return this;
        }

        public Builder setDirInclude(String path) {
            dirInclude.add(path);
            return this;
        }

        public Builder setDirInclude(List<String> paths) {
            dirInclude.addAll(paths);
            return this;
        }

        public OssProfile build() {
            return new OssProfile(this);
        }
    }


    /**
     * 默认明文CredentialProvider。
     * <p>
     * 使用储存在客户端中的access_key_id以及access_key_secret
     */
    public static class DefaultPlainTextAKSKCredentialProvider extends OSSPlainTextAKSKCredentialProvider {

        public DefaultPlainTextAKSKCredentialProvider(String accessKeyId, String accessKeySecret) {
            super(accessKeyId, accessKeySecret);
        }
    }


    /**
     * 默认自签名CredentialProvider
     * <p>
     * 将请求内容以form表单形式POST到指定url，获取其签名。表单内容为：“content”=“请求内容”
     */
    public static class DefaultCustomSignerCredentialProvider extends OSSCustomSignerCredentialProvider {
        private final String url;

        public DefaultCustomSignerCredentialProvider(String url) {
            this.url = url;
        }

        @Override
        @WorkerThread
        public String signContent(String content) {
            OkHttpClient client = new OkHttpClient();

            RequestBody formBody = new FormBody.Builder().add("content", content).build();
            Request request = new Request.Builder().url(url).post(formBody).build();

            String signed;
            try {
                Response response = client.newCall(request).execute();
                ResponseBody body = response.body();

                signed = body.string();
                body.close();

                return signed;

            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    /***
     * 默认STS授权CredentialProvider。
     * <p>
     * 将bucket与role信息以GET方式发送到指定url，获取stsToken。url参数名称分别为bucket与role。stsToken自动更新。
     */
    public static class DefaultFederationCredentialProvider extends OSSFederationCredentialProvider {
        private final String url;

        public DefaultFederationCredentialProvider(String url, String bucket, String role) {
            this.url = String.format("%s?bucket=%s&role=%s", url, bucket, role);
        }

        @Override
        @WorkerThread
        public OSSFederationToken getFederationToken() {
            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder().url(url).build();

            String content;
            try {
                Response response = client.newCall(request).execute();
                ResponseBody body = response.body();

                content = body.string();
                body.close();

            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }

            try {
                JSONObject json = new JSONObject(content);
                JSONObject credentials = json.getJSONObject("Credentials");

                return new OSSFederationToken(credentials.getString("AccessKeyId"),
                        credentials.getString("AccessKeySecret"),
                        credentials.getString("SecurityToken"),
                        credentials.getString("Expiration"));

            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

}
