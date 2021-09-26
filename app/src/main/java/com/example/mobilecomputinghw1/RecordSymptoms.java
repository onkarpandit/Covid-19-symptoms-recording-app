package com.example.mobilecomputinghw1;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.HashMap;

public class RecordSymptoms extends AppCompatActivity implements AdapterView.OnItemSelectedListener, View.OnClickListener {
    RatingBar ratingBar;
    String currentItem = "Nausea";
    HashMap<String, String> symptoms = new HashMap<>();
    String vitalSigns;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_symptoms);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        vitalSigns = (String) intent.getStringExtra("HEART_RESPIRATORY_RATE");

        Spinner symptomSelector = (Spinner)findViewById(R.id.symptom_selector);
        ArrayAdapter<String> symptomAdaptor = new ArrayAdapter<String>(RecordSymptoms.this,
                android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.symptoms));
        symptomAdaptor.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        symptomSelector.setAdapter(symptomAdaptor);
        symptomSelector.setOnItemSelectedListener(this);


        ratingBar = findViewById(R.id.ratingBar);
        initialiseHashMap();


        Button setRatingButton = findViewById(R.id.setRatingButton);
        setRatingButton.setOnClickListener(this);
        Button saveRatingsButton = findViewById(R.id.uploadToDb);
        saveRatingsButton.setOnClickListener(this);
    }

    private void initialiseHashMap() {
        symptoms.put("Cough", "0");
        symptoms.put("Diarrhoea", "0");
        symptoms.put("Fever", "0");
        symptoms.put("Feeling Tired", "0");
        symptoms.put("Headache", "0");
        symptoms.put("Loss of smell or taste","0");
        symptoms.put("Muscle Ache", "0");
        symptoms.put("Nausea", "0");
        symptoms.put("Shortness of breath","0");
        symptoms.put("Soar Throat", "0");
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        currentItem = parent.getSelectedItem().toString();
        ratingBar.setRating(Float.parseFloat(symptoms.get(currentItem)));

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.setRatingButton){
            symptoms.put(currentItem, Float.toString(ratingBar.getRating()));
        }
        if (v.getId() == R.id.uploadToDb){
            uploadSignsToDB();
        }
    }


    private void uploadSignsToDB() {
        Intent intent = new Intent(RecordSymptoms.this, HeartRateCalculator.class);
        intent.putExtra("SYMPTOMS", symptoms.toString());
        startActivity(intent);
    }
}