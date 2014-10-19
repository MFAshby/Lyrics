package com.martin.lyrics;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * Class for searching azlyrics.com
 * Scrapes the azlyrics.com website, using XPath selectors.
 * Created by martin on 19/10/14.
 */
public class AZLyricsAdapter implements SourceAdapter {

    @Override public ArrayList<SearchResult> getSearchResults(String query) {
        try {
            //create URL and do the search, use this utility method to get back a valid document..
            Document doc = Functions.getCleanDocument("http://search.azlyrics.com/search.php?q=", query);

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
            ArrayList<SearchResult> al = new ArrayList<SearchResult>(nl.getLength());
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                //run the sub-expressions on each result to get each part of the search result.
                SearchResult r = new SearchResult((String)artistsexpr.evaluate(n, XPathConstants.STRING),
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

    @Override public String getLyrics(SearchResult searchResult) {
        try {
            //Use the link on the result to get a Document from the web.
            Document d = Functions.getCleanDocument(searchResult.getLink(), "");
            //azlyrics use a nice comment in their HTML at the start of the lyrics :)
            XPathExpression expr = XPathFactory.newInstance().newXPath().compile("//div[comment()=' start of lyrics ']");
            return (String)expr.evaluate(d, XPathConstants.STRING);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
        return null;
    }
}