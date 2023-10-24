package com.example.thirtyscs;
import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class Result extends WearableActivity {

    private TextView standTextView;
	private TextView timeTextView;
    private TextView hrTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        timeTextView = (TextView) findViewById(R.id.textView1);
        standTextView = (TextView) findViewById(R.id.textView2);
        hrTextView = (TextView) findViewById(R.id.textView5);

        // Enables Always-on
        setAmbientEnabled();

        Intent mIntent = getIntent();
        int stands = mIntent.getExtras().getInt("stands");
        float time = mIntent.getExtras().getFloat("time");
        int dHR = mIntent.getExtras().getInt("dHR");

        String tt = String.format("%.2f",time);

        Log.e("UAH", "time "+tt+" stands "+stands+" deltaHR "+dHR);
        timeTextView.setText(tt);
        standTextView.setText(""+stands);
        hrTextView.setText(""+dHR);
    }

    public void gohome(View view){
        finish();
    }
}
