package com.example.android_codenames_repo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.opencv.android.OpenCVLoader;

import userinterface.CameraActivity;

public class MainActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (OpenCVLoader.initDebug())
            Log.d("LOADED", "success");
        else
            Log.d("LOADED", "err");

        Intent intent = new Intent(this, CameraActivity.class);
        startActivity(intent);
    }
}