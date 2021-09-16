package com.example.mobilecomputinghw1;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.Spinner;

import java.util.HashMap;

public class RecordSymptoms extends AppCompatActivity implements AdapterView.OnItemSelectedListener, View.OnClickListener {
    RatingBar ratingBar;
    String selectedItem = "Nausea";
    HashMap<String, String> symptomsMap= new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_symptoms);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Spinner symptomSelector = (Spinner)findViewById(R.id.symptom_selector);
        ArrayAdapter<String> symptomAdaptor = new ArrayAdapter<String>(RecordSymptoms.this,
                android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.symptoms));
        symptomAdaptor.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        symptomSelector.setAdapter(symptomAdaptor);
        symptomSelector.setOnItemSelectedListener(this);


        ratingBar = findViewById(R.id.ratingBar);
        symptomsMap.put("Cough", "0");
        symptomsMap.put("Diarrhoea", "0");
        symptomsMap.put("Fever", "0");
        symptomsMap.put("Feeling Tired", "0");
        symptomsMap.put("Headache", "0");
        symptomsMap.put("Loss of smell or taste","0");
        symptomsMap.put("Muscle Ache", "0");
        symptomsMap.put("Nausea", "0");
        symptomsMap.put("Shortness of breath","0");
        symptomsMap.put("Soar Throat", "0");


        Button setRatingButton = findViewById(R.id.setRatingButton);
        setRatingButton.setOnClickListener(this);
        Button saveRatingsButton = findViewById(R.id.saveRatingsButton);
        saveRatingsButton.setOnClickListener(this);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        selectedItem = parent.getSelectedItem().toString();
        ratingBar.setRating(Float.parseFloat(symptomsMap.get(selectedItem)));

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.setRatingButton){
            symptomsMap.put(selectedItem, Float.toString(ratingBar.getRating()));
        }
        if (v.getId() == R.id.saveRatingsButton){
            String symptomHashMap = symptomsMap.toString();
            Log.d("HashMap to string", symptomHashMap);
            Intent intent = new Intent(this, HeartRateCalculator.class);
            intent.putExtra("EXTRA_TEXT", symptomHashMap);
            startActivity(intent);
        }
    }
}