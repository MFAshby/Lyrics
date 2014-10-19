package com.martin.lyrics;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Shows the activity_lyrics
 * Created by martin on 12/10/14.
 */
public class ReadActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lyrics);
        TextView tv = (TextView)findViewById(R.id.lyricsTextView);
        tv.setText(getIntent().getStringExtra(SearchActivity.EXTRA_LYRICS));
    }
}
