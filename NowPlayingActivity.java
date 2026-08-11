package com.example.musiclite;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class NowPlayingActivity extends Activity {

    private TextView tvNowTitle, tvNowArtist;
    private SeekBar seekBarNow;
    private Button btnPlayNow, btnStopNow, btnRewindNow, btnForwardNow, btnBack;
    private ImageView ivBigAlbumArt;

    private MediaPlayer mediaPlayer;
    private Handler handler = new Handler();
    private Runnable updateSeekBar;
    private boolean isPlaying = false;

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

        mediaPlayer = MainActivity.getMediaPlayer();

        if (mediaPlayer != null) {
            isPlaying = mediaPlayer.isPlaying();
            btnPlayNow.setText(isPlaying ? "⏸" : "▶");
            startUpdatingSeekBar();
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
            } else if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                btnPlayNow.setText("⏸");
                isPlaying = true;
                handler.post(updateSeekBar);
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
}