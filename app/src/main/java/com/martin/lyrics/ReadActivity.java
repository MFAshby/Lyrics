package com.martin.lyrics;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.webkit.WebView;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Shows the selected song lyrics. Uses a webview (so I don't have to implement pinch-zoom
 * myself) and has an autoscroll
 * Created by martin on 12/10/14.
 */
public class ReadActivity extends Activity {
    private int m_scroll_rate = 0;
    private WebView m_webview;
    private Handler m_handler;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_read);
        String lyrics = getIntent().getStringExtra(SearchActivity.EXTRA_LYRICS);
        m_webview = (WebView)findViewById(R.id.webView);
        m_webview.loadDataWithBaseURL(null, lyrics, "text/plain", "UTF-8", null);
        m_webview.getSettings().setBuiltInZoomControls(true);
        m_webview.getSettings().setSupportZoom(true);

        SeekBar sb = (SeekBar)findViewById(R.id.seek_auto_scroll);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                m_scroll_rate = progress;
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        m_handler = new Handler();
        scrollChange();
    }

    private void scrollChange() {
        //scroll by x% of the screen per second, where X is the progress bar.
        //update once every 50ms
        final int screenHeight = getResources().getConfiguration().screenHeightDp;
        Runnable r = new Runnable() {
            @Override public void run() {
                int f = (m_scroll_rate * screenHeight)/(400*20);
                if (f > 0) {
                    m_webview.scrollBy(0, f);
                }
                scrollChange();
            }
        }; m_handler.postDelayed(r, 50);
    }
}
