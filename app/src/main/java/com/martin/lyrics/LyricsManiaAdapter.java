package com.martin.lyrics;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * Class for searching lyricsmania.com.
 * Scrapes the website, using XPath to pull out the relevant info.
 */
public class LyricsManiaAdapter implements SourceAdapter {

    private static final String ROOT_URL = "http://www.lyricsmania.com";

    @Override public ArrayList<SearchResult> getSearchResults(String query) {
        try {
            Document d = Functions.getCleanDocument(ROOT_URL + "/searchnew.php?k=", query);
            XPathExpression expr = XPathFactory.newInstance().newXPath().compile("//div[@class='elenco']/div[@class='col-left']/ul/li/a");
            NodeList nl = (NodeList)expr.evaluate(d, XPathConstants.NODESET);
            ArrayList<SearchResult> r = new ArrayList<SearchResult>();

            //annoyingly, this site concatenates the song title - artist like so. Use a regex to
            //pull out just the artist.
            Pattern p = Pattern.compile("- (.*)$");
            for (int i=0; i<nl.getLength(); i++) {
                Node n = nl.item(i);
                String nodeText = n.getTextContent();
                Matcher m = p.matcher(nodeText);
                String artist = m.find() ? m.group(1) : "";
                SearchResult lr = new SearchResult(artist,
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

    @Override public String getLyrics(SearchResult searchResult) {
        try {
            Document d = Functions.getCleanDocument(ROOT_URL + searchResult.getLink(), "");
            XPathExpression expr = XPathFactory.newInstance().newXPath().compile("//div[@class='lyrics-body']");
            return (String)expr.evaluate(d, XPathConstants.STRING);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
        return null;
    }
}