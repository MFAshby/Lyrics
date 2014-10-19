package com.martin.lyrics;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Activity to search for lyrics.
 */
public class SearchActivity extends Activity {

    //used to store application state when you rotate the device. See onSaveInstanceState.
    protected static final String SEARCH_RESULT = "com.martin.lyrics.search_results";
    protected static final String SEARCH_TEXT = "com.martin.lyrics.search_text";

    //Used to pass the song lyrics to the next activity, which will display them.
    protected static final String EXTRA_LYRICS = "com.martin.lyrics.EXTRA_LYRICS";

    //What the user has searched.
    private String m_search_text;

    //Array of results which are currently displayed.
    private ArrayList<SearchResult> m_results;

    //An object which links the above array to a ListView on the search screen.
    private ArrayAdapter<SearchResult> m_results_adapter;

    //The ListView which will display all the results on screen.
    private ListView m_list_view;

    //A progress bar, shown when the user searches the internet
    private ProgressBar m_progress_bar;

    //A timer, used to detect when the user has stopped typing for a bit.
    private Timer m_timer;
    private TimerTask m_typing_timeout;

    /**
     * This method is called when the activity is first started.
     */
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //set up the content view
        setContentView(R.layout.activity_search);

        //Get a reference to the main list view.
        m_list_view = (ListView) findViewById(R.id.listView);

        //create the adapter for the list view.
        m_results_adapter = new LyricsResultsAdapter(this);
        m_list_view.setAdapter(m_results_adapter);

        //listen to the user clicking/tapping on the list view. Show the selected lyrics.
        m_list_view.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                new ReadTask().execute(m_results_adapter.getItem(position));
            }
        });

        //get a reference to the progress bar and hide it. We'll show it when needed.
        m_progress_bar = (ProgressBar)findViewById(R.id.progressBar);
        m_progress_bar.setVisibility(View.INVISIBLE);

        m_timer = new Timer();
        m_typing_timeout = null;

        if (savedInstanceState != null) {
            //If we are restoring the application, e.g. after the user has rotated their device,
            //then remember what results we were showing at the time, and what they seached for.
            m_results = savedInstanceState.getParcelableArrayList(SEARCH_RESULT);
            m_results_adapter.clear();
            m_results_adapter.addAll(m_results);
            m_results_adapter.notifyDataSetChanged();

            m_search_text = savedInstanceState.getString(SEARCH_TEXT);
        } else {
            m_search_text = null;
            //search locally stored results when the user first opens the app.
            new SearchTask(new LocalAdapter(this)).execute("");
        }
    }

    /**
     * This is called to create the options menu, which also sets up the action bar at the
     * top of the screen.
     */
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.my, menu);

        //set up the search view
        MenuItem item = menu.findItem(R.id.action_search);
        SearchView m_search_view = (SearchView)item.getActionView();
        if (m_search_text != null) {
            item.expandActionView();
            m_search_view.setQuery(m_search_text, false);
        }
        m_search_view.setOnQueryTextListener(new OnSearchQueryTextListener());

        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            //launch the settings activity.
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * This is called when the app is put into the background, or when it is rotated. Save the
     * state of the application here, so it can be restored in onCreate.
     * @param b the bundle to save things into.
     */
    @Override public void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        b.putParcelableArrayList(SEARCH_RESULT, m_results);
        b.putString(SEARCH_TEXT, m_search_text);
    }

    /**
     * Used by the search bar when the text changes. Search the internet on submit
     * and search local results if the user stops typing for 300ms.
     */
    private class OnSearchQueryTextListener implements SearchView.OnQueryTextListener {

        @Override public boolean onQueryTextSubmit(String query) {
            //get the adapter that the user selected.
            SourceAdapter adapter = null;
            try {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(SearchActivity.this);
                String sourceClass = sp.getString(getResources().getString(R.string.pref_lyrics_source),
                        getResources().getString(R.string.default_source_value));
                Class c = Class.forName(sourceClass);
                adapter = (SourceAdapter)c.newInstance();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

            new SearchTask(adapter).execute(query);
            return true;
        }

        @Override public boolean onQueryTextChange(String newText) {
            //store the text
            m_search_text = newText;

            //bump the timer, so that if the user stops typing for a second we'll search local results.
            if (m_typing_timeout != null) {
                //cancel the old task first.
                m_typing_timeout.cancel();
            }
            m_typing_timeout = new TimerTask() {
                @Override public void run() {
                    //search locally stored results. Must kick this off in the UI thread.
                    runOnUiThread(new Runnable() {
                        public void run() {
                        new SearchTask(new LocalAdapter(SearchActivity.this)).execute(m_search_text);
                        }
                    });
                }
            };
            m_timer.purge();
            m_timer.schedule(m_typing_timeout, 300);

            return true;
        }
    }

    /**
     * Used by the ListView on the screen to display lyric search results using a custom defined view.
     */
    private class LyricsResultsAdapter extends ArrayAdapter<SearchResult> {

        public LyricsResultsAdapter(Context context) {
            super(context, R.layout.list_item);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            SearchResult lr = getItem(position);
            //if an already initialised view was provided, use that. Else create a new one from the
            //layout file.
            View v_use = convertView != null ?
                            convertView :
                            getLayoutInflater().inflate(R.layout.list_item, parent, false);
            //set the title and artist on the appropriate labels
            ((TextView)v_use.findViewById(R.id.li_artist)).setText(lr.getArtist());
            ((TextView)v_use.findViewById(R.id.li_song_title)).setText(lr.getTitle());
            return v_use;
        }
    }

    /**
     *  Background task to search the net for lyrics..
     */
    private class SearchTask extends AsyncTask<String, Void, ArrayList<SearchResult>> {
        private SourceAdapter adapter;
        protected SearchTask(SourceAdapter a) {
            adapter = a;
        }

        @Override protected void onPreExecute() {
            //kick off the progress spinner, hide the list view
            m_list_view.setVisibility(View.INVISIBLE);
            m_progress_bar.setVisibility(View.VISIBLE);
        }

        @Override protected ArrayList<SearchResult> doInBackground(String... search) {
            return adapter.getSearchResults(search[0]);
        }

        @Override protected void onPostExecute(ArrayList<SearchResult> results) {
            m_results = results;
            m_results_adapter.clear();
            m_results_adapter.addAll(m_results);
            m_results_adapter.notifyDataSetChanged();
            m_list_view.setVisibility(View.VISIBLE);
            m_progress_bar.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Background task to retrieve lyrics for the selected title, and display them in a new activity
     * Also stores them locally in the database.
     */
    private class ReadTask extends AsyncTask<SearchResult, Void, String> {

        @Override protected void onPreExecute(){
            m_list_view.setVisibility(View.INVISIBLE);
            m_progress_bar.setVisibility(View.VISIBLE);
        }

        @Override protected String doInBackground(SearchResult... result) {
            //get the actual lyrics using the adapter
            String lyrics = result[0].getAdapter().getLyrics(result[0]);

            //save them to local storage.
            if (lyrics != null) {
                LocalAdapter.saveLyrics(result[0], lyrics);
            }

            return lyrics;
        }

        @Override protected void onPostExecute(String lyrics) {
            //hide the progress indicator, unhide the list.
            m_list_view.setVisibility(View.VISIBLE);
            m_progress_bar.setVisibility(View.INVISIBLE);

            //kick off the new activity to display the lyrics.
            Intent i = new Intent(SearchActivity.this, ReadActivity.class);
            i.putExtra(EXTRA_LYRICS, lyrics);
            startActivity(i);
        }
    }
}
