package com.retro.microtube;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import java.lang.reflect.Method;

public class AudioService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    public static final String ACTION_PLAY = "com.retro.microtube.action.PLAY";
    public static final String ACTION_STOP = "com.retro.microtube.action.STOP";
    public static final String EXTRA_URL = "EXTRA_URL";
    public static final String EXTRA_TITLE = "EXTRA_TITLE";

    private MediaPlayer mediaPlayer;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MicroTube:WakeLock");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }

        if (intent.getAction().equals(ACTION_PLAY)) {
            String url = intent.getStringExtra(EXTRA_URL);
            String title = intent.getStringExtra(EXTRA_TITLE);
            startPlaying(url, title != null ? title : "MicroTube Audio");
        } else if (intent.getAction().equals(ACTION_STOP)) {
            stopPlaying();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void startPlaying(String streamUrl, String title) {
        stopPlaying();

        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }

        // Display Notification to prevent Android's low-memory killer from ending playback
        Notification notification = new Notification(android.R.drawable.ic_media_play, "Playing audio...", System.currentTimeMillis());
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        // Reflection ensures compatibility with API 8 runtime while compiling on newer SDKs
        try {
            Method m = Notification.class.getMethod("setLatestEventInfo", Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
            m.invoke(notification, this, "MicroTube Background", title, pendingIntent);
        } catch (Exception ignored) {}

        startForeground(101, notification);

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            mediaPlayer.setDataSource(this, Uri.parse(streamUrl));
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnErrorListener(this);
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            stopPlaying();
        }
    }

    private void stopPlaying() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        stopForeground(true);
    }

    public void onPrepared(MediaPlayer mp) {
        mp.start();
    }

    public void onCompletion(MediaPlayer mp) {
        stopPlaying();
        stopSelf();
    }

    public boolean onError(MediaPlayer mp, int what, int extra) {
        stopPlaying();
        stopSelf();
        return true;
    }

    @Override
    public void onDestroy() {
        stopPlaying();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
