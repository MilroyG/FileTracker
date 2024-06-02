package com.example.filetracker;

import static android.content.ContentValues.TAG;
import static com.example.filetracker.CSVReader.fetchEmployeeListFromCSV;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;

import android.view.Gravity;
import android.view.LayoutInflater;

import android.view.View;


import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;


import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;


import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;


import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.mlkit.vision.text.Text;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.MediaStore;


public class Admin extends AppCompatActivity implements View.OnClickListener {

    private EditText searchEditText;
    private TableLayout resultsTable;
    private DBHandler dbHandler;

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private EditText input;


    // URLs for API endpoints
    private static final String URL2 = "https://goawrd.gov.in/file-tracker/scan?code=<code>&employee=<employee>&username=<username>";
    private static final String URL3 = "http://goawrd.gov.in/file-tracker/regcode?code=<code>&filename=<filename>";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);
      // setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // Force portrait orientation
        findViewById(R.id.search).setOnClickListener(this);
        Button scanBtn = findViewById(R.id.scanQRButton);
        scanBtn.setOnClickListener(this);
        String username = getIntent().getStringExtra("USERNAME");
        String division = getIntent().getStringExtra("DIVISION");
        if (username != null) {
            Toast.makeText(this, "Welcome, " + username +" (" + division + ")", Toast.LENGTH_SHORT).show();
        }

        // Create an instance of DBHandler
        dbHandler = new DBHandler(this);
        downloadAndStoreCSVData();
           }

    //--------------------------------------------------------------------------------------------//

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.scanQRButton) {
            if (NetworkUtils.isNetworkConnected(getApplicationContext())) {
                IntentIntegrator intentIntegrator = new IntentIntegrator(this);
                intentIntegrator.setPrompt("Scan a barcode or QR Code");
                intentIntegrator.setOrientationLocked(true);
                intentIntegrator.initiateScan();
            } else {
                Toast.makeText(Admin.this, "No internet connection", Toast.LENGTH_SHORT).show();
            }
        } else if (view.getId() == R.id.search) {
            Intent intent = new Intent(Admin.this, Search.class);
            startActivity(intent);
       }
    }

    //--------------------------------------------------------------------------------------------//


    //Csv Link
    private void downloadAndStoreCSVData() {
        // URL of the CSV file to download
        String csvUrl = "https://docs.google.com/spreadsheets/d/e/2PACX-1vRnjsCiY_MV7PBvE8qkUjSqSuFrfyAQlpuoDbJ2WsItmd4LmswTjsTkFc-GQ6z2-Uluqn4fOC299enn/pub?gid=1956630541&single=true&output=csv";

        // Execute AsyncTask to download CSV data
        new Admin.DownloadCSVTask().execute(csvUrl);

    }


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
            } else {
                Toast.makeText(Admin.this, "Failed to download CSV data", Toast.LENGTH_SHORT).show();
            }
        }
    }

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

//        Toast.makeText(Admin.this, "Refreshing...Please wait!", Toast.LENGTH_SHORT).show();
//        Toast.makeText(Admin.this, "Done.", Toast.LENGTH_SHORT).show();
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
        dbHandler.insertData(employeeName, division);
    }






    //--------------------------------------------------------------------------------------------//
                                        // Scanning of QrCode
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Lock the orientation to portrait mode
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    // Inside your camera capture method
    private void captureImage() {
        // Launch camera to capture image
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IntentIntegrator.REQUEST_CODE) {
            // Handle QR code scan result
            IntentResult intentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if (intentResult != null) {
                if (intentResult.getContents() != null) {
                    String scannedData = intentResult.getContents();
                    String username = getIntent().getStringExtra("USERNAME");
                    Toast.makeText(this, "Scanned data: " + scannedData, Toast.LENGTH_SHORT).show();
                    openDialogForEmployeeName(scannedData, username);
                } else {
                    Toast.makeText(this, "Scan canceled or failed", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // Handle image capture result
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            if (imageBitmap != null) {
                recognizeText(imageBitmap);
            } else {
                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show();
            }

        }
    }
    private Bitmap rotateImageIfRequired(Context context, Bitmap img) {
        // Rotate the image based on EXIF data or other methods
        Matrix matrix = new Matrix();
        matrix.postRotate(170); // Rotate as needed, adjust the degree as necessary
        return Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
    }



    // Inside the recognizeText method
    private void recognizeText(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(new OnSuccessListener<Text>() {
                    @Override
                    public void onSuccess(Text visionText) {
                        String recognizedText = visionText.getText();
                        Log.d("RecognizedText", "Recognized Text: " + recognizedText); // Add this log statement
                        if(input != null) {
                            input.setText(recognizedText); // Set recognized text to the input field
                        } else {
                            Log.e("RecognizedText", "Input EditText is null");
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(Admin.this, "Text recognition failed", Toast.LENGTH_SHORT).show();
                        Log.e("RecognizedText", "Text recognition failed: " + e.getMessage()); // Add this log statement
                    }
                });
    }


    //--------------------------------------------------------------------------------------------//
                                 // Dialog Box for Employee

    private void openDialogForEmployeeName(final String scannedData, final String username) {
        final String[] parts = scannedData.split(";");
        final String code = parts[0];
        final String division = getIntent().getStringExtra("DIVISION");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("The file is marked to:");
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_enter_employee_name, null);
        final MaterialAutoCompleteTextView employeeAutoCompleteTextView = dialogView.findViewById(R.id.employee_autocomplete);
        final TextView textCode = dialogView.findViewById(R.id.text_code);
        final TextView textUsername = dialogView.findViewById(R.id.text_username);
        textCode.setText("Code: " + code);
        textUsername.setText("Username: " + username);
        // Making code and username non-editable
        textCode.setEnabled(false);
        textUsername.setEnabled(false);
        builder.setView(dialogView);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String selectedEmployee = employeeAutoCompleteTextView.getText().toString();
                if (!selectedEmployee.isEmpty()) {
                    String employeeName = selectedEmployee.trim();
                    sendRequestToURL2(code, username, employeeName);

                    Toast.makeText(Admin.this, "Submitted....", Toast.LENGTH_SHORT).show();

                } else {
                    Toast.makeText(Admin.this, "Please select an employee", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        // Fetch employee list and populate autocomplete text view
        // Assuming you have an instance of DBHandler called dbHandler
        ArrayList<String> employeeList = dbHandler. getEmployeeByDivision(division);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(Admin.this, android.R.layout.simple_dropdown_item_1line, employeeList);
        employeeAutoCompleteTextView.setAdapter(adapter);
    }

    //--------------------------------------------------------------------------------------------//

                                //RequestUrl2


    private void sendRequestToURL2(final String code, final String username, final String employeeName) {
        String url = URL2.replace("<code>", code)
                .replace("<username>", username)
                .replace("<employee>", employeeName);

        Log.d("Admin", "URL2: " + url);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS) // Set connection timeout
                .readTimeout(30, TimeUnit.SECONDS)    // Set read timeout
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(Admin.this, "Error: Network failure", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    final String responseBody = response.body().string();
                    Log.d("Admin", "Response body: " + responseBody);


                    if (responseBody.equals("Ok")) {

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(Admin.this, "Entry saved in database", Toast.LENGTH_SHORT).show();
                                Log.d("Admin", "Toast displayed: Entry saved in database");
                            }
                        });

                    } else if (responseBody.equals("File not in System")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                promptForFilename(code);
                            }
                        });
                    }


                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(Admin.this, "Error: Unsuccessful response", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }







    //--------------------------------------------------------------------------------------------//

                            //Filename Dialog

    private void promptForFilename(final String code) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);


        // Customize the dialog title and icon
        builder.setTitle("This is a new file.")
                .setIcon(R.drawable.file_add)
                .setMessage("Please enter the full file subject");


        // Inflate a custom layout for the dialog content
        View dialogLayout = getLayoutInflater().inflate(R.layout.custom_filename_dialog, null);
        ScrollView scrollView = dialogLayout.findViewById(R.id.scroll_view);
         input = dialogLayout.findViewById(R.id.filename_edit_text);
        final ImageView cameraButton = dialogLayout.findViewById(R.id.ocr_button);


        // Set custom positive button with color
        builder.setPositiveButton("OK", null); // Set null to delay the automatic closing of the dialog
        // Set custom negative button with color
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        // Create the dialog
        final AlertDialog dialog = builder.create();

        // Set the positive button listener after creating the dialog
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                positiveButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String filename = input.getText().toString().trim();
                        if (!filename.isEmpty()) {
                            sendRequestToURL3(code, filename);
                            dialog.dismiss(); // Dismiss the dialog only when conditions are met
                        } else {
                            Toast.makeText(Admin.this, "Please enter filename", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

                // Set onClickListener for the camera button
                cameraButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Invoke camera to capture image for text recognition
                        dispatchTakePictureIntent();
                    }
                });
            }
        });

        // Set the custom view containing the ScrollView
        dialog.setView(dialogLayout);
        // Show the dialog
        dialog.show();
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }



    //--------------------------------------------------------------------------------------------//
                                //RequestUrl3

    private void sendRequestToURL3(final String code, final String filename) {
        String url = URL3.replace("<code>", code)
                .replace("<filename>", filename);

        Log.d("Admin", "URL3: " + url);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS) // Set connection timeout
                .readTimeout(30, TimeUnit.SECONDS)    // Set read timeout
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(Admin.this, "Error: Network failure", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    final String responseBody = response.body().string();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(Admin.this, "Response from URL3: " + responseBody, Toast.LENGTH_SHORT).show();
                            initiateScanningProcess();
                        }
                    });

                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(Admin.this, "Error: Unsuccessful response", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }
    private void initiateScanningProcess() {
        IntentIntegrator intentIntegrator = new IntentIntegrator(this);
        intentIntegrator.setPrompt("Scan a barcode or QR Code");
        intentIntegrator.setOrientationLocked(true);
        intentIntegrator.initiateScan();
    }

   }