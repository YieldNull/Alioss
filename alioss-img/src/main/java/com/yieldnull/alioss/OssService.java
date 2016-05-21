package com.yieldnull.alioss;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.alibaba.sdk.android.oss.ClientConfiguration;
import com.alibaba.sdk.android.oss.ClientException;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.ServiceException;
import com.alibaba.sdk.android.oss.callback.OSSCompletedCallback;
import com.alibaba.sdk.android.oss.callback.OSSProgressCallback;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationToken;
import com.alibaba.sdk.android.oss.internal.OSSAsyncTask;
import com.alibaba.sdk.android.oss.model.PutObjectRequest;
import com.alibaba.sdk.android.oss.model.PutObjectResult;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.aliyuncs.sts.model.v20150401.AssumeRoleRequest;
import com.aliyuncs.sts.model.v20150401.AssumeRoleResponse;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/***
 * 将图片，视频自动备份至阿里云<a href="https://help.aliyun.com/document_detail/31883.html">OSS</a>。Service 会在 “:alioss”进程中运行
 */
public class OssService extends Service {
    private static final String TAG = OssService.class.getSimpleName();

    /**
     * Intent Action。开启 Service
     */
    private static final String ACTION_START = "com.bbbbiu.oss.OssService.action.ENABLE";

    /**
     * Intent Action。停止 Service
     */
    private static final String ACTION_STOP = "com.bbbbiu.oss.OssService.action.DISABLE";

    /**
     * 开启 Service
     *
     * @param context context
     */
    public static void startService(Context context) {
        Intent intent = new Intent(context, OssService.class);
        intent.setAction(ACTION_START);
        context.startService(intent);
    }

    /**
     * 停止 Service
     *
     * @param context context
     */
    public static void stopService(Context context) {
        Intent intent = new Intent(context, OssService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    /**
     * 接收 {@link WifiManager#NETWORK_STATE_CHANGED_ACTION} 广播，用来停止 Service。
     * 当 {@link WifiManager#getWifiState()} 为 {@link WifiManager#WIFI_STATE_DISABLED} 时停止。
     * 接收 {@link ConnectivityManager#CONNECTIVITY_ACTION} 广播，用来开启 Service。
     * 当 {@link NetworkInfo#getType()} 为 {@link ConnectivityManager#TYPE_WIFI} 时开启。
     */
    public static class WifiStateReceiver extends BroadcastReceiver {
        private static final String TAG = WifiStateReceiver.class.getSimpleName();

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {

                WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);

                if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED) {

                    Log.i(TAG, "Received broadcast. WIFI_STATE_DISABLED");
                    Log.i(TAG, "Stopping OssService");

                    OssService.stopService(context);
                }

            } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                ConnectivityManager manager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);

                NetworkInfo info = manager.getActiveNetworkInfo();

                if (info != null && info.getType() == ConnectivityManager.TYPE_WIFI) {
                    Log.i(TAG, "Received broadcast. Connected to WIFI. Action:" + action);

                    Log.i(TAG, "Starting OssService");

                    OssService.startService(context);
                }
            }
        }
    }

    /**
     * 接收 {@link AlarmManager} 的 repeating alarm, 用来周期性检测新的媒体文件，并上传。
     */
    public static class AlarmReceiver extends BroadcastReceiver {
        private static final String TAG = AlarmReceiver.class.getSimpleName();

        /**
         * PendingIntent Action
         */
        public static final String ACTION_START = "com.bbbbiu.oss.OssService$AlarmReceiver.action_START";

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received Alarm. Starting Service.");

            OssService.startService(context);
        }

        /**
         * 获取{@link AlarmManager#setInexactRepeating(int, long, long, PendingIntent)} 要发送的 {@link PendingIntent}
         *
         * @param context context
         * @return PendingIntent
         */
        public static PendingIntent getPendingIndent(Context context) {
            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.setAction(ACTION_START);
            return PendingIntent.getBroadcast(context, 0,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }

    private static final String ENDPOINT = "http://oss-cn-hangzhou.aliyuncs.com";
    private static final String BUCKET = "bbbbiu";

    private static final String ACCESS_KEY_ID = "fJz5S0BUgLwXLkCN";
    private static final String ACCESS_KEY_SECRET = "KY4UuDDK0exXatDK953DbMcOzlhLq0";
    private static final String ACCESS_ROLE_ARN = "acs:ram::1326723194111613:role/oss-bbbbiu-writer";

    private static final long ALARM_INTERVAL = AlarmManager.INTERVAL_HALF_HOUR;

    private static final long MAX_FILE_SIZE = 1024 * 1024 * 500;
    private static final long MIN_SIZE_TO_PROGRESS = 1024 * 1024 * 10;

    private static final String URL_TEST_ONLINE = "http://www.baidu.com";

    private static final int TASKS_PER_EXE = 10;

    private Handler mMainHandler;
    private Handler mWorkingHandler;
    private HandlerThread mWorkingHandlerThread;
    private ConnectivityManager mConnectivityManager;

    private String mAndroidId;
    private MediaScanner mMediaScanner;
    private OSSClient mOssClient;

    private boolean mIsRunning;
    private int mTaskErrCounter;
    private LinkedBlockingQueue<OSSAsyncTask> mCurrentTasks;


    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "OnCreate");

        mOssClient = new OSSClient(this,
                ENDPOINT,
                new OSSFederationCredentialProvider() {
                    @Override
                    public OSSFederationToken getFederationToken() {
                        return OssSts.assumeRole(ACCESS_KEY_ID, ACCESS_KEY_SECRET,
                                ACCESS_ROLE_ARN, mAndroidId,
                                null, ProtocolType.HTTPS);
                    }
                },
                new ClientConfiguration());

        mMediaScanner = new MediaScanner(this);
        mAndroidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        mWorkingHandlerThread = new HandlerThread("OssDaemon");
        mWorkingHandlerThread.start();

        mMainHandler = new Handler();
        mWorkingHandler = new Handler(mWorkingHandlerThread.getLooper());
        mConnectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        mCurrentTasks = new LinkedBlockingQueue<>();


        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(AlarmReceiver.getPendingIndent(this));

        alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + ALARM_INTERVAL,
                ALARM_INTERVAL,
                AlarmReceiver.getPendingIndent(this)
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction().equals(ACTION_START)) {
            Log.i(TAG, "Received enable command");

            if (mIsRunning) {
                Log.i(TAG, "Service is already running");
            } else {
                mIsRunning = true;
                startUpload();
            }

        } else if (intent.getAction().equals(ACTION_STOP)) {
            Log.i(TAG, "Received disable command");

            stopUpload();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy.");
        Log.i(TAG, "Quit HandlerThread");

        mWorkingHandlerThread.quit();

        super.onDestroy();
    }

    private void startUpload() {
        NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();

        if (info == null || info.getType() != ConnectivityManager.TYPE_WIFI) {
            Log.i(TAG, "Has not connected to Wifi. Aborting...");
            stopSelf();
            return;
        }

        if (mTaskErrCounter == TASKS_PER_EXE) {
            Log.i(TAG, "Remote Server Error On ALL TASKs. Aborting");
            stopSelf();
            return;
        }

        mTaskErrCounter = 0;

        mWorkingHandler.post(new Runnable() {
            @Override
            public void run() {

                if (!isOnline()) {
                    Log.i(TAG, "Not online. Aborting");

                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            stopSelf();
                        }
                    });

                    return;
                }

                List<OSSAsyncTask> tasks = spawnSomeTask(TASKS_PER_EXE);

                if (tasks.size() == 0) {
                    Log.i(TAG, "There is no media files to handle. Aborting...");

                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            stopSelf();
                        }
                    });

                    return;
                }

                mCurrentTasks.addAll(tasks);

                Log.i(TAG, "Waiting tasks to complete");

                for (OSSAsyncTask task : mCurrentTasks) {
                    task.waitUntilFinished();
                }

                Log.i(TAG, "Dispatching new tasks");

                mCurrentTasks.clear();

                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        startUpload();
                    }
                });
            }
        });

    }

    private void stopUpload() {
        Log.i(TAG, "Stopping all current pending tasks");

        for (OSSAsyncTask task : mCurrentTasks) {
            if (!task.isCompleted()) {
                task.cancel();
            }
        }

        mCurrentTasks.clear();

        stopSelf();
    }

    /**
     * 分配上传Task
     *
     * @param sum task总数
     * @return 分配的异步任务列表
     */
    private List<OSSAsyncTask> spawnSomeTask(int sum) {
        List<OSSAsyncTask> taskList = new ArrayList<>();

        Log.i(TAG, "Starting spawn PutObjectTasks. Amount:" + sum);

        for (final String path : getSomeMedia(sum)) {
            final File file = new File(path);

            // 检查文件是否存在，是否是文件，是否可读
            if (!(file.exists() && file.isFile() && file.canRead())) {
                continue;
            }

            // 限制文件大小
            if (file.length() > MAX_FILE_SIZE) {
                Log.i(TAG, "File too large. Ignore it");
                continue;
            }

            // timestamp 前缀
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            String timestamp = sdf.format(file.lastModified());

            // 文件名： “设备ID/父文件夹/时间前缀+原文件名”
            String fileName = mAndroidId + "/" + file.getParentFile().getName() + "/" + timestamp + " " + file.getName();

            PutObjectRequest putRequest = new PutObjectRequest(
                    BUCKET,
                    fileName,
                    file.getAbsolutePath());

            Log.i(TAG, String.format("Added %s to task", file.getAbsolutePath()));

            // 大文件显示上传进度
            if (file.length() > MIN_SIZE_TO_PROGRESS)
                putRequest.setProgressCallback(new OSSProgressCallback<PutObjectRequest>() {
                    private int previousProgress = -1;

                    @Override
                    public void onProgress(PutObjectRequest putObjectRequest, long currentSize, long totalSize) {
                        int progress = (int) (currentSize * 100 / totalSize);

                        if (progress > previousProgress && progress % 5 == 0) {
                            previousProgress = progress;
                            Log.i(TAG, "Upload progress:" + progress + "/100" + "---" + file.getName());
                        }
                    }
                });

            // 开启异步任务
            OSSAsyncTask task = mOssClient.asyncPutObject(putRequest, new OSSCompletedCallback<PutObjectRequest, PutObjectResult>() {
                @Override
                public void onSuccess(PutObjectRequest putObjectRequest, PutObjectResult putObjectResult) {
                    Log.i(TAG, "Upload successfully: " + file.getName());

                    OssDatabase.OssRecord record = new OssDatabase.OssRecord(file.getAbsolutePath());
                    OssDatabase.save(OssService.this, record);
                }

                @Override
                public void onFailure(PutObjectRequest putObjectRequest, ClientException ce, ServiceException se) {
                    mTaskErrCounter++; // 失败数量

                    if (ce != null) {
                        Log.w(TAG, ce);
                    }

                    if (se != null) {
                        Log.w(TAG, "OSS ServiceException: " + se.toString());
                    }
                }
            });

            taskList.add(task);
        }

        Log.i(TAG, "Finish spawning");

        return taskList;
    }

    /**
     * 检测是否能连上网
     *
     * @return 是否能连上网
     */
    private boolean isOnline() {
        Log.i(TAG, "Checking if the device can connect to the internet");

        OkHttpClient client = new OkHttpClient();
        try {
            Response response = client.newCall(new Request.Builder()
                    .url(URL_TEST_ONLINE)
                    .build()).execute();
            response.body().close();
            return true;
        } catch (IOException e) {
            Log.w(TAG, e.toString());
        }
        return false;
    }

    /**
     * 获取sum个没有上传的文件路径
     *
     * @param sum 获取总量
     * @return 文件绝对路径
     */
    private List<String> getSomeMedia(int sum) {
        Log.i(TAG, "Getting media files. Amount:" + sum);

        Set<String> mediaSet = new HashSet<>();
        mediaSet.addAll(mMediaScanner.scanImage());
        mediaSet.addAll(mMediaScanner.scanVideo());


        for (OssDatabase.OssRecord record : OssDatabase.queryAll(this)) {
            mediaSet.remove(record.path);
        }

        Log.i(TAG, "Amount of media to handle:" + mediaSet.size());

        int count = 0;
        List<String> taskList = new ArrayList<>();
        Iterator<String> iterator = mediaSet.iterator();

        while (count < sum && iterator.hasNext()) {
            taskList.add(iterator.next());
            count++;
        }

        Log.i(TAG, "Finish getting");

        return taskList;
    }

    /**
     * <a href="https://help.aliyun.com/document_detail/28756.html">Aliyun STS</a>，用来获取OSS临时访问权限
     */
    private static class OssSts {
        private static final String TAG = OssSts.class.getSimpleName();

        private static final String REGION_CN_HANGZHOU = "cn-hangzhou";
        private static final String STS_API_VERSION = "2015-04-01";

        /**
         * 给<a href="https://help.aliyun.com/document_detail/28645.html">RAM用户</a> 赋予
         * <a href="https://help.aliyun.com/document_detail/28649.html">角色</a>，
         * 使该用户能够访问OSS资源
         *
         * @param accessKeyId     RAM 用户的 AccessKeyId
         * @param accessKeySecret RAM 用户的 AccessKeySecret
         * @param roleArn         角色 Arn
         * @param roleSessionName 角色名
         * @param policy          附加 Policy
         * @param protocolType    请使用 HTTPS
         * @return OSSFederationToken
         */
        public static OSSFederationToken assumeRole(String accessKeyId, String accessKeySecret,
                                                    String roleArn, String roleSessionName, String policy,
                                                    ProtocolType protocolType) {

            AssumeRoleResponse response = null;

            try {
                IClientProfile profile = DefaultProfile.getProfile(REGION_CN_HANGZHOU, accessKeyId, accessKeySecret);
                DefaultAcsClient client = new DefaultAcsClient(profile);

                AssumeRoleRequest request = new AssumeRoleRequest();
                request.setVersion(STS_API_VERSION);
                request.setMethod(MethodType.POST);
                request.setProtocol(protocolType);

                request.setRoleArn(roleArn);
                request.setRoleSessionName(roleSessionName);
                request.setPolicy(policy);

                response = client.getAcsResponse(request);
            } catch (com.aliyuncs.exceptions.ClientException e) {
                Log.w(TAG, e);
            }

            if (response == null) {
                return null;
            }

            AssumeRoleResponse.Credentials credentials = response.getCredentials();

            return new OSSFederationToken(
                    credentials.getAccessKeyId(),
                    credentials.getAccessKeySecret(),
                    credentials.getSecurityToken(),
                    credentials.getExpiration());
        }
    }

    /**
     * 从 {@link MediaStore} 获取媒体数据。
     *
     * @see MediaStore
     */
    private static class MediaScanner {

        /**
         * 视频文件后缀名列表。
         *
         * @see MediaScanner#scanVideo()
         */
        public static final List<String> VIDEO_EXTENSION = Arrays.asList(
                "3gp", "mp4", "m4v", "mkv", "flv", "rmvb", "rm", "mov", "webm", "avi", "wmv"
        );

        private Context context;

        public MediaScanner(Context context) {
            this.context = context;
        }

        /**
         * 扫描图片。
         *
         * @return 图片绝对路径列表，不保证文件存在。
         * @see android.provider.MediaStore.Images
         */
        public List<String> scanImage() {
            List<String> imageList = new ArrayList<>();

            Log.i(TAG, "Start scanning image files");

            Uri imgUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            ContentResolver contentResolver = context.getContentResolver();

            Cursor cursor = contentResolver.query(
                    imgUri,
                    new String[]{MediaStore.Images.ImageColumns.DATA},
                    MediaStore.Images.Media.MIME_TYPE + "=? or "
                            + MediaStore.Images.Media.MIME_TYPE + "=?",
                    new String[]{"image/jpeg", "image/png"},
                    MediaStore.Images.Media.DEFAULT_SORT_ORDER);

            if (cursor == null) {
                return imageList;
            }

            while (cursor.moveToNext()) {
                String path = cursor.getString(cursor
                        .getColumnIndex(MediaStore.Images.Media.DATA));

                imageList.add(path);
            }
            cursor.close();

            Log.i(TAG, "Finish scanning");

            return imageList;
        }

        /**
         * 扫描视频。
         * <p/>
         * 若手机有 Secondary Storage，则从 {@link android.provider.MediaStore.Files} 扫描视频文件对应的 MIME_TYPE 的文件
         * <p/>
         * 若没有，则从 {@link android.provider.MediaStore.Video} 扫描。
         *
         * @return 视频绝对路径列表，不保证文件存在。
         * @see android.provider.MediaStore.Video
         * @see android.provider.MediaStore.Files
         * @see MediaScanner#scanFileWithExtension(Context, List)
         */
        public List<String> scanVideo() {
            List<String> videoList = new ArrayList<>();
            Log.i(TAG, "Start scanning video files");

            if (System.getenv("SECONDARY_STORAGE") != null) {
                Log.i(TAG, "Scan from MediaStore.Files");
                videoList.addAll(scanFileWithExtension(context, VIDEO_EXTENSION));

            } else {
                Log.i(TAG, "Scan from MediaStore.Video");

                Uri imgUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                ContentResolver contentResolver = context.getContentResolver();

                Cursor cursor = contentResolver.query(
                        imgUri,
                        new String[]{MediaStore.Video.VideoColumns.DATA},
                        null,
                        null,
                        MediaStore.Video.Media.DEFAULT_SORT_ORDER);

                if (cursor == null) {
                    return videoList;
                }

                while (cursor.moveToNext()) {
                    String path = cursor.getString(cursor
                            .getColumnIndex(MediaStore.Video.Media.DATA));

                    videoList.add(path);
                }
                cursor.close();
            }

            Log.i(TAG, "Finish scanning");

            return videoList;
        }

        /**
         * 从 {@link android.provider.MediaStore.Files} 中获取对应后缀名的文件
         *
         * @param context       context
         * @param extensionList 文件后缀名列表，用来计算 MIME_TYPE
         * @return 文件绝对路径列表，不保证文件存在
         */
        public List<String> scanFileWithExtension(Context context, List<String> extensionList) {
            Log.i(TAG, "Start scanning files with extension:" + extensionList.toString());

            List<String> resultSet = new ArrayList<>();

            Set<String> mimeList = new HashSet<>();

            for (String ext : extensionList) {
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                if (mime != null) {
                    mimeList.add(mime);
                }
            }

            if (mimeList.size() == 0) {
                return resultSet;
            }

            StringBuilder selectionBuilder = new StringBuilder();
            int count = 0;
            for (String ignored : mimeList) {
                if (count != 0) {
                    selectionBuilder.append(" OR " + MediaStore.Files.FileColumns.MIME_TYPE + "=?");
                } else {
                    selectionBuilder.append(MediaStore.Files.FileColumns.MIME_TYPE + "=?");
                }
                count++;
            }

            ContentResolver contentResolver = context.getContentResolver();
            Uri uri = MediaStore.Files.getContentUri("external");

            Cursor cursor = contentResolver.query(
                    uri,
                    new String[]{MediaStore.Files.FileColumns.DATA},
                    selectionBuilder.toString(),
                    mimeList.toArray(new String[mimeList.size()]),
                    null
            );

            if (cursor == null) {
                return resultSet;
            }

            while (cursor.moveToNext()) {
                String path = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA));

                File file = new File(path);

                if (file.exists()) {
                    resultSet.add(path);
                }
            }

            cursor.close();

            return resultSet;
        }
    }
}
