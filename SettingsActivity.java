package com.example.musiclite;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONObject;

public class SettingsActivity extends Activity {

    private TextView tvUpdateStatus;
    private Button btnCheckUpdate, btnBack;

    private final String CURRENT_VERSION = "1.0";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        tvUpdateStatus = findViewById(R.id.tvUpdateStatus);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        btnCheckUpdate.setOnClickListener(v -> {
            tvUpdateStatus.setText("⏳ Проверка обновлений через GitHub API...");
            new Thread(() -> {
                String result = checkForUpdateViaAPI();
                runOnUiThread(() -> tvUpdateStatus.setText(result));
            }).start();
        });
    }

    private String checkForUpdateViaAPI() {
        try {
            URL url = new URL("https://api.github.com/repos/GiantCraft/Music-Lite/releases/latest");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();

                JSONObject json = new JSONObject(sb.toString());
                String latestVersion = json.getString("tag_name");
                
                // ПРАВИЛЬНО: берём массив "assets" и берём первый файл (индекс 0)
                JSONArray assets = json.getJSONArray("assets");
                String apkUrl = assets.getJSONObject(0).getString("browser_download_url");

                if (!CURRENT_VERSION.equals(latestVersion)) {
                    return "✅ Доступна версия " + latestVersion + "! Нажмите на ссылку, чтобы скачать.";
                } else {
                    return "✅ У вас последняя версия (" + CURRENT_VERSION + ")";
                }
            } else {
                return "❌ Ошибка API GitHub: код " + conn.getResponseCode();
            }
        } catch (Exception e) {
            return "❌ Ошибка: " + e.getMessage() + "\nПроверьте интернет или выпустите релиз на GitHub.";
        }
    }
}