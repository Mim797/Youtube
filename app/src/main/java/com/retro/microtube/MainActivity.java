package com.retro.microtube;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
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

    // WARNING: Do NOT use https:// here. Android 2.2 will crash on modern SSL handshakes.
    // Use an HTTP instance or proxy.
    public static final String BACKEND_URL = "http://pipedapi.kavin.rocks"; 

    private EditText searchQuery;
    private ListView resultsList;
    private Button btnStopAudio;
    private ArrayList<String> videoTitles = new ArrayList<String>();
    private ArrayList<String> videoIds = new ArrayList<String>();
    private ArrayAdapter<String> adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        searchQuery = (EditText) findViewById(R.id.search_query);
        resultsList = (ListView) findViewById(R.id.results_list);
        btnStopAudio = (Button) findViewById(R.id.btn_stop_audio);
        Button btnSearch = (Button) findViewById(R.id.btn_search);

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, videoTitles);
        resultsList.setAdapter(adapter);

        btnSearch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String query = searchQuery.getText().toString().trim();
                if (query.length() > 0) {
                    new SearchTask().execute(query);
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
                conn.setInstanceFollowRedirects(false); // Stop auto-redirecting to HTTPS
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");

                int responseCode = conn.getResponseCode();
                if (responseCode == 301 || responseCode == 302) {
                    return "Error: Server redirected to HTTPS (TLS unsupported on 2.2)";
                }
                if (responseCode != 200) {
                    return "Server HTTP Error: " + responseCode;
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
                return null; // Null means success
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
                // Shows the real technical error on your screen
                Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
            }
        }
    }
}
