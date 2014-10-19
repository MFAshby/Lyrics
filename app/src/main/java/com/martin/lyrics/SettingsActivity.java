package com.martin.lyrics;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

/**
 * Shows the settings fragment.
 * Created by martin on 19/10/14.
 */
public class SettingsActivity extends Activity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
            sp.registerOnSharedPreferenceChangeListener(this);
            addPreferencesFromResource(R.xml.preferences);

            PreferenceScreen ps = getPreferenceScreen();
            for (int i=0; i<ps.getPreferenceCount(); i++) {
                Preference p = ps.getPreference(i);
                updatePreference(p.getKey());
            }
        }

        @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updatePreference(key);
        }

        private void updatePreference(String key) {
            Preference p = findPreference(key);
            if (p instanceof ListPreference) {
                ListPreference lp = (ListPreference)p;
                p.setSummary(lp.getEntry());
            }
        }
    }
}
