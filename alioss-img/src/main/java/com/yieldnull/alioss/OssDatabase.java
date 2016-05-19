package com.yieldnull.alioss;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by YieldNull at 5/19/16
 */
public class OssDatabase extends SQLiteOpenHelper {
    private static final String TAG = OssDatabase.class.getSimpleName();

    private static final String DATABASE_NAME = "com.yieldnull.ossimg";
    private static int DATABASE_VERSION = 1;


    private static final String SQL_CREATE = "CREATE TABLE " +
            OssRecord.TABLE_NAME + " (" +
            OssRecord.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            OssRecord.COLUMN_PATH + " TEXT NOT NULL )";

    private static OssDatabase sOssDatabase;

    public static OssDatabase getSingleton(Context context) {
        if (sOssDatabase == null) {
            sOssDatabase = new OssDatabase(context);
        }

        return sOssDatabase;
    }

    public static void save(Context context, OssRecord record) {
        SQLiteDatabase db = OssDatabase.getSingleton(context).getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(OssRecord.COLUMN_PATH, record.path);

        db.insert(OssRecord.TABLE_NAME, "", values);
    }

    public static List<OssRecord> queryAll(Context context) {
        SQLiteDatabase db = OssDatabase.getSingleton(context).getReadableDatabase();

        List<OssRecord> records = new ArrayList<>();

        Cursor cursor = db.query(
                OssRecord.TABLE_NAME,
                new String[]{OssRecord.COLUMN_PATH},
                null,
                null,
                null,
                null,
                null);

        if (cursor == null) {
            return records;
        }

        while (cursor.moveToNext()) {
            String path = cursor.getString(cursor.getColumnIndex(OssRecord.COLUMN_PATH));
            records.add(new OssRecord(path));
        }

        cursor.close();

        return records;
    }

    private OssDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }


    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }


    public static class OssRecord {
        public static final String TABLE_NAME = "OssRecord";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_PATH = "path";

        public String path;

        public OssRecord(String path) {
            this.path = path;
        }
    }
}
