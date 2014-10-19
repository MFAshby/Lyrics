package com.martin.lyrics;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/**
 * Represents a search result. Contains a link so you can fetch the lyrics.
 * Created by martin on 19/10/14.
 */
public class SearchResult implements Parcelable {
    private String artist;
    private String title;
    private String link;

    private SourceAdapter adapter;

    protected SearchResult(String artist, String title, String link, SourceAdapter adapter) {
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
                SourceAdapter adapter = null;
                adapter = (SourceAdapter)Class.forName(adapterclass).getConstructor().newInstance();
                return new SearchResult(artist, title, link, adapter);
            } catch (Exception e) {
                Log.e("createFromParcel", "failed!", e);
            }
            return null;
        }

        @Override
        public SearchResult[] newArray(int size) {
            return new SearchResult[size];
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

    public SourceAdapter getAdapter() {
        return adapter;
    }
}
