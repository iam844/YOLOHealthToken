package com.iam844.adityajaiswal.YOLOHealthToken;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import static com.iam844.adityajaiswal.YOLOHealthToken.ServerConstants.API_URL;

public class MainActivity extends AppCompatActivity {

    private static final String KIOSKID_KEY = "kioskid";
    public static int STOP_CONNECTION_UPDATE_ON_ACTIVITY_CHANGE = 0;

    public String enteredKioskId;

    TextView getTokenNumber;
    Bundle jsonBundle;

    Thread updateConnectionThread;
    ImageView ivIsConnected;
    EditText etKioskId;
    Button btnRun;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getTokenNumber = findViewById(R.id.tokenNumber);

        ivIsConnected = findViewById(R.id.ivIsConnected);
        etKioskId = findViewById(R.id.et_KioskId);
        btnRun = findViewById(R.id.btnRun);

        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send(v);
            }
        });
    }

    public boolean checkNetworkConnection() {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        boolean isConnected = false;
        if (networkInfo != null && (isConnected = networkInfo.isConnected())) {
            // show "Connected" & type of network "WIFI or MOBILE"
            if (networkInfo.getTypeName().equalsIgnoreCase("wifi")) {
                ivIsConnected.setImageResource(R.drawable.ic_wifi_green_48dp);
            }
            else {
                ivIsConnected.setImageResource(R.drawable.ic_mobile_green_48dp);
            }

        } else {
            // show "Not Connected"
            ivIsConnected.setImageResource(R.drawable.ic_wifi_red_48dp);
        }

        return isConnected;
    }

    private String httpPost(String myUrl) throws IOException, JSONException {

        String jsonResponse = "";
        int responseCode;

        URL url = new URL(myUrl);

        // create HttpURLConnection
        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
        httpURLConnection.setReadTimeout(10000);
        httpURLConnection.setConnectTimeout(15000);
        httpURLConnection.setRequestMethod("POST");
        httpURLConnection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        httpURLConnection.setDoInput(true);
        httpURLConnection.setDoOutput(true);

        // build JSON object
        JSONObject jsonObject = buildJsonObject();
        Log.d("JSON Object ",jsonObject.toString());

        // add JSON content to POST request body
        setPostRequestContent(httpURLConnection, jsonObject);

        // make POST request to the given URL
        httpURLConnection.connect();

        // return response code
        responseCode = httpURLConnection.getResponseCode();

        // get JSON response
        jsonResponse = getRequestContent(httpURLConnection, responseCode, jsonResponse);

        return jsonResponse;
    }

    private class HTTPAsyncTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            // params comes from the execute() call: params[0] is the url.
            try {
                try {
                    String params =  httpPost(urls[0]);
                    Log.d("HTTPAsyncTask ", params);
                    return params;

                } catch (JSONException e) {
                    e.printStackTrace();
                    return "Error!";
                }
            } catch (IOException e) {
                return "Unable to retrieve web page. URL may be invalid.";
            }
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {

            jsonBundle = new Bundle();
            jsonBundle.putString("JSONString", result);
            jsonBundle.putString("EnteredKioskID", etKioskId.getText().toString());

            Log.d("ON POST EXECUTE ", "KioskID : " + etKioskId.getText().toString() + "RESULT : " + result);

            Intent tokenActivityIntent = new Intent(MainActivity.this, TokenActivity.class);
            tokenActivityIntent.putExtras(jsonBundle);
            startActivity(tokenActivityIntent);

            //Stop updateConnection thread
            STOP_CONNECTION_UPDATE_ON_ACTIVITY_CHANGE = 1;
        }
    }

    public void send(View view) {
        Toast.makeText(this, "Current Token Number", Toast.LENGTH_SHORT).show();
        // perform HTTP POST request
        if(checkNetworkConnection()) {

            //Save Kiosk ID for periodic calling of API
            enteredKioskId = etKioskId.getText().toString();
            new HTTPAsyncTask().execute(API_URL+ "/Utils/get/current/token/?kioskid=" + etKioskId.getText().toString());
        }
        else {
            Toast.makeText(this, "Not Connected!", Toast.LENGTH_SHORT).show();
        }
    }

    private JSONObject buildJsonObject() throws JSONException {

        JSONObject jsonObject = new JSONObject();
        jsonObject.accumulate(KIOSKID_KEY, etKioskId.getText().toString());

        return jsonObject;
    }

    private void setPostRequestContent(HttpURLConnection conn, JSONObject jsonObject) throws IOException {

        OutputStream os = conn.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
        writer.write(jsonObject.toString());
        Log.i(MainActivity.class.toString(), jsonObject.toString());
        writer.flush();
        writer.close();
        os.close();
    }

    private String getRequestContent(HttpURLConnection httpURLConnection, int responseCode, String jsonResponse) throws IOException {

        if (responseCode == HttpsURLConnection.HTTP_OK) {

            String line;

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));

            while ((line = bufferedReader.readLine()) != null) {
                jsonResponse += line;
            }
            Log.d("JSON Response ", jsonResponse);
        } else {
            jsonResponse = "";
            Log.d("JSON Response ",jsonResponse);
        }

        return  jsonResponse;
    }

    @Override
    protected void onStart() {
        super.onStart();

        updateConnectionThread = new Thread() {
            @Override
            public void run(){
                while(STOP_CONNECTION_UPDATE_ON_ACTIVITY_CHANGE == 0){
                    try {
                        Thread.sleep(1000);  //1000ms = 1 sec
                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                checkNetworkConnection();
                            }
                        });
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        updateConnectionThread.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //Stop updateConnection thread
        STOP_CONNECTION_UPDATE_ON_ACTIVITY_CHANGE = 1;
    }
}