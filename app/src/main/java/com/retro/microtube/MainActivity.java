package com.retro.microtube;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

public class MainActivity extends Activity {

    public static String BACKEND_URL = "http://192.168.1.2:8080"; 

    private EditText searchQuery;
    private ListView resultsList;
    private Button btnStopAudio;
    private Button btnServer;
    private ArrayList<String> videoTitles = new ArrayList<String>();
    private ArrayList<String> videoIds = new ArrayList<String>();
    private ArrayAdapter<String> adapter;
    private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("MicroTubePrefs", MODE_PRIVATE);
        BACKEND_URL = prefs.getString("server_url", "http://192.168.1.2:8080");

        searchQuery = (EditText) findViewById(R.id.search_query);
        resultsList = (ListView) findViewById(R.id.results_list);
        btnStopAudio = (Button) findViewById(R.id.btn_stop_audio);
        btnServer = (Button) findViewById(R.id.btn_server);
        Button btnSearch = (Button) findViewById(R.id.btn_search);

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, videoTitles);
        resultsList.setAdapter(adapter);

        // Tap the ⚙ button to set Server URL
        btnServer.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showServerDialog();
            }
        });

        // Tap Go to search
        btnSearch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Hide soft keyboard
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(searchQuery.getWindowToken(), 0);

                String query = searchQuery.getText().toString().trim();
                if (query.length() > 0) {
                    new SearchTask().execute(query);
                } else {
                    Toast.makeText(MainActivity.this, "Please type something first!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnStopAudio.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent stopIntent = new Intent(MainActivity.this, AudioService.class);
                stopIntent.setAction(AudioService.ACTION_STOP);
                startService(stopIntent);
                btnStopAudio.setVisibility(View.GONE);
            }
        });

        resultsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String videoId = videoIds.get(position);
                String title = videoTitles.get(position);
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                intent.putExtra("VIDEO_ID", videoId);
                intent.putExtra("VIDEO_TITLE", title);
                startActivity(intent);
            }
        });
    }

    // Physical MENU button support on Samsung GT-I5500
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Server Settings");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            showServerDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showServerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Server URL");
        final EditText input = new EditText(this);
        input.setText(BACKEND_URL);
        builder.setView(input);

        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                BACKEND_URL = input.getText().toString().trim();
                prefs.edit().putString("server_url", BACKEND_URL).commit();
                Toast.makeText(MainActivity.this, "Saved: " + BACKEND_URL, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        btnStopAudio.setVisibility(View.VISIBLE);
    }

    private class SearchTask extends AsyncTask<String, Void, String> {
        private ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            dialog = ProgressDialog.show(MainActivity.this, "", "Searching...", true);
        }

        @Override
        protected String doInBackground(String... params) {
            videoTitles.clear();
            videoIds.clear();
            HttpURLConnection conn = null;
            try {
                String q = URLEncoder.encode(params[0], "UTF-8");
                URL url = new URL(BACKEND_URL + "/search?q=" + q + "&filter=videos");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setInstanceFollowRedirects(false);

                int responseCode = conn.getResponseCode();
                if (responseCode == 301 || responseCode == 302) {
                    return "Redirected to HTTPS (TLS unsupported)";
                }
                if (responseCode != 200) {
                    return "HTTP Error: " + responseCode;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject response = new JSONObject(sb.toString());
                JSONArray items = response.getJSONArray("items");

                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String title = item.optString("title", "No Title");
                    String rawUrl = item.optString("url", "");
                    
                    if (rawUrl.contains("v=")) {
                        String id = rawUrl.substring(rawUrl.indexOf("v=") + 2);
                        videoTitles.add(title);
                        videoIds.add(id);
                    }
                }
                return null;
            } catch (Exception e) {
                return e.getClass().getSimpleName() + ": " + e.getMessage();
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        @Override
        protected void onPostExecute(String errorMsg) {
            dialog.dismiss();
            if (errorMsg == null) {
                if (videoTitles.isEmpty()) {
                    Toast.makeText(MainActivity.this, "No videos found", Toast.LENGTH_SHORT).show();
                } else {
                    adapter.notifyDataSetChanged();
                }
            } else {
                Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
            }
        }
    }
}
