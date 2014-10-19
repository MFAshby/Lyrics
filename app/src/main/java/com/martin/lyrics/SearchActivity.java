package com.martin.lyrics;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
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

import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.DomSerializer;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;


public class SearchActivity extends Activity {

    //used to store application state when you rotate the device. See onSaveInstanceState.
    protected static final String SEARCH_RESULT = "com.martin.lyrics.search_results";
    protected static final String SEARCH_TEXT = "com.martin.lyrics.search_text";

    //Used to pass the song lyrics to the next activity, which will display them.
    protected static final String EXTRA_LYRICS = "com.martin.lyrics.EXTRA_LYRICS";

    //What the user has searched.
    private String m_search_text;

    //Array of results which are currently displayed.
    private ArrayList<LyricResult> m_results;

    //A class which links the above array, to a ListView on the search screen.
    private ArrayAdapter<LyricResult> m_results_adapter;

    //The ListView which will display all the results on screen.
    private ListView m_list_view;

    //A progress bar, shown when the user searches the internet
    private ProgressBar m_progress_bar;

    //A local database to store results in
    private SQLiteDatabase m_local_results;

    //A timer, used to detect when the user has stopped typing for a bit.
    private Timer m_timer;
    private TimerTask m_typing_timeout;

    /**
     * This method is called when the activity is first started.
     * @param savedInstanceState
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

        //listen to the user clicking/tapping on the list view.
        m_list_view.setOnItemClickListener(new OnListItemClickListener());

        //get a reference to the progress bar and hide it. We'll show it when needed.
        m_progress_bar = (ProgressBar)findViewById(R.id.progressBar);
        m_progress_bar.setVisibility(View.INVISIBLE);

        //open up the database for stored results.
        m_local_results = new LyricsDatabase(this).getWritableDatabase();

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
            new LyricSearcher(new LocalLyricsAdapter()).execute("");
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
            DialogPreference
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
     * Useful common function for getting a web page that can be parsed as XML sensibly.
     * @param urlString The URL of the page to fetch.
     * @param queryString Non-encoded query string (e.g. search terms from a text field)
     * @return Parsed Document object ready for querying with XPath.
     */
    private static Document getCleanDocument(String urlString, String queryString) {
        try {
            //make the query string safe to use in a URL
            String encodedSearch = URLEncoder.encode(queryString, "UTF-8");

            //create the URL
            URL url = new URL(urlString + encodedSearch);

            //Open up a connection
            HttpURLConnection con = (HttpURLConnection)url.openConnection();

            //clean up resulting page and convert to DOM, which can be used with XPath.
            TagNode tn = new HtmlCleaner().clean(con.getInputStream());
            return new DomSerializer(new CleanerProperties()).createDOM(tn);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Used by the search bar when the text changes. Search the intenet on submit..
     * and search local results if the user stops typing for a second.
     */
    private class OnSearchQueryTextListener implements SearchView.OnQueryTextListener {

        @Override public boolean onQueryTextSubmit(String query) {
            //search the internet
            //new LyricSearcher(new AZLyricsAdapter()).execute(query);
            new LyricSearcher(new LyricsManiaAdapter()).execute(query);
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
            m_typing_timeout = new UserStoppedTypingTask();
            m_timer.purge();
            m_timer.schedule(m_typing_timeout, 300);

            return true;
        }
    }

    /**
     * This will get run if the user ever stops typing for 300 millis.
     */
    private class UserStoppedTypingTask extends TimerTask {

        @Override
        public void run() {
            //search locally stored results. Must kick this off in the UI thread.
            runOnUiThread(new Runnable(){
                public void run() {
                    new LyricSearcher(new LocalLyricsAdapter()).execute(m_search_text);
                }
            });
        }
    }

    /**
     * Used when selecting an item from the list. Kicks off a retrieve of the selected lyrics
     */
    private class OnListItemClickListener implements AdapterView.OnItemClickListener {

        @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            new LyricsGrabber().execute(m_results_adapter.getItem(position));
        }
    }

    /**
     * Used by the ListView on the screen to display lyric search results using a custom defined view.
     */
    private class LyricsResultsAdapter extends ArrayAdapter<LyricResult> {

        public LyricsResultsAdapter(Context context) {
            super(context, R.layout.list_item);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            LyricResult lr = getItem(position);
            //if an already initialised view was provided, use that. Else create a new one from the
            //layout file.
            View v_use = convertView != null ?
                            convertView :
                            getLayoutInflater().inflate(R.layout.list_item, parent, false);
            //set the title and artist on the appropriate labels
            ((TextView)v_use.findViewById(R.id.li_artist)).setText(lr.artist);
            ((TextView)v_use.findViewById(R.id.li_song_title)).setText(lr.title);
            return v_use;
        }
    }

    /**
     *  Background task to search the net for lyrics..
     */
    private class LyricSearcher extends AsyncTask<String, Void, ArrayList<LyricResult>> {
        private LyricSourceAdapter adapter;
        protected LyricSearcher(LyricSourceAdapter a) {
            adapter = a;
        }

        @Override protected void onPreExecute() {
            //kick off the progress spinner, hide the list view
            m_list_view.setVisibility(View.INVISIBLE);
            m_progress_bar.setVisibility(View.VISIBLE);
        }

        @Override protected ArrayList<LyricResult> doInBackground(String... search) {
            return adapter.getSearchResults(search[0]);
        }

        @Override protected void onPostExecute(ArrayList<LyricResult> results) {
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
    private class LyricsGrabber extends AsyncTask<LyricResult, Void, String> {

        @Override protected void onPreExecute(){
            m_list_view.setVisibility(View.INVISIBLE);
            m_progress_bar.setVisibility(View.VISIBLE);
        }

        @Override protected String doInBackground(LyricResult... result) {
            //get the actual lyrics using the adapter
            String lyrics = result[0].getAdapter().getLyrics(result[0]);

            ContentValues cv = new ContentValues();
            cv.put("Artist", result[0].artist);
            cv.put("Title", result[0].title);
            cv.put("Lyrics", lyrics);

            //store the lyrics in the DB. Replace any existing ones for the same artist (unique index
            //is on artist_title, see LyricsDatabase class)
            m_local_results.insertWithOnConflict("Lyrics", null, cv, SQLiteDatabase.CONFLICT_REPLACE);

            return lyrics;
        }

        @Override protected void onPostExecute(String lyrics) {
            //hide the progress indicator, unhide the list.
            m_list_view.setVisibility(View.VISIBLE);
            m_progress_bar.setVisibility(View.INVISIBLE);

            //kick off the new activity to display the lyrics.
            Intent i = new Intent(SearchActivity.this, LyricsActivity.class);
            i.putExtra(EXTRA_LYRICS, lyrics);
            startActivity(i);
        }
    }

    /**
     * Represents a search result. Contains a link so you can fetch the lyrics.
     */
    private static class LyricResult implements Parcelable {
        private String artist;
        private String title;
        private String link;

        private LyricSourceAdapter adapter;

        protected LyricResult(String artist, String title, String link, LyricSourceAdapter adapter) {
            this.artist = artist;
            this.title = title;
            this.link = link;
            this.adapter = adapter;
        }

        /**
         * All this crap is required for Parcelable interface, which lets you store these things
         * as data, and pass em around.
         */
        public static final Parcelable.Creator CREATOR = new Creator() {

            @Override
            public Object createFromParcel(Parcel source) {
                try {
                    String artist = source.readString();
                    String title = source.readString();
                    String link = source.readString();
                    String adapterclass = source.readString();
                    LyricSourceAdapter adapter = null;
                    adapter = (LyricSourceAdapter)Class.forName(adapterclass).getConstructor().newInstance();
                    return new LyricResult(artist, title, link, adapter);
                } catch (Exception e) {
                    Log.e("createFromParcel", "failed!", e);
                }
                return null;
            }

            @Override
            public LyricResult[] newArray(int size) {
                return new LyricResult[size];
            }
        };

        @Override public int describeContents() {
            return 0;
        }

        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(artist);
            dest.writeString(title);
            dest.writeString(link);
            dest.writeString(adapter.getClass().getName());
        }

        public String getArtist() {
            return artist;
        }

        public String getTitle() {
            return title;
        }

        public String getLink() {
            return link;
        }

        public LyricSourceAdapter getAdapter() {
            return adapter;
        }
    }

    /**
     * Used for local storage, a SQLite database!
     */
    private static class LyricsDatabase extends SQLiteOpenHelper {

        protected LyricsDatabase(Context context) {
            super(context, "LyricsDB", null, 2);
        }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE Lyrics (Artist text, Title text, Lyrics text, DtSaved datetime DEFAULT CURRENT_TIMESTAMP);");
            db.execSQL("CREATE UNIQUE INDEX ix_Lyrics ON Lyrics (Artist, Title);");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE Lyrics;");
            onCreate(db);
        }
    }

    /**
     * Represents a source of lyrics, e.g. a website, or a local database.
     */
    private interface LyricSourceAdapter {
        ArrayList<LyricResult> getSearchResults(String query);
        String getLyrics(LyricResult searchResult);
    }

    /**
     * Class for searching the local database of lyrics.
     */
    private class LocalLyricsAdapter implements LyricSourceAdapter {

        @Override public ArrayList<LyricResult> getSearchResults(String query) {
            String where = "Artist LIKE " + DatabaseUtils.sqlEscapeString(query+"%") + " OR Title LIKE " + DatabaseUtils.sqlEscapeString(query+"%");
            Cursor c = m_local_results.query(false, "Lyrics", new String[]{"Artist", "Title"}, where, null, null, null, "DtSaved DESC", "5");
            ArrayList<LyricResult> al = new ArrayList<LyricResult>(c.getCount());
            while (c.moveToNext()) {
                LyricResult lr = new LyricResult(c.getString(c.getColumnIndex("Artist")),
                                                 c.getString(c.getColumnIndex("Title")),
                                                 null, this);
                al.add(lr);
            }
            return al;
        }

        @Override public String getLyrics(LyricResult searchResult) {
            String where = "Artist = " + DatabaseUtils.sqlEscapeString(searchResult.getArtist())
                       + " AND Title = " + DatabaseUtils.sqlEscapeString(searchResult.getTitle());
            Cursor c = m_local_results.query(false, "Lyrics", new String[]{"Lyrics"}, where, null, null, null, null, null);
            c.moveToFirst();
            return c.getString(c.getColumnIndex("Lyrics"));
        }
    }

    /**
     * Class for searching azlyrics.com
     * Scrapes the azlyrics.com website, using XPath selectors.
     */
    private class AZLyricsAdapter implements LyricSourceAdapter {

        @Override public ArrayList<LyricResult> getSearchResults(String query) {
            try {
                //create URL and do the search, use this utility method to get back a valid document..
                Document doc = getCleanDocument("http://search.azlyrics.com/search.php?q=", query);

                //create searcher for the relevant sections
                String base = "//div[@id='inn']/div[@class='hlinks' and text()='Song results:']/following-sibling::div[@class='sen']";
                String titles = "a";
                String artists = "b";
                String links = "a/@href";
                XPath xPath = XPathFactory.newInstance().newXPath();
                XPathExpression baseexpr = xPath.compile(base);
                XPathExpression titlesexpr = xPath.compile(titles);
                XPathExpression artistsexpr = xPath.compile(artists);
                XPathExpression linksexpr = xPath.compile(links);

                //run the base expression to get a Node for each result.
                NodeList nl = (NodeList) baseexpr.evaluate(doc, XPathConstants.NODESET);
                ArrayList<LyricResult> al = new ArrayList<LyricResult>(nl.getLength());
                for (int i = 0; i < nl.getLength(); i++) {
                    Node n = nl.item(i);
                    //run the sub-expressions on each result to get each part of the search result.
                    LyricResult r = new LyricResult((String)artistsexpr.evaluate(n, XPathConstants.STRING),
                                                    (String)titlesexpr.evaluate(n, XPathConstants.STRING),
                                                    (String)linksexpr.evaluate(n, XPathConstants.STRING),
                                                    this);
                    al.add(r);
                }
                return al;
            } catch (XPathExpressionException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override public String getLyrics(LyricResult searchResult) {
            try {
                //Use the link on the result to get a Document from the web.
                Document d = getCleanDocument(searchResult.getLink(), "");
                //azlyrics use a nice comment in their HTML at the start of the lyrics :)
                XPathExpression expr = XPathFactory.newInstance().newXPath().compile("//div[comment()=' start of lyrics ']");
                return (String)expr.evaluate(d, XPathConstants.STRING);
            } catch (XPathExpressionException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Class for searching lyricsmania.com.
     * Scrapes the website, using XPath to pull out the relevant info.
     */
    private static class LyricsManiaAdapter implements LyricSourceAdapter {
        private static final String ROOT_URL = "http://www.lyricsmania.com";

        @Override public ArrayList<LyricResult> getSearchResults(String query) {
            try {
                Document d = getCleanDocument(ROOT_URL + "/searchnew.php?k=", query);
                XPathExpression expr = XPathFactory.newInstance().newXPath().compile("//div[@class='elenco']/div[@class='col-left']/ul/li/a");
                NodeList nl = (NodeList)expr.evaluate(d, XPathConstants.NODESET);
                ArrayList<LyricResult> r = new ArrayList<LyricResult>();

                //annoyingly, this site concatenates the song title - artist like so. Use a regex to
                //pull out just the artist.
                Pattern p = Pattern.compile("- (.*)$");
                for (int i=0; i<nl.getLength(); i++) {
                    Node n = nl.item(i);
                    String nodeText = n.getTextContent();
                    Matcher m = p.matcher(nodeText);
                    String artist = m.find() ? m.group(1) : "";
                    LyricResult lr = new LyricResult(artist,
                            n.getAttributes().getNamedItem("title").getNodeValue(),
                            n.getAttributes().getNamedItem("href").getNodeValue(),
                            this);
                    r.add(lr);
                }
                return r;

            } catch (XPathExpressionException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override public String getLyrics(LyricResult searchResult) {
            try {
                Document d = getCleanDocument(ROOT_URL + searchResult.getLink(), "");
                XPathExpression expr = XPathFactory.newInstance().newXPath().compile("//div[@class='lyrics-body']");
                return (String)expr.evaluate(d, XPathConstants.STRING);
            } catch (XPathExpressionException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
