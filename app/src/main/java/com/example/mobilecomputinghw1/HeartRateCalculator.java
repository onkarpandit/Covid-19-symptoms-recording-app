package com.example.mobilecomputinghw1;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
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
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

public class HeartRateCalculator extends AppCompatActivity implements SensorEventListener  {
    private static final int VIDEO_CAPTURE = 101;
    String filePath;
    private int heartRate = 0;
    private String respiratoryRate = "0";
    Database database;
    private EntryStorage store;

    private final int measurementInterval = 45;
    private final int measurementLength = 50000;
    private final int clipLength = 3500;

    private int valeyDetections = 0;
    private int ticksPassed = 0;
    private Uri fileUri;

    private CopyOnWriteArrayList<Long> valleys;

    private CountDownTimer timer;

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

    private boolean isCameraPermitted() {
        if (getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAMERA_ANY)){
            return true;
        } else {
            return false;
        }
    }

    void configurePermissions() {
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
        Intent intent = new Intent(this, RecordSymptoms.class);
        intent.putExtra("HEART_RESPIRATORY_RATE", String.valueOf(heartRate)+","+respiratoryRate);
        startActivity(intent);
    }

    private boolean valeyDetection() {
        final int valleyDetectionWindowSize = 13;
        CopyOnWriteArrayList<DataPoint<Integer>> subList = store.getLastStdValues(valleyDetectionWindowSize);
        if (subList.size() < valleyDetectionWindowSize) {
            return false;
        } else {
            Integer referenceValue = subList.get((int) Math.ceil(valleyDetectionWindowSize / 2)).measurement;

            for (DataPoint<Integer> measurement : subList) {
                if (measurement.measurement < referenceValue) return false;
            }

            // filter out consecutive measurements due to too high measurement rate
            return (!subList.get((int) Math.ceil(valleyDetectionWindowSize / 2)).measurement.equals(
                    subList.get((int) Math.ceil(valleyDetectionWindowSize / 2) - 1).measurement));
        }
    }

    void measurePulse(final TextureView textureView, final VideoService cameraService) {

        // 20 times a second, get the amount of red on the picture.
        // detect local minimums, calculate pulse.

        store = new EntryStorage();

        valeyDetections = 0;

        timer = new CountDownTimer(measurementLength, measurementInterval) {
            @Override
            public void onTick(long millisUntilFinished) {
                // skip the first measurements, which are broken by exposure metering
                if (clipLength > (++ticksPassed * measurementInterval)) return;

                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run(){
                        Bitmap currentBitmap = textureView.getBitmap();
                        int pixelCount = textureView.getWidth() * textureView.getHeight();
                        int measurement = 0;
                        int[] pixels = new int[pixelCount];

                        currentBitmap.getPixels(pixels, 0, textureView.getWidth(), 0, 0, textureView.getWidth(), textureView.getHeight());

                        // extract the red component
                        // https://developer.android.com/reference/android/graphics/Color.html#decoding
                        for (int pixelIndex = 0; pixelIndex < pixelCount; pixelIndex++) {
                            measurement += (pixels[pixelIndex] >> 16) & 0xff;
                        }
                        // max int is 2^31 (2147483647) , so width and height can be at most 2^11,
                        // as 2^8 * 2^11 * 2^11 = 2^30, just below the limit

                        store.add(measurement);

                        if (valeyDetection()) {
                            valeyDetections += 1;
                            valleys.add(store.getLastTimestamp().getTime());
                        }
                    }
                });
                thread.start();
            }

            @Override
            public void onFinish() {
                CopyOnWriteArrayList<DataPoint<Float>> stdValues = store.getStdValues();

                float heartbeat = (60f * (valeyDetections - 1) / (Math.max(1, (valleys.get(valleys.size() - 1) - valleys.get(0)) / 1000f)));
                int heart_beat = Math.round(heartbeat);
                heartRate = heart_beat;

                String currentValue = String.format( Locale.getDefault(),"HEART_BEAT: " + heartRate);
                ((TextView) findViewById(R.id.heartRateInstruction)).setText(currentValue);

                cameraService.stop();
            }
        };

        timer.start();
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

    private File createFile() throws IOException {
        // Create an image file name
        String imageFileName = "myvideo";
        File storageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM), "Camera");
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".mp4",         /* suffix */
                storageDir      /* directory */
        );


        return image;
    }

    private void measureHeartRate() {

        Log.v("myTag","FAB recording");
        File mediaFile = null;
        try {
            mediaFile = createFile();
        } catch (IOException ex) {
            Log.v("myTag","Exception");
            return;
        }


        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT,5);

        fileUri = FileProvider.getUriForFile(HeartRateCalculator.this,
                BuildConfig.APPLICATION_ID + ".provider",
                mediaFile);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
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

            File videoFile=new File(fileUri.toString());

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

                //get a frame at every 100 milliseconds. Therefore 450 data-points for a 45 sec video.
                //croppedBitmap is a centered bitmap of crpWidth and crpHeight
                int sum = 0;
                Bitmap croppedBitmap = ThumbnailUtils.extractThumbnail(retriever.getFrameAtTime(i*1000,MediaMetadataRetriever.OPTION_CLOSEST), crpWidth, crpHeight);

                //stored pixels values in px[]
                int[] px = new int[crpWidth * crpHeight];
                croppedBitmap.getPixels(px,0, crpWidth,0,0,crpWidth,crpHeight);

                //get red value for a px. Calculate sum of all red values.
                for (int j = 0 ; j < crpWidth*crpHeight; j++) {
                    int redIntensity = (px[j] & 0xff0000) >> 16;
                    sum = sum + redIntensity;
                }
                //store average red color of each frame in meanRedIntensity
                meanRedIntensity.add((float)sum/(crpWidth*crpHeight));

                //progress calculation for heartRate TextView
                float perc = (i/(float)time)*100;
                String p = formatter.format(perc);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        logger.setText(p+"% processed");
                    }
                });
            }

            //create a diffList to store changes in redInstensity values in recorded frames.
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
            heartRate = (60*peak/(time/1000));

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