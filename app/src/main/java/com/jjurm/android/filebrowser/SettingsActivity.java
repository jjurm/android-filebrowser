package com.jjurm.android.filebrowser;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

/**
 * Activity with settings using PreferenceFragment
 */
public class SettingsActivity extends AppCompatActivity {

    public static final String KEY_PREF_DEFAULT_DIRECTORY = "pref_defaultDir";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // load preferences
            addPreferencesFromResource(R.xml.preferences);
        }

    }

}
