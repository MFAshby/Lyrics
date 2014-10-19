package com.martin.lyrics;

import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.DomSerializer;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.w3c.dom.Document;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Because java doesn't do functional, but I want a place to store useful
 * utility stuff that doesn't belong on a class.
 * Created by martin on 19/10/14.
 */
public abstract class Functions {

    /**
     * Useful common function for getting a web page that can be parsed as XML sensibly.
     * @param urlString The URL of the page to fetch.
     * @param queryString Non-encoded query string (e.g. search terms from a text field)
     * @return Parsed Document object ready for querying with XPath.
     */
    public static Document getCleanDocument(String urlString, String queryString) {
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
}
