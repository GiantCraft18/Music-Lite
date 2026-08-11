package com.example.musiclite;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    private ListView lvSongs;
    private EditText etSearch;
    private TextView btnShowFavorites;
    private Button btnPlay, btnStop, btnRewind, btnForward, btnMute;
    private TextView tvNowPlaying, tvArtistPlaying;
    private SeekBar seekBar;
    private TextView tvCurrentTime, tvTotalTime;

    private MediaPlayer mediaPlayer;
    private List<Song> songList = new ArrayList<>();
    private List<Song> filteredList = new ArrayList<>();
    private List<Song> favoriteList = new ArrayList<>();
    private int currentSongIndex = -1;
    private boolean isPlaying = false;
    private boolean isShowingFavorites = false;

    private Handler handler = new Handler();
    private Runnable updateSeekBar;

    private SharedPreferences prefs;
    private AudioManager audioManager;

    private int cachedPosition = 0;
    private boolean wasPlayingBeforePause = false;
    private boolean isProcessing = false;
    private boolean isMuted = false;
    private float previousVolume = 1.0f;

    private SongAdapter adapter;

    private static MediaPlayer sharedMediaPlayer = null;

    public static MediaPlayer getMediaPlayer() {
        return sharedMediaPlayer;
    }

    private View $(String id) {
        return findViewById(getResources().getIdentifier(id, "id", getPackageName()));
    }

    private BroadcastReceiver headsetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", -1);
                if (state == 0 && mediaPlayer != null && isPlaying) {
                    mediaPlayer.pause();
                    btnPlay.setText("▶");
                    isPlaying = false;
                    Toast.makeText(MainActivity.this, "🎧 Наушники отключены. Пауза.", Toast.LENGTH_SHORT).show();
                } else if (state == 1 && mediaPlayer != null && !isPlaying) {
                    if (wasPlayingBeforePause) {
                        mediaPlayer.start();
                        btnPlay.setText("⏸");
                        isPlaying = true;
                        Toast.makeText(MainActivity.this, "🎧 Наушники подключены.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("music_prefs", MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        lvSongs = (ListView) $("lvSongs");
        etSearch = (EditText) $("etSearch");
        btnShowFavorites = (TextView) $("btnShowFavorites");
        btnPlay = (Button) $("btnPlay");
        btnStop = (Button) $("btnStop");
        btnRewind = (Button) $("btnRewind");
        btnForward = (Button) $("btnForward");
        btnMute = (Button) $("btnMute");
        tvNowPlaying = (TextView) $("tvNowPlaying");
        tvArtistPlaying = (TextView) $("tvArtistPlaying");
        seekBar = (SeekBar) $("seekBar");
        tvCurrentTime = (TextView) $("tvCurrentTime");
        tvTotalTime = (TextView) $("tvTotalTime");

        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(headsetReceiver, filter);

        // Загружаем избранное из памяти
        String favs = prefs.getString("favorites", "");
        if (!favs.isEmpty()) {
            for (String path : favs.split(";")) {
                for (Song s : songList) {
                    if (s.path.equals(path)) favoriteList.add(s);
                }
            }
        }

        loadMusicFiles();
        updateAdapter();

        cachedPosition = prefs.getInt("cached_pos", 0);
        currentSongIndex = prefs.getInt("last_index", -1);
        String lastTitle = prefs.getString("last_title", "");
        String lastArtist = prefs.getString("last_artist", "");
        boolean wasPlaying = prefs.getBoolean("was_playing", false);

        if (currentSongIndex != -1 && !lastTitle.isEmpty() && wasPlaying) {
            tvNowPlaying.setText(lastTitle);
            tvArtistPlaying.setText(lastArtist);
            playSong(currentSongIndex);
            if (cachedPosition > 0 && mediaPlayer != null) {
                mediaPlayer.seekTo(cachedPosition);
            }
        }

        // Переключение на избранное/все песни
        btnShowFavorites.setOnClickListener(v -> {
            isShowingFavorites = !isShowingFavorites;
            if (isShowingFavorites) {
                btnShowFavorites.setText("📋 Все");
                filteredList.clear();
                filteredList.addAll(favoriteList);
            } else {
                btnShowFavorites.setText("♥️");
                filteredList.clear();
                filteredList.addAll(songList);
            }
            adapter.notifyDataSetChanged();
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSongs(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) mediaPlayer.seekTo(progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        lvSongs.setOnItemClickListener((parent, view, position, id) -> {
            int originalIndex = songList.indexOf(filteredList.get(position));
            openNowPlaying(originalIndex);
        });

        // ДОЛГОЕ НАЖАТИЕ: ДОБАВИТЬ/УБРАТЬ ИЗ ИЗБРАННОГО
        lvSongs.setOnItemLongClickListener((parent, view, position, id) -> {
            Song song = filteredList.get(position);
            if (favoriteList.contains(song)) {
                favoriteList.remove(song);
                Toast.makeText(MainActivity.this, "♡ Убрано из избранного", Toast.LENGTH_SHORT).show();
            } else {
                favoriteList.add(song);
                Toast.makeText(MainActivity.this, "♥️ Добавлено в избранное", Toast.LENGTH_SHORT).show();
            }
            saveFavorites();

            // Если мы сейчас в режиме избранного, обновляем список
            if (isShowingFavorites) {
                filteredList.clear();
                filteredList.addAll(favoriteList);
            }
            adapter.notifyDataSetChanged();
            return true;
        });

        btnPlay.setOnClickListener(v -> {
            if (isProcessing) return;
            isProcessing = true;

            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                btnPlay.setText("▶");
                isPlaying = false;
                handler.removeCallbacks(updateSeekBar);
                Log.d("MusicLite", "⏸ Пауза");
            } else if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                btnPlay.setText("⏸");
                isPlaying = true;
                handler.post(updateSeekBar);
                Log.d("MusicLite", "▶ Продолжено");
            } else if (mediaPlayer == null && currentSongIndex != -1) {
                playSong(currentSongIndex);
            } else {
                Toast.makeText(this, "Выберите песню сначала!", Toast.LENGTH_SHORT).show();
            }
            isProcessing = false;
        });

        btnStop.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                fadeOutAndStop();
                Log.d("MusicLite", "⏹ Стоп с затуханием");
            }
        });

        btnRewind.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                int newPos = Math.max(0, mediaPlayer.getCurrentPosition() - 5000);
                mediaPlayer.seekTo(newPos);
            }
        });

        btnForward.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                int newPos = Math.min(mediaPlayer.getDuration(), mediaPlayer.getCurrentPosition() + 5000);
                mediaPlayer.seekTo(newPos);
            }
        });

        btnMute.setOnClickListener(v -> {
            if (mediaPlayer == null) return;
            isMuted = !isMuted;
            if (isMuted) {
                int streamVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                previousVolume = streamVolume / 15.0f;
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                btnMute.setText("🔇");
                btnMute.setBackgroundColor(0xFFFF9800);
            } else {
                int newVolume = (int) (previousVolume * 15);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
                btnMute.setText("🔊");
                btnMute.setBackgroundColor(0xFF333333);
            }
        });
    }

    private void saveFavorites() {
        StringBuilder sb = new StringBuilder();
        for (Song s : favoriteList) {
            sb.append(s.path).append(";");
        }
        prefs.edit().putString("favorites", sb.toString()).apply();
    }

    private void updateAdapter() {
        filteredList.clear();
        filteredList.addAll(isShowingFavorites ? favoriteList : songList);
        adapter = new SongAdapter(this, filteredList);
        lvSongs.setAdapter(adapter);
    }

    private void filterSongs(String query) {
        if (isShowingFavorites) {
            filteredList.clear();
            for (Song s : favoriteList) {
                if (s.title.toLowerCase().contains(query.toLowerCase())) {
                    filteredList.add(s);
                }
            }
        } else {
            filteredList.clear();
            for (Song s : songList) {
                if (s.title.toLowerCase().contains(query.toLowerCase())) {
                    filteredList.add(s);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void openNowPlaying(int index) {
        Song song = songList.get(index);
        Intent intent = new Intent(this, NowPlayingActivity.class);
        intent.putExtra("title", song.title);
        intent.putExtra("artist", song.artist);
        intent.putExtra("path", song.path);
        if (mediaPlayer != null) {
            intent.putExtra("position", mediaPlayer.getCurrentPosition());
            intent.putExtra("duration", mediaPlayer.getDuration());
        }
        startActivity(intent);
    }

    private void fadeOutAndStop() {
        if (mediaPlayer == null) return;
        final float maxVolume = 1.0f;
        final float minVolume = 0.0f;
        final int steps = 10;
        final long delay = 30;

        for (int i = 0; i < steps; i++) {
            final float volume = maxVolume - ((maxVolume - minVolume) * i / steps);
            handler.postDelayed(() -> {
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(volume, volume);
                }
            }, i * delay);
        }

        handler.postDelayed(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.reset();
                mediaPlayer.release();
                mediaPlayer = null;
                sharedMediaPlayer = null;
                isPlaying = false;
                btnPlay.setText("▶");
                tvNowPlaying.setText("Нет трека");
                tvArtistPlaying.setText("");
                seekBar.setProgress(0);
                tvCurrentTime.setText("0:00");
                tvTotalTime.setText("0:00");
                handler.removeCallbacks(updateSeekBar);
                Log.d("MusicLite", "⏹ Полная остановка");

                prefs.edit()
                    .putInt("cached_pos", 0)
                    .putInt("last_index", -1)
                    .putString("last_title", "")
                    .putString("last_artist", "")
                    .putBoolean("was_playing", false)
                    .apply();
            }
        }, steps * delay + 100);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mediaPlayer != null && isPlaying) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null) {
            cachedPosition = mediaPlayer.getCurrentPosition();
            wasPlayingBeforePause = isPlaying;
            prefs.edit().putInt("cached_pos", cachedPosition).apply();
            Log.d("MusicLite", "💾 Кэш сохранён: " + cachedPosition);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPlayer != null && wasPlayingBeforePause && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            isPlaying = true;
            btnPlay.setText("⏸");
            handler.post(updateSeekBar);
            Log.d("MusicLite", "🔄 Плеер восстановлен");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mediaPlayer != null) {
            int pos = mediaPlayer.getCurrentPosition();
            prefs.edit()
                .putInt("cached_pos", pos)
                .putInt("last_index", currentSongIndex)
                .putString("last_title", tvNowPlaying.getText().toString())
                .putString("last_artist", tvArtistPlaying.getText().toString())
                .putBoolean("was_playing", isPlaying)
                .apply();
            Log.d("MusicLite", "💾 ПОЛНОЕ СОХРАНЕНИЕ: позиция " + pos);
        }
    }

    private void loadMusicFiles() {
        String[] projection = { MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DATA };
        Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, MediaStore.Audio.Media.IS_MUSIC + " != 0", null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String title = cursor.getString(0);
                String artist = cursor.getString(1);
                String path = cursor.getString(2);
                songList.add(new Song(title, artist, path));
            }
            cursor.close();
        }
        Collections.sort(songList, (a, b) -> a.title.compareToIgnoreCase(b.title));
        if (songList.isEmpty()) Toast.makeText(this, "Нет музыкальных файлов", Toast.LENGTH_LONG).show();
        else Log.d("MusicLite", "📂 Загружено треков: " + songList.size());
    }

    private void playSong(int index) {
        if (index < 0 || index >= songList.size()) return;
        currentSongIndex = index;
        Song song = songList.get(index);
        if (mediaPlayer != null) { mediaPlayer.release(); mediaPlayer = null; }
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(song.path);
            mediaPlayer.prepare();
            mediaPlayer.start();
            isPlaying = true;
            btnPlay.setText("⏸");
            tvNowPlaying.setText(song.title);
            tvArtistPlaying.setText(song.artist);
            int duration = mediaPlayer.getDuration();
            seekBar.setMax(duration);
            tvTotalTime.setText(formatTime(duration));

            sharedMediaPlayer = mediaPlayer;

            mediaPlayer.setVolume(0.0f, 0.0f);
            final float maxVolume = 1.0f;
            final int steps = 10;
            final long delay = 30;
            for (int i = 0; i < steps; i++) {
                final float volume = maxVolume * (i + 1) / steps;
                handler.postDelayed(() -> {
                    if (mediaPlayer != null) {
                        mediaPlayer.setVolume(volume, volume);
                    }
                }, i * delay);
            }

            updateSeekBar = new Runnable() {
                @Override
                public void run() {
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        int cur = mediaPlayer.getCurrentPosition();
                        seekBar.setProgress(cur);
                        tvCurrentTime.setText(formatTime(cur));
                        handler.postDelayed(this, 1000);
                    }
                }
            };
            handler.post(updateSeekBar);

            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d("MusicLite", "⏩ Автопереход на следующий трек...");
                int nextIndex = (currentSongIndex + 1) % songList.size();
                playSong(nextIndex);
            });

        } catch (Exception e) {
            Toast.makeText(this, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show();
            Log.e("MusicLite", "❌ Ошибка: " + e.getMessage());
        }
    }

    private String formatTime(int milliseconds) {
        int seconds = (milliseconds / 1000) % 60;
        int minutes = (milliseconds / (1000 * 60)) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    public static class Song {
        public String title;
        public String artist;
        public String path;
        public Song(String title, String artist, String path) {
            this.title = title;
            this.artist = artist;
            this.path = path;
        }
    }
            }
