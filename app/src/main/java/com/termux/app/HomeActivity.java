package com.termux.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

// inspired by SimpleSMSMessenger as it seems to work well as a Home Launcher app
// maybe the in-between is helpful
public class HomeActivity extends AppCompatActivity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(this, TermuxActivity.class));
        finish();
    }
}
