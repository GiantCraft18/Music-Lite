package com.example.musiclite;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class SettingsActivity extends Activity {

    private TextView tvGitHubLink;
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        tvGitHubLink = findViewById(R.id.tvGitHubLink);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        // ТВОЯ НОВАЯ ССЫЛКА
        tvGitHubLink.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/GiantCraft18"));
            startActivity(browserIntent);
        });
    }
}