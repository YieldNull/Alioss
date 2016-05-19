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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OssService extends Service {
    private static final String TAG = OssService.class.getSimpleName();

    private static final String ACTION_ENABLE = "com.bbbbiu.oss.OssService.action.ENABLE";
    private static final String ACTION_DISABLE = "com.bbbbiu.oss.OssService.action.DISABLE";

    public static void startService(Context context) {
        Intent intent = new Intent(context, OssService.class);
        intent.setAction(ACTION_ENABLE);
        context.startService(intent);
    }

    public static void stopService(Context context) {
        Intent intent = new Intent(context, OssService.class);
        intent.setAction(ACTION_DISABLE);
        context.startService(intent);
    }

    public static class WifiStateReceiver extends BroadcastReceiver {
        private static final String TAG = WifiStateReceiver.class.getSimpleName();

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {

                WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);

                if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLING) {

                    Log.i(TAG, "Received broadcast. WIFI_STATE_DISABLING. Action:" + action);
                    Log.i(TAG, "Disabling OssService");

                    OssService.stopService(context);
                }

            } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                ConnectivityManager manager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);

                NetworkInfo info = manager.getActiveNetworkInfo();

                if (info != null && info.getType() == ConnectivityManager.TYPE_WIFI) {
                    Log.i(TAG, "Received broadcast. Connected to WIFI. Action:" + action);

                    Log.i(TAG, "Enabling OssService");

                    OssService.startService(context);
                }
            }
        }
    }

    public static class AlarmReceiver extends BroadcastReceiver {
        private static final String TAG = AlarmReceiver.class.getSimpleName();

        public static final String ACTION_START = "com.bbbbiu.oss.OssService$AlarmReceiver.action_START";

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received Alarm. Starting Service.");

            OssService.startService(context);
        }

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
    private int mServerErrCounter;
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
                        return OssStsService.assumeRole(ACCESS_KEY_ID, ACCESS_KEY_SECRET,
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
        if (intent == null || intent.getAction().equals(ACTION_ENABLE)) {
            Log.i(TAG, "Received enable command");

            startUpload();

        } else if (intent.getAction().equals(ACTION_DISABLE)) {
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
        if (mIsRunning && mCurrentTasks.size() > 0) {
            Log.i(TAG, "Service is already running");
            return;
        }

        mIsRunning = true;
        NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();

        if (info == null || info.getType() != ConnectivityManager.TYPE_WIFI) {
            Log.i(TAG, "Has not connected to Wifi. Aborting...");
            mIsRunning = false;
            return;
        }

        if (mServerErrCounter == TASKS_PER_EXE) {
            Log.i(TAG, "Remote Server Error On ALL TASKs. Aborting");
            stopSelf();
            return;
        }

        mServerErrCounter = 0;

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!isOnline()) {
                    Log.i(TAG, "Not online. Aborting");
                    mIsRunning = false;
                    return;
                }

                List<OSSAsyncTask> tasks = spawnSomeTask(TASKS_PER_EXE);

                if (tasks.size() == 0) {
                    Log.i(TAG, "There is no media files to handle. Aborting...");
                    stopSelf();
                    return;
                }

                mCurrentTasks.addAll(tasks);

                mWorkingHandler.post(new Runnable() {
                    @Override
                    public void run() {

                        Log.i(TAG, "Waiting previous tasks to complete");

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
        }).start();

    }

    private void stopUpload() {
        Log.i(TAG, "Stopping all current pending tasks");

        mIsRunning = false;

        for (OSSAsyncTask task : mCurrentTasks) {
            if (!task.isCompleted()) {
                task.cancel();
            }
        }

        mCurrentTasks.clear();
    }

    private List<OSSAsyncTask> spawnSomeTask(int sum) {
        List<OSSAsyncTask> taskList = new ArrayList<>();

        Log.i(TAG, "Starting spawn PutObjectTasks. Amount:" + sum);

        for (final String path : getSomeMedia(sum)) {
            final File file = new File(path);

            if (!(file.exists() && file.isFile() && file.canRead())) {
                continue;
            }

            if (file.length() > MAX_FILE_SIZE) {
                Log.i(TAG, "File too large. Ignore it");
                continue;
            }

            PutObjectRequest putRequest = new PutObjectRequest(
                    BUCKET,
                    mAndroidId + "/" + file.getParentFile().getName() + "/" + file.getName(),
                    file.getAbsolutePath());

            Log.i(TAG, String.format("Added %s to task", file.getAbsolutePath()));

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

            OSSAsyncTask task = mOssClient.asyncPutObject(putRequest, new OSSCompletedCallback<PutObjectRequest, PutObjectResult>() {
                @Override
                public void onSuccess(PutObjectRequest putObjectRequest, PutObjectResult putObjectResult) {
                    Log.i(TAG, "Upload successfully: " + file.getName());

                    OssDatabase.OssRecord record = new OssDatabase.OssRecord(file.getAbsolutePath());
                    OssDatabase.save(OssService.this, record);
                }

                @Override
                public void onFailure(PutObjectRequest putObjectRequest, ClientException ce, ServiceException se) {

                    if (ce != null) {
                        Log.w(TAG, ce);
                    }

                    if (se != null) {
                        Log.w(TAG, "OSS ServiceException: " + se.toString());
                        mServerErrCounter++;
                    }
                }
            });

            taskList.add(task);
        }

        Log.i(TAG, "Finish spawning");

        return taskList;
    }


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

    static class OssStsService {
        private static final String TAG = OssStsService.class.getSimpleName();

        private static final String REGION_CN_HANGZHOU = "cn-hangzhou";
        private static final String STS_API_VERSION = "2015-04-01";

        static OSSFederationToken assumeRole(String accessKeyId, String accessKeySecret,
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

    static class MediaScanner {
        static final List<String> VIDEO_EXTENSION = Arrays.asList(
                "3gp", "mp4", "m4v", "mkv", "flv", "rmvb", "rm", "mov", "webm", "avi", "wmv"
        );

        Context context;

        public MediaScanner(Context context) {
            this.context = context;
        }

        private List<String> scanImage() {
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

        private List<String> scanVideo() {
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

        private List<String> scanFileWithExtension(Context context, List<String> extensionList) {
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
