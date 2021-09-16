package com.example.mobilecomputinghw1;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Call Heart Rate Activity
        Button heartRateButton = (Button) findViewById(R.id.getStarted);

        heartRateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent heartInt = new Intent(MainActivity.this, HeartRateCalculator.class);
                startActivity(heartInt);
            }
        });
    }
}