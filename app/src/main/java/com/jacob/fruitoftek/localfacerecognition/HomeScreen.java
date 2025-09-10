package com.jacob.fruitoftek.localfacerecognition;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HomeScreen extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_screen);

        // Menerapkan animasi ke elemen-elemen
        ImageView imageView1 = findViewById(R.id.logoImageView);
        ImageView imageView2 = findViewById(R.id.FaceImageView);
        TextView textView1 = findViewById(R.id.textView1);
        TextView textView2 = findViewById(R.id.textView2);
        TextView textView3 = findViewById(R.id.textView3);
        TextView textView4 = findViewById(R.id.textView4);
        Button button = findViewById(R.id.startButton);

        Animation animation = AnimationUtils.loadAnimation(this, R.anim.slide_in_animation);
        imageView1.startAnimation(animation);
        imageView2.startAnimation(animation);
        textView1.startAnimation(animation);
        textView2.startAnimation(animation);
        textView3.startAnimation(animation);
        textView4.startAnimation(animation);
        button.startAnimation(animation);

        Button startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start MainActivity when the button is clicked
                startActivity(new Intent(HomeScreen.this, MainActivity.class));
            }
        });
    }
}
