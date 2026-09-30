package com.retro.microtube;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class PlayerActivity extends Activity {

    private VideoView videoView;
    private Button btnBackground;
    private ProgressDialog dialog;
    private String streamUrl = null;
    private String videoTitle = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        videoView = (VideoView) findViewById(R.id.video_view);
        btnBackground = (Button) findViewById(R.id.btn_background);

        String videoId = getIntent().getStringExtra("VIDEO_ID");
        videoTitle = getIntent().getStringExtra("VIDEO_TITLE");

        btnBackground.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (streamUrl != null) {
                    // Switch to background audio service
                    if (videoView.isPlaying()) {
                        videoView.stopPlayback();
                    }
                    Intent serviceIntent = new Intent(PlayerActivity.this, AudioService.class);
                    serviceIntent.setAction(AudioService.ACTION_PLAY);
                    serviceIntent.putExtra(AudioService.EXTRA_URL, streamUrl);
                    serviceIntent.putExtra(AudioService.EXTRA_TITLE, videoTitle);
                    startService(serviceIntent);
                    Toast.makeText(PlayerActivity.this, "Playing in background", Toast.LENGTH_SHORT).show();
                    finish(); // Exit fullscreen player back to list
                }
            }
        });

        new FetchStreamTask().execute(videoId);
    }

    private class FetchStreamTask extends AsyncTask<String, Void, String> {
        @Override
        protected void onPreExecute() {
            dialog = ProgressDialog.show(PlayerActivity.this, "", "Loading stream...", true);
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                String videoId = params[0];
                URL url = new URL(MainActivity.BACKEND_URL + "/streams/" + videoId);
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
                JSONArray streams = response.getJSONArray("videoStreams");

                // Filter for 240p or 360p progressive MP4
                for (int i = 0; i < streams.length(); i++) {
                    JSONObject s = streams.getJSONObject(i);
                    String mime = s.getString("mimeType");
                    String quality = s.getString("quality");

                    if (mime.contains("video/mp4") && (quality.contains("240p") || quality.contains("360p"))) {
                        return s.getString("url");
                    }
                }
            } catch (Exception ignored) {}
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            dialog.dismiss();
            streamUrl = result;
            if (streamUrl != null) {
                MediaController controller = new MediaController(PlayerActivity.this);
                controller.setAnchorView(videoView);
                videoView.setMediaController(controller);
                videoView.setVideoURI(Uri.parse(streamUrl));
                videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    public void onPrepared(MediaPlayer mp) {
                        videoView.start();
                    }
                });
            } else {
                Toast.makeText(PlayerActivity.this, "Stream unavailable", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
    }
}
