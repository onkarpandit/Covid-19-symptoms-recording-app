package com.example.mobilecomputinghw1;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Database extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "SYMPTOMSDB.db";
    public static final String TABLE_NAME = "SymptomsTable";

    // Column names
    public static final String COL_0 = "LAST NAME";
    public static final String COL_1 = "HEART_RATE";
    public static final String COL_2 = "RESPIRATORY_RATE";
    public static final String COL_3 = "NAUSEA";
    public static final String COL_4 = "HEADACHE";
    public static final String COL_5 = "DIARRHOEA";
    public static final String COL_6 = "SOAR_THROAT";
    public static final String COL_7 = "FEVER";
    public static final String COL_8 = "MUSCLE_ACHE";
    public static final String COL_9 = "LOSS_OF_SMELL";
    public static final String COL_10 = "COUGH";
    public static final String COL_11 = "SHORTNESS_OF_BREATH";
    public static final String COL_12 = "FEELING_TIRED";

    public Database(@Nullable Context context) {
        super(context, DATABASE_NAME, null, 1);

    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("create table "+TABLE_NAME+"( ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "HEART_RATE TEXT," +
                " RESPIRATORY_RATE TEXT, " +
                "NAUSEA TEXT," +
                "HEADACHE TEXT," +
                "DIARRHOEA TEXT," +
                "SOAR_THROAT TEXT," +
                "FEVER TEXT," +
                "MUSCLE_ACHE TEXT," +
                "LOSS_OF_SMELL TEXT," +
                "COUGH TEXT," +
                "SHORTNESS_OF_BREATH TEXT," +
                "FEELING_TIRED TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS "+TABLE_NAME);
        onCreate(db);
    }

    public Boolean insertData(HashMap<String, String> data){
        for (Map.Entry<String, String> iter: data.entrySet()) {
            Log.d("Key Value pair", iter.getKey() +" "+iter.getValue());
        }

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COL_0, "PANDIT");
        contentValues.put(COL_1, data.get("Heart Rate"));
        contentValues.put(COL_2, data.get("Respiratory Rate"));
        contentValues.put(COL_3, data.get("Nausea"));
        contentValues.put(COL_4, data.get("Headache"));
        contentValues.put(COL_5, data.get("Diarrhoea"));
        contentValues.put(COL_6, data.get("Soar Throat"));
        contentValues.put(COL_7, data.get("Fever"));
        contentValues.put(COL_8, data.get("Muscle Ache"));
        contentValues.put(COL_9, data.get("Loss of smell or taste"));
        contentValues.put(COL_10, data.get("Cough"));
        contentValues.put(COL_11, data.get("Shortness of breath"));
        contentValues.put(COL_12, data.get("Feeling Tired"));
        long result = db.insert(TABLE_NAME, null, contentValues);
        if (result == -1){
            return false;
        }
        return true;
    }
}
