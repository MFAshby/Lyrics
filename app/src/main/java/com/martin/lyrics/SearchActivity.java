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
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;


public class SearchActivity extends Activity {
    protected static final String SEARCH_RESULT = "com.martin.lyrics.search_results";
    protected static final String EXTRA_LYRICS = "com.martin.lyrics.EXTRA_LYRICS";

    private ArrayList<LyricResult> m_results;
    private ArrayAdapter<LyricResult> m_results_adapter;
    private ProgressBar m_progress_bar;
    private ListView m_list_view;

    private SQLiteDatabase m_local_results;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        m_list_view = (ListView) findViewById(R.id.listView);
        m_results_adapter = new LyricsResultsAdapter(this);
        m_list_view.setAdapter(m_results_adapter);
        m_list_view.setOnItemClickListener(new OnListItemClickListener());

        m_progress_bar = (ProgressBar)findViewById(R.id.progressBar);
        m_progress_bar.setVisibility(View.INVISIBLE);

        m_local_results = new LyricsDatabase(this).getWritableDatabase();

        if (savedInstanceState != null) {
            m_results = savedInstanceState.getParcelableArrayList(SEARCH_RESULT);
            m_results_adapter.clear();
            m_results_adapter.addAll(m_results);
            m_results_adapter.notifyDataSetChanged();
        }

        //search locally stored results initially
        new LyricSearcher(new LocalLyricsAdapter()).execute("");
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.my, menu);

        //set up the search view
        SearchView m_search_view = (SearchView) menu.findItem(R.id.action_search).getActionView();
        m_search_view.setOnQueryTextListener(new OnSearchQueryTextListener());

        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override public void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        b.putParcelableArrayList(SEARCH_RESULT, m_results);
    }

    /**
     * Useful common function for getting a web page that can be parsed as XML sensibly.
     * @param urlString The URL of the page to fetch.
     * @param queryString Non-encoded query string (e.g. search terms from a text field)
     * @return Parsed Document object ready for querying with XPath.
     */
    private static Document getCleanDocument(String urlString, String queryString) {
        try {
            String encodedSearch = URLEncoder.encode(queryString, "UTF-8");
            URL url = new URL(urlString + encodedSearch);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            //clean up resulting page and convert to DOM
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
     * and search locals only on text change.
     */
    private class OnSearchQueryTextListener implements SearchView.OnQueryTextListener {

        @Override public boolean onQueryTextSubmit(String query) {
            //search the internet
            //new LyricSearcher(new AZLyricsAdapter()).execute(query);
            new LyricSearcher(new LyricsManiaAdapter()).execute(query);
            return true;
        }

        @Override public boolean onQueryTextChange(String newText) {
            //search locally stored results
            new LyricSearcher(new LocalLyricsAdapter()).execute(newText);
            return true;
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
     * encapsulates a search result. Just show artist/title for now, use the link to grab them from the net
     */
    private static class LyricResult implements Parcelable {
        private String artist;
        private String title;
        private String link;

        private LyricSourceAdapter adapter;

        LyricResult(String artist, String title, String link, LyricSourceAdapter adapter) {
            this.artist = artist;
            this.title = title;
            this.link = link;
            this.adapter = adapter;
        }

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

        public LyricsDatabase(Context context) {
            super(context, "LyricsDB", null, 2);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE Lyrics (Artist text, Title text, Lyrics text, DtSaved datetime DEFAULT CURRENT_TIMESTAMP);");
            db.execSQL("CREATE UNIQUE INDEX ix_Lyrics ON Lyrics (Artist, Title);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE Lyrics;");
            onCreate(db);
        }
    }

    /**
     * Base class for lyrics site.
     */
    private interface LyricSourceAdapter {
        ArrayList<LyricResult> getSearchResults(String query);
        String getLyrics(LyricResult searchResult);
    }

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

    private class AZLyricsAdapter implements LyricSourceAdapter {

        @Override public ArrayList<LyricResult> getSearchResults(String query) {
            //create URL and do the search
            try {
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

                //run the base expression to get results
                NodeList nl = (NodeList) baseexpr.evaluate(doc, XPathConstants.NODESET);
                ArrayList<LyricResult> al = new ArrayList<LyricResult>(nl.getLength());
                for (int i = 0; i < nl.getLength(); i++) {
                    Node n = nl.item(i);
                    LyricResult r = new LyricResult((String)artistsexpr.evaluate(n, XPathConstants.STRING),
                                                    (String)titlesexpr.evaluate(n, XPathConstants.STRING),
                                                    (String)linksexpr.evaluate(n, XPathConstants.STRING),
                                                    this);
                    al.add(r);
                    Log.d("LyricResult!", r.title + " " + r.link);
                }
                return al;
            } catch (XPathExpressionException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override public String getLyrics(LyricResult searchResult) {
            try {
                Document d = getCleanDocument(searchResult.link, "");
                XPathExpression expr = XPathFactory.newInstance().newXPath().compile("//div[comment()=' start of lyrics ']");
                return (String)expr.evaluate(d, XPathConstants.STRING);
            } catch (XPathExpressionException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private static class LyricsManiaAdapter implements LyricSourceAdapter {
        private static final String ROOT_URL = "http://www.lyricsmania.com";

        @Override public ArrayList<LyricResult> getSearchResults(String query) {
            try {
                Document d = getCleanDocument(ROOT_URL + "/searchnew.php?k=", query);
                XPathExpression expr = XPathFactory.newInstance().newXPath().compile("//div[@class='elenco']/div[@class='col-left']/ul/li/a");
                NodeList nl = (NodeList)expr.evaluate(d, XPathConstants.NODESET);
                ArrayList<LyricResult> r = new ArrayList<LyricResult>();
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
