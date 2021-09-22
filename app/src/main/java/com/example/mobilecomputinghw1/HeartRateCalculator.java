package com.example.mobilecomputinghw1;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

public class HeartRateCalculator extends AppCompatActivity implements SensorEventListener  {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_ID = 1;
    private String heartRate = "0";
    private String respiratoryRate = "0";
    private HashMap<String, String> symptoms;
    Database database;

    private final VideoService cameraService = new VideoService(this);
    private EntryStorage store;

    private final int measurementInterval = 45;
    private final int measurementLength = 50000;
    private final int clipLength = 3500;

    private int valeyDetections = 0;
    private int ticksPassed = 0;

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

        Button measureHeartRateButton = (Button) findViewById(R.id.heartRateButton);
        Button measureRespiratoryButton = (Button) findViewById(R.id.respRateButton);
        Button upload_signs_button = (Button) findViewById(R.id.upload_signs_button);
        Button recordSymptoms = (Button) findViewById(R.id.record_symptoms);

        logger = (TextView) findViewById(R.id.heartRateInstruction);

        database = new Database(this);

        initialiseHashMap();

        Intent intent = getIntent();
        String symptomsString = (String) intent.getStringExtra("EXTRA_TEXT");
        if(symptomsString != null){
            symptomsString = symptomsString.substring(1);
            symptomsString = symptomsString.substring(0, symptomsString.length() - 1);

            String[] pairs = symptomsString.split(",");
            for (int i=0;i<pairs.length;i++) {
                String pair = pairs[i].trim();
                String[] keyValue = pair.split("=");
                symptoms.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                REQUEST_ID);

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
                ((TextView) findViewById(R.id.respInstruction)).setText("Calculating...");
                accelManage = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                senseAccel = accelManage.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                accelManage.registerListener(HeartRateCalculator.this, senseAccel, SensorManager.SENSOR_DELAY_NORMAL);
            }
        });


        upload_signs_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                symptoms.put("Heart Rate", heartRate);
                symptoms.put("Respiratory Rate", respiratoryRate);
                boolean flag = database.insertData(symptoms);
                if (flag) {
                    Toast.makeText(getApplicationContext(), "Successfully inserted data", Toast.LENGTH_LONG).show();
                }

                Intent intent = new Intent(HeartRateCalculator.this, HeartRateCalculator.class);
                startActivity(intent);

            }
        });

        recordSymptoms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openRecordSymptoms();
            }
        });

    }

    public void openRecordSymptoms() {
        Intent intent = new Intent(this, RecordSymptoms.class);
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
                heartRate = String.valueOf(heart_beat);

                String currentValue = String.format( Locale.getDefault(),"HEART_BEAT: " + heartRate);
                ((TextView) findViewById(R.id.heartRateInstruction)).setText(currentValue);

                cameraService.stop();
            }
        };

        timer.start();
    }

    private void measureHeartRate() {
        logger.setText("Calculating...");

        valleys = new CopyOnWriteArrayList<>();

        TextureView cameraTextureView = findViewById(R.id.textureView2);

        SurfaceTexture previewSurfaceTexture = cameraTextureView.getSurfaceTexture();
        if (previewSurfaceTexture != null) {
            // this first appears when we close the application and switch back - TextureView isn't quite ready at the first onResume.
            Surface previewSurface = new Surface(previewSurfaceTexture);

            cameraService.start(previewSurface);
            measurePulse(cameraTextureView, cameraService);
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

    private void initialiseHashMap(){
        symptoms = new HashMap<>();
        symptoms.put("Heart Rate","0");
        symptoms.put("Respiratory Rate","0");
        symptoms.put("Nausea", "0");
        symptoms.put("Headache", "0");
        symptoms.put("Diarrhoea", "0");
        symptoms.put("Soar Throat", "0");
        symptoms.put("Fever", "0");
        symptoms.put("Muscle Ache", "0");
        symptoms.put("Loss of smell or taste", "0");
        symptoms.put("Cough", "0");
        symptoms.put("Shortness of breath", "0");
        symptoms.put("Feeling Tired", "0");
    }
}