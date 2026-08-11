package com.example.musiclite;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

public class NowPlayingActivity extends Activity {

    private TextView tvNowTitle, tvNowArtist;
    private SeekBar seekBarNow;
    private Button btnPlayNow, btnStopNow, btnRewindNow, btnForwardNow, btnBack;
    private ImageView ivBigAlbumArt;
    private LinearLayout equalizerContainer;

    private MediaPlayer mediaPlayer;
    private Handler handler = new Handler();
    private Runnable updateSeekBar;
    private boolean isPlaying = false;

    private View[] bars = new View[8];
    private Random random = new Random();

    private View $(String id) {
        return findViewById(getResources().getIdentifier(id, "id", getPackageName()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_now_playing);

        tvNowTitle = (TextView) $("tvNowTitle");
        tvNowArtist = (TextView) $("tvNowArtist");
        seekBarNow = (SeekBar) $("seekBarNow");
        btnPlayNow = (Button) $("btnPlayNow");
        btnStopNow = (Button) $("btnStopNow");
        btnRewindNow = (Button) $("btnRewindNow");
        btnForwardNow = (Button) $("btnForwardNow");
        btnBack = (Button) $("btnBack");
        ivBigAlbumArt = (ImageView) $("ivBigAlbumArt");
        equalizerContainer = (LinearLayout) $("equalizerContainer");

        String title = getIntent().getStringExtra("title");
        String artist = getIntent().getStringExtra("artist");
        String path = getIntent().getStringExtra("path");
        int position = getIntent().getIntExtra("position", 0);
        int duration = getIntent().getIntExtra("duration", 0);

        tvNowTitle.setText(title);
        tvNowArtist.setText(artist);
        seekBarNow.setMax(duration);
        seekBarNow.setProgress(position);

        Bitmap albumArt = getAlbumArt(path);
        if (albumArt != null) {
            ivBigAlbumArt.setImageBitmap(albumArt);
        } else {
            ivBigAlbumArt.setImageResource(android.R.drawable.ic_media_play);
        }

        setupEqualizerBars();

        mediaPlayer = MainActivity.getMediaPlayer();

        if (mediaPlayer != null) {
            isPlaying = mediaPlayer.isPlaying();
            btnPlayNow.setText(isPlaying ? "⏸" : "▶");
            startUpdatingSeekBar();
            if (isPlaying) {
                startEqualizerAnimation();
                startForegroundService(title, artist, path);
            }
        } else {
            Toast.makeText(this, "❌ Плеер не найден. Вернитесь в главное меню.", Toast.LENGTH_SHORT).show();
            finish();
        }

        seekBarNow.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnPlayNow.setOnClickListener(v -> {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                btnPlayNow.setText("▶");
                isPlaying = false;
                handler.removeCallbacks(updateSeekBar);
                stopEqualizerAnimation();
                stopForegroundService();
            } else if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                btnPlayNow.setText("⏸");
                isPlaying = true;
                handler.post(updateSeekBar);
                startEqualizerAnimation();
                startForegroundService(title, artist, path);
            }
        });

        btnStopNow.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.reset();
                mediaPlayer.release();
                mediaPlayer = null;
                isPlaying = false;
                btnPlayNow.setText("▶");
                seekBarNow.setProgress(0);
                tvNowTitle.setText("Нет трека");
                tvNowArtist.setText("");
                stopEqualizerAnimation();
                stopForegroundService();
                Toast.makeText(this, "⏹ Остановлено", Toast.LENGTH_SHORT).show();
            }
        });

        btnRewindNow.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                int newPos = Math.max(0, mediaPlayer.getCurrentPosition() - 5000);
                mediaPlayer.seekTo(newPos);
            }
        });

        btnForwardNow.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                int newPos = Math.min(mediaPlayer.getDuration(), mediaPlayer.getCurrentPosition() + 5000);
                mediaPlayer.seekTo(newPos);
            }
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void startForegroundService(String title, String artist, String path) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction("PLAY");
        intent.putExtra("title", title);
        intent.putExtra("artist", artist);
        intent.putExtra("path", path);
        startService(intent);
    }

    private void stopForegroundService() {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction("STOP");
        startService(intent);
    }

    private void setupEqualizerBars() {
        for (int i = 0; i < 8; i++) {
            View bar = new View(this);
            int size = 40 + random.nextInt(60);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(20, size);
            params.setMargins(4, 0, 4, 0);
            bar.setLayoutParams(params);
            bar.setBackgroundColor(Color.parseColor("#00BCD4"));
            equalizerContainer.addView(bar);
            bars[i] = bar;
        }
    }

    private Runnable equalizerRunnable;
    private void startEqualizerAnimation() {
        if (equalizerRunnable != null) return;
        equalizerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPlaying) {
                    for (int i = 0; i < bars.length; i++) {
                        int newHeight = 20 + random.nextInt(150);
                        View bar = bars[i];
                        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) bar.getLayoutParams();
                        params.height = newHeight;
                        bar.setLayoutParams(params);
                    }
                    handler.postDelayed(this, 150);
                }
            }
        };
        handler.post(equalizerRunnable);
    }

    private void stopEqualizerAnimation() {
        if (equalizerRunnable != null) {
            handler.removeCallbacks(equalizerRunnable);
            equalizerRunnable = null;
        }
        for (View bar : bars) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) bar.getLayoutParams();
            params.height = 10;
            bar.setLayoutParams(params);
        }
    }

    private Bitmap getAlbumArt(String path) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(path);
            byte[] artBytes = retriever.getEmbeddedPicture();
            if (artBytes != null) {
                return BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void startUpdatingSeekBar() {
        updateSeekBar = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    seekBarNow.setProgress(mediaPlayer.getCurrentPosition());
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.post(updateSeekBar);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateSeekBar);
    }
}