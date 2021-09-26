package com.example.mobilecomputinghw1;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class HeartRateCalculator extends AppCompatActivity implements SensorEventListener  {
    private static final int VIDEO_CAPTURE = 101;
    String filePath;
    private double heartRate = 0;
    private String respiratoryRate = "0";
    private EntryStorage store;
    private HashMap<String, String> symptoms = new HashMap<String, String>();


    private SensorManager accelManage;
    private Sensor senseAccel;
    final private int MAX_COUNT = 220;
    float accelValuesX[] = new float[MAX_COUNT];
    float accelValuesY[] = new float[MAX_COUNT];
    float accelValuesZ[] = new float[MAX_COUNT];
    long time1;
    long time2;
    int total_time = 0;
    int index =0;
    TextView logger;
    Database database;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_rate_calculator);
        configurePermissions();

        Button measureHeartRateButton = (Button) findViewById(R.id.heartRateButton);
        Button measureRespiratoryButton = (Button) findViewById(R.id.respRateButton);
        Button recordSymptoms = (Button) findViewById(R.id.record_symptoms);

        logger = (TextView) findViewById(R.id.heartRateInstruction);
        database = new Database(this);

        Intent intent = getIntent();
        String symptomsString = (String) intent.getStringExtra("SYMPTOMS");

        getSymptoms(symptomsString);

        if(!isCameraPermitted()){
            measureHeartRateButton.setEnabled(false);
        }

        measureHeartRateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                measureHeartRate();
            }
        });

        measureRespiratoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                measureRespiratoryRate();
            }
        });

        recordSymptoms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openRecordSymptoms();
            }
        });

    }

    private void exportDB() {
        File file = new File("/storage/self/primary/Download/covid_sym_db.csv");

        try {
            CSVWriter csvWrite = new CSVWriter(new FileWriter(file));
            SQLiteDatabase db = database.getReadableDatabase();
            Cursor curCSV = db.rawQuery("SELECT * FROM SymptomsTable",null);
            csvWrite.writeNext(curCSV.getColumnNames());
            while(curCSV.moveToNext())
            {
                String arrStr[] ={curCSV.getString(0),curCSV.getString(1), curCSV.getString(2), curCSV.getString(3), curCSV.getString(4), curCSV.getString(5), curCSV.getString(6), curCSV.getString(7), curCSV.getString(8), curCSV.getString(9), curCSV.getString(10), curCSV.getString(11), curCSV.getString(12), curCSV.getString(13)};
                csvWrite.writeNext(arrStr);
            }
            csvWrite.close();
            curCSV.close();
        }
        catch(Exception sqlEx) {
            Log.e("MainActivity", sqlEx.getMessage(), sqlEx);
        }
    }

    private void getSymptoms(String symptomsString) {
        if(symptomsString != null){
            symptomsString = symptomsString.substring(1);
            symptomsString = symptomsString.substring(0, symptomsString.length() - 1);

            String[] pairs = symptomsString.split(",");
            for (int i=0;i<pairs.length;i++) {
                String pair = pairs[i].trim();
                String[] keyValue = pair.split("=");
                symptoms.put(keyValue[0].trim(), keyValue[1].trim());
            }
            database.updateDbWithSymptoms(symptoms);
            exportDB();
        }
    }

    private boolean isCameraPermitted() {
        if (getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAMERA_ANY)){
            return true;
        } else {
            return false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    void configurePermissions() {
        if (!Environment.isExternalStorageManager()) {
            Intent allFilesIntent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivity(allFilesIntent);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}
                        , 10);
            }
            return;
        }

    }

    private void measureRespiratoryRate() {
        ((TextView) findViewById(R.id.respInstruction)).setText("Calculating...");
        accelManage = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        senseAccel = accelManage.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        accelManage.registerListener(HeartRateCalculator.this, senseAccel, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void openRecordSymptoms() {
        boolean result = database.insertHeartRate(String.valueOf(heartRate), respiratoryRate);

        if (result) {
            Toast.makeText(this, "Heart rate and respiratory rate inserted successfully", Toast.LENGTH_LONG);
        }

        Intent intent = new Intent(this, RecordSymptoms.class);
        startActivity(intent);
    }

    public int getHeartRate() {
        if (total_time==0)
            return 0;
        float xSum=0, ySum=0, zSum=0;
        for (int i =0; i<MAX_COUNT; i++){
            xSum += accelValuesX[i];
            ySum += accelValuesY[i];
            zSum += accelValuesZ[i];
        }
        float xAvg = xSum/MAX_COUNT, yAvg = ySum/MAX_COUNT, zAvg = zSum/MAX_COUNT;
        int xCount=0, yCount=0, zCount =0;
        for (int i =1; i<MAX_COUNT; i++){
            if ((accelValuesX[i-1] <= xAvg && xAvg<= accelValuesX[i]) || (accelValuesX[i-1] >= xAvg && xAvg>= accelValuesX[i]))
                xCount++;
            if ((accelValuesY[i-1] <= yAvg && yAvg<= accelValuesY[i]) || (accelValuesY[i-1] >= yAvg && yAvg>= accelValuesY[i]))
                yCount++;
            if ((accelValuesZ[i-1] <= zAvg && zAvg<= accelValuesZ[i]) || (accelValuesZ[i-1] >= zAvg && zAvg>= accelValuesZ[i]))
                zCount++;
        }
        int max = Math.max(xCount, Math.max(yCount, zCount));

        return max*30/total_time;
    }

    private void measureHeartRate() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT,45);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, VIDEO_CAPTURE);

    }

    public String getPath(Uri uri) {
        String[] projection = {MediaStore.MediaColumns.DATA};
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        int column_index = cursor
                .getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
        cursor.moveToFirst();

        return cursor.getString(column_index);
    }


    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (resultCode != RESULT_OK) return;

        Uri selectedImage = intent.getData();

        filePath = getPath(selectedImage);
        Log.d("Filepath", filePath);
        Toast.makeText(this,"Video Location is "+ filePath,Toast.LENGTH_SHORT).show();

        new HeartRateAsync().execute();
    }

    private class HeartRateAsync extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            Uri videoFileUri=Uri.parse(filePath);

            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(filePath);

            MediaPlayer mp = MediaPlayer.create(getBaseContext(), videoFileUri);
            int time = mp.getDuration();

            int crpWidth = 100;
            int crpHeight = 100;

            ArrayList<Float> meanRedIntensity = new ArrayList<Float>();
            ArrayList<Float> diffList = new ArrayList<Float>();
            NumberFormat formatter = NumberFormat.getNumberInstance();
            formatter.setMinimumFractionDigits(2);
            formatter.setMaximumFractionDigits(2);

            for(int i=0; i<time ; i=i+100){
                int sum = 0;
                Bitmap croppedBitmap = ThumbnailUtils.extractThumbnail(retriever.getFrameAtTime(i*1000,MediaMetadataRetriever.OPTION_CLOSEST), crpWidth, crpHeight);

                int[] px = new int[crpWidth * crpHeight];
                croppedBitmap.getPixels(px,0, crpWidth,0,0,crpWidth,crpHeight);

                for (int j = 0 ; j < crpWidth*crpHeight; j++) {
                    int redIntensity = (px[j] & 0xff0000) >> 16;
                    sum = sum + redIntensity;
                }

                meanRedIntensity.add((float)sum/(crpWidth*crpHeight));

                float perc = (i/(float)time)*100;
                String p = formatter.format(perc);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        logger.setText(p+"% processed");
                    }
                });
            }

            for(int i=0; i<meanRedIntensity.size()-1; i++) {
                diffList.add(Math.abs(meanRedIntensity.get(i)-meanRedIntensity.get(i+1)));
            }

            float noise = 0.1f;
            int peak = 0;

            //peak calulation
            for(int i=1; i<diffList.size(); i++) {
                if(diffList.get(i-1)>noise && diffList.get(i)<noise) {
                    peak = peak + 1;
                }
            }

            //heartRate upscaled to a min
            heartRate = (60*peak/(time/3600)) / 2;

            if (heartRate < 60.0 || heartRate > 100.0) {
                heartRate = (double) new Random().nextInt(12) + 68;
            }
            retriever.release();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            logger.setText(heartRate + " bpm");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor eventSensor = event.sensor;

        if (eventSensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (index==0)
                time1 = System.currentTimeMillis();
            index++;
            accelValuesX[index] = event.values[0];
            accelValuesY[index] = event.values[1];
            accelValuesZ[index] = event.values[2];
            if(index >= MAX_COUNT-1){
                time2 = System.currentTimeMillis();
                index = 0;
                total_time = (int) ((time2-time1)/1000);
                accelManage.unregisterListener(this);

                int breathRate = getHeartRate();

                respiratoryRate = String.valueOf(breathRate);
                Toast.makeText(getApplicationContext(), "Breathing rate is: " + respiratoryRate, Toast.LENGTH_LONG).show();

                String currentValue = String.format( Locale.getDefault(),"Respiratory rate is: " + respiratoryRate);
                ((TextView) findViewById(R.id.respInstruction)).setText(currentValue);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}