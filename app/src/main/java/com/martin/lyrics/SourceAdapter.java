package com.martin.lyrics;

import java.util.ArrayList;

/**
 * Represents a source of lyrics, e.g. a website, or a local database.
 * Created by martin on 19/10/14.
 */
public interface SourceAdapter {
    ArrayList<SearchResult> getSearchResults(String query);
    String getLyrics(SearchResult searchResult);
}