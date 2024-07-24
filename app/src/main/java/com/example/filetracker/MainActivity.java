package com.example.filetracker;

import static java.security.AccessController.getContext;

import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import android.os.AsyncTask;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;

public class MainActivity extends AppCompatActivity {
    private static final String PREF_LOGGED_IN = "loggedIn";
    private EditText employeeEditText, divisionEditText, editTextPassword;
    private DBHandler dbHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Check if user is already logged in
        // Check if the user is already logged in
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean loggedIn = sharedPreferences.getBoolean(PREF_LOGGED_IN, false);
        if (loggedIn) {
            // If already logged in, directly navigate to Admin activity
            String username = sharedPreferences.getString("USERNAME", "");
            String division = sharedPreferences.getString("DIVISION", "");
            if (!username.isEmpty()) {
                Intent intent = new Intent(MainActivity.this, Admin.class);
                intent.putExtra("USERNAME", username);
                intent.putExtra("DIVISION", division);
                startActivity(intent);
                finish(); // Finish MainActivity so the user cannot go back to it
                return;
            }
        }
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        employeeEditText = findViewById(R.id.idEmployee);
        divisionEditText = findViewById(R.id.idDivision);
        Button loginButton = findViewById(R.id.Login);
        editTextPassword = findViewById(R.id.password);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);



        //--------------------------------------------------------------------------------------------//

        // Set click listener for login button
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Retrieve entered employee name, division, and password
                String employeeName = employeeEditText.getText().toString().trim();
                String division = divisionEditText.getText().toString().trim();
                String password = editTextPassword.getText().toString();

                // Hardcoded password
                final String PASSWORD = "goawrd2000";
                // Check if all fields are not empty
                if (!employeeName.isEmpty() && !division.isEmpty() && password.equals(PASSWORD)) {
                    // Insert data into the Users table
                    dbHandler.insertUserData(employeeName, division);

                    // Update login status to true
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean(PREF_LOGGED_IN, true);
                    editor.putString("USERNAME", employeeName);// Store the username
                    editor.putString("DIVISION",division);
                    editor.apply();

                    // Navigate to the Admin activity
                    Intent intent = new Intent(MainActivity.this, Admin.class);
                    intent.putExtra("USERNAME", employeeName);
                    intent.putExtra("DIVISION", division);

                    startActivity(intent);

                    // Finish MainActivity so the user cannot go back to it
                    finish();
                } else {
                    // Fields are empty
                    Toast.makeText(MainActivity.this, "Please enter  username, division, and password", Toast.LENGTH_SHORT).show();
                }
            }
        });



        // Create an instance of DBHandler
        dbHandler = new DBHandler(this);
        downloadAndStoreCSVData();

//        // Inside onCreate() method after initializing the database handler
//        AutoCompleteTextView autoCompleteTextView = findViewById(R.id.idDivision);
//
//// Assuming getDivisions() method returns an ArrayList<String> containing division names
//        ArrayList<String> divisionsList = dbHandler.getDivisions();
//
//// Remove duplicates from the divisions list
//        HashSet<String> uniqueDivisionsSet = new HashSet<>(divisionsList);
//        ArrayList<String> uniqueDivisionsList = new ArrayList<>(uniqueDivisionsSet);
//
//        if (uniqueDivisionsList != null && !uniqueDivisionsList.isEmpty()) {
//            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, uniqueDivisionsList);
//            autoCompleteTextView.setAdapter(adapter);
//        } else {
//            // Handle case where no unique divisions are found in the database
//            Toast.makeText(this, "No unique divisions found", Toast.LENGTH_SHORT).show();
//        }

    }

    //--------------------------------------------------------------------------------------------//
    //Csv Link
    private void downloadAndStoreCSVData() {
        // URL of the CSV file to download
        String csvUrl = "https://docs.google.com/spreadsheets/d/e/2PACX-1vRnjsCiY_MV7PBvE8qkUjSqSuFrfyAQlpuoDbJ2WsItmd4LmswTjsTkFc-GQ6z2-Uluqn4fOC299enn/pub?gid=1956630541&single=true&output=csv";

        // Execute AsyncTask to download CSV data
        new DownloadCSVTask().execute(csvUrl);
    }

    //--------------------------------------------------------------------------------------------//

    //Background Task Network + Csv Data parse
    private class DownloadCSVTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            // Background task to download CSV data
            StringBuilder resultBuilder = new StringBuilder();
            HttpURLConnection urlConnection = null;
            try {
                URL url = new URL(urls[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                InputStream inputStream = urlConnection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    resultBuilder.append(line).append("\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
            return resultBuilder.toString();
        }

        @Override
        protected void onPostExecute(String csvData) {
            // Post-execution task to parse CSV data and store it locally
            if (!csvData.isEmpty()) {
                parseCSVAndStoreLocally(csvData);

                // Populate the dropdown list after CSV data is processed
                populateDivisionDropdown();
            } else {
                Toast.makeText(MainActivity.this, "Failed to download CSV data", Toast.LENGTH_SHORT).show();
            }
        }

    }

    // Method to populate the division dropdown list
    private void populateDivisionDropdown() {
        AutoCompleteTextView autoCompleteTextView = findViewById(R.id.idDivision);

        // Get the list of divisions from the database
        ArrayList<String> divisionsList = dbHandler.getDivisions();

        // Remove duplicates from the divisions list
        HashSet<String> uniqueDivisionsSet = new HashSet<>(divisionsList);
        ArrayList<String> uniqueDivisionsList = new ArrayList<>(uniqueDivisionsSet);

        if (uniqueDivisionsList != null && !uniqueDivisionsList.isEmpty()) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, uniqueDivisionsList);
            autoCompleteTextView.setAdapter(adapter);
        } else {
            // Handle case where no unique divisions are found in the database
            Toast.makeText(this, "No unique divisions found", Toast.LENGTH_SHORT).show();
        }
    }
    //--------------------------------------------------------------------------------------------//

    private void parseCSVAndStoreLocally(String csvData) {
        // Parse CSV data and store it in the database
        // Split CSV data into lines
        String[] lines = csvData.split("\n");

        // Open database for writing
        SQLiteDatabase db = dbHandler.getWritableDatabase();

        // Loop through each line of CSV data and insert into database
        for (String line : lines) {
            // Split line into columns (assuming comma-separated)
            String[] columns = line.split(",");

            // Insert data into the database only if it doesn't already exist
            if (columns.length >= 2) {
                String employeeName = columns[1].trim();
                String division = columns[0].trim();

                // Check if the record already exists in the database
                if (!isRecordExists(db, employeeName, division)) {
                    ContentValues values = new ContentValues();
                    values.put("EmployeeName", employeeName);
                    values.put("Division", division);
                    db.insert("File", null, values);
                }
            }
        }

        // Close the database
        db.close();

        Toast.makeText(MainActivity.this, "Refreshing...Please wait!", Toast.LENGTH_SHORT).show();
        Toast.makeText(MainActivity.this, "Done.", Toast.LENGTH_SHORT).show();
    }

    //--------------------------------------------------------------------------------------------//

    //     Method to check if a record already exists in the database
    private boolean isRecordExists(SQLiteDatabase db, String employeeName, String division) {
        Cursor cursor = db.rawQuery("SELECT * FROM File WHERE EmployeeName = ? AND Division = ?", new String[]{employeeName, division});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }


    private void createEmployeeLocally(String employeeName, String division) {
        // Insert the new employee into the local database
        dbHandler.insertUserData(employeeName, division);
    }

    // Define a method to start the Admin activity with the username
}
