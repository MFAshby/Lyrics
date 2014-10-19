package com.martin.lyrics;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

/**
 * Class for searching the local database of lyrics.
 * Created by martin on 19/10/14.
 */
public class LocalAdapter implements SourceAdapter {

    //A local database to store results in. Static, as just used once per application.
    private static SQLiteDatabase m_local_results = null;

    public LocalAdapter(Context cx) {
        //make sure the local database is ready.
        getDatabase(cx);
    }

    private static SQLiteDatabase getDatabase(Context cx) {
        if (m_local_results == null) {
            m_local_results = new LyricsDatabase(cx).getWritableDatabase();
        }
        return m_local_results;
    }

    @Override public ArrayList<SearchResult> getSearchResults(String query) {
        String where = "Artist LIKE " + DatabaseUtils.sqlEscapeString(query + "%") + " OR Title LIKE " + DatabaseUtils.sqlEscapeString(query+"%");
        Cursor c = m_local_results.query(false, "Lyrics", new String[]{"Artist", "Title"}, where, null, null, null, "DtSaved DESC", "5");
        ArrayList<SearchResult> al = new ArrayList<SearchResult>(c.getCount());
        while (c.moveToNext()) {
            SearchResult lr = new SearchResult(c.getString(c.getColumnIndex("Artist")),
                    c.getString(c.getColumnIndex("Title")),
                    null, this);
            al.add(lr);
        }
        return al;
    }

    @Override public String getLyrics(SearchResult searchResult) {
        String where = "Artist = " + DatabaseUtils.sqlEscapeString(searchResult.getArtist())
                + " AND Title = " + DatabaseUtils.sqlEscapeString(searchResult.getTitle());
        Cursor c = m_local_results.query(false, "Lyrics", new String[]{"Lyrics"}, where, null, null, null, null, null);
        c.moveToFirst();
        return c.getString(c.getColumnIndex("Lyrics"));
    }

    public static void saveLyrics(Context cx, SearchResult r, String lyrics) {
        ContentValues cv = new ContentValues();
        cv.put("Artist", r.getArtist());
        cv.put("Title", r.getTitle());
        cv.put("Lyrics", lyrics);

        //store the lyrics in the DB. Replace any existing ones for the same artist (unique index
        //is on artist_title)
        getDatabase(cx).insertWithOnConflict("Lyrics", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static void deleteAll(Context cx) {
        getDatabase(cx).delete("Lyrics", null, null);
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
}