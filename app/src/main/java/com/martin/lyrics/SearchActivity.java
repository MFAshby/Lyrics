package com.martin.lyrics;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        m_list_view = (ListView) findViewById(R.id.listView);
        m_results_adapter = new LyricsResultsAdapter(this);
        m_list_view.setAdapter(m_results_adapter);
        m_list_view.setOnItemClickListener(new OnListItemClickListener());

        m_progress_bar = (ProgressBar)findViewById(R.id.progressBar);
        m_progress_bar.setVisibility(View.INVISIBLE);

        if (savedInstanceState != null) {
            m_results = savedInstanceState.getParcelableArrayList(SEARCH_RESULT);
            m_results_adapter.clear();
            m_results_adapter.addAll(m_results);
            m_results_adapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.my, menu);

        //set up the search view
        SearchView m_search_view = (SearchView) menu.findItem(R.id.action_search).getActionView();
        m_search_view.setOnQueryTextListener(new OnSearchQueryTextListener());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        b.putParcelableArrayList(SEARCH_RESULT, m_results);
    }

    /**
     * Used by the search bar when the text changes. Search the intenet on submit..
     * and search locals only on text change.
     */
    class OnSearchQueryTextListener implements SearchView.OnQueryTextListener {
        @Override
        public boolean onQueryTextSubmit(String query) {
            //search the internet
            new InternetLyricSearcher().execute(query);
            return true;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            //search locally stored results
            return false;
        }
    }

    /**
     * Used when selecting an item from the list. Kicks of a retrieve of the selected lyrics
     */
    class OnListItemClickListener implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            new InternetLyricsGrabber().execute(position);
        }
    }

    /**
     * Used by the ListView on the screen to display lyrics with a custom view.
     */
    class LyricsResultsAdapter extends ArrayAdapter<LyricResult> {

        public LyricsResultsAdapter(Context context) {
            super(context, R.layout.list_item);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            LyricResult lr = getItem(position);
            View v_use = convertView != null ? convertView : getLayoutInflater().inflate(R.layout.list_item, parent, false);
            ((TextView)v_use.findViewById(R.id.li_artist)).setText(lr.artist);
            ((TextView)v_use.findViewById(R.id.li_song_title)).setText(lr.title);
            return v_use;
        }
    }

    /**
     *  Background task to search the net for lyrics..
     */
    class InternetLyricSearcher extends AsyncTask<String, Void, ArrayList<LyricResult>> {

        @Override
        protected void onPreExecute() {
            //kick off the progress spinner, hide the list view
            m_list_view.setVisibility(View.INVISIBLE);
            m_progress_bar.setVisibility(View.VISIBLE);
        }

        @Override
        protected ArrayList<LyricResult> doInBackground(String... search) {
            LyricSourceAdapter lyricsAdapter = new AZLyricsAdapter();
            return lyricsAdapter.getSearchResults(search[0]);
        }

        @Override
        protected void onPostExecute(ArrayList<LyricResult> results) {
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
     */
    class InternetLyricsGrabber extends AsyncTask<Integer, Void, String> {

        @Override
        public void onPreExecute(){
            m_list_view.setVisibility(View.INVISIBLE);
            m_progress_bar.setVisibility(View.VISIBLE);
        }
        @Override
        protected String doInBackground(Integer... params) {
            LyricSourceAdapter adapter = new AZLyricsAdapter();
            return adapter.getLyrics(m_results_adapter.getItem(params[0]));
        }

        @Override
        protected void onPostExecute(String lyrics) {
            m_list_view.setVisibility(View.VISIBLE);
            m_progress_bar.setVisibility(View.INVISIBLE);
            Intent i = new Intent(SearchActivity.this, LyricsActivity.class);
            i.putExtra(EXTRA_LYRICS, lyrics);
            startActivity(i);
        }
    }

    /**
     * encapsulates a search result. Just show artist/title for now, use the link to grab them from the net
     */
    static class LyricResult implements Parcelable {
        String artist;
        String title;
        String link;

        public static final Parcelable.Creator CREATOR = new Creator() {

            @Override
            public Object createFromParcel(Parcel source) {
                LyricResult r = new LyricResult();
                r.artist = source.readString();
                r.title = source.readString();
                r.link = source.readString();
                return r;
            }

            @Override
            public LyricResult[] newArray(int size) {
                return new LyricResult[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(artist);
            dest.writeString(title);
            dest.writeString(link);
        }
    }

    /**
     * Base class for lyrics site.
     */
    interface LyricSourceAdapter {
        ArrayList<LyricResult> getSearchResults(String query);
        String getLyrics(LyricResult searchResult);
    }

    class AZLyricsAdapter implements LyricSourceAdapter {

        @Override
        public ArrayList<LyricResult> getSearchResults(String query) {
            //create URL and do the search
            try {
                String encodedSearch = URLEncoder.encode(query, "UTF-8");
                URL url = new URL("http://search.azlyrics.com/search.php?q=" + encodedSearch);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();

                //clean up resulting page and convert to DOM
                TagNode tn = new HtmlCleaner().clean(con.getInputStream());
                Document doc = new DomSerializer(new CleanerProperties()).createDOM(tn);

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
                    LyricResult r = new LyricResult();
                    r.title = (String)titlesexpr.evaluate(n, XPathConstants.STRING);
                    r.artist = (String)artistsexpr.evaluate(n, XPathConstants.STRING);
                    r.link = (String)linksexpr.evaluate(n, XPathConstants.STRING);
                    al.add(r);
                    Log.d("LyricResult!", r.title + " " + r.link);
                }
                return al;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (XPathExpressionException e) {
                e.printStackTrace();
            } catch (ParserConfigurationException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public String getLyrics(LyricResult searchResult) {
            try {
                Document d = new DomSerializer(new CleanerProperties()).createDOM(
                                new HtmlCleaner().clean(
                                        new URL(searchResult.link)
                                                .openConnection()
                                                .getInputStream()));
                XPathExpression expr = XPathFactory.newInstance().newXPath().compile("//div[comment()=' start of lyrics ']");
                return (String)expr.evaluate(d, XPathConstants.STRING);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ParserConfigurationException e) {
                e.printStackTrace();
            } catch (XPathExpressionException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
