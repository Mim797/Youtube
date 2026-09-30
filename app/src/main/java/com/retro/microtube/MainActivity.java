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

    // HTTP endpoint used to bypass Android 2.2 TLS handshake limitations
    public static final String BACKEND_URL = "http://api.piped.privacydev.net"; 

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
        // Show stop button if user returns while audio is playing
        btnStopAudio.setVisibility(View.VISIBLE);
    }

    private class SearchTask extends AsyncTask<String, Void, Boolean> {
        private ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            dialog = ProgressDialog.show(MainActivity.this, "", "Searching...", true);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            videoTitles.clear();
            videoIds.clear();
            try {
                String q = URLEncoder.encode(params[0], "UTF-8");
                URL url = new URL(BACKEND_URL + "/search?q=" + q + "&filter=videos");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

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
                    String title = item.getString("title");
                    String rawUrl = item.getString("url");
                    String id = rawUrl.substring(rawUrl.indexOf("v=") + 2);

                    videoTitles.add(title);
                    videoIds.add(id);
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            dialog.dismiss();
            if (success) {
                adapter.notifyDataSetChanged();
            } else {
                Toast.makeText(MainActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
