package com.yieldnull.alioss.sample;

import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;

public class AuthPreferenceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.authorization_method);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragment {
        private SwitchPreference serverSwitch;
        private SwitchPreference plainSwitch;
        private EditTextPreference serverAddress;
        private EditTextPreference plainKey;
        private EditTextPreference plainSecret;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_auth);

            serverSwitch = (SwitchPreference) findPreference(getString(R.string.setting_key_switch_auth_server));
            plainSwitch = (SwitchPreference) findPreference(getString(R.string.setting_key_switch_auth_plain));
            serverAddress = (EditTextPreference) findPreference(getString(R.string.setting_key_text_auth_server_address));
            plainKey = (EditTextPreference) findPreference(getString(R.string.setting_key_text_auth_plain_id));
            plainSecret = (EditTextPreference) findPreference(getString(R.string.setting_key_text_auth_plain_secret));

            serverSwitch.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if ((Boolean) newValue) {
                        authEnableServer();
                    } else {
                        authEnablePlain();
                    }
                    return true;
                }
            });

            plainSwitch.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if ((Boolean) newValue) {
                        authEnablePlain();
                    } else {
                        authEnableServer();
                    }
                    return true;
                }
            });

            serverAddress.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    serverAddress.setSummary(String.valueOf(newValue));
                    return true;
                }
            });

            plainKey.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    plainKey.setSummary(String.format("%s: %s", getString(R.string.setting_summary_text_auth_plain_id), newValue));
                    return true;
                }
            });

            plainSecret.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    plainSecret.setSummary(String.format("%s: %s", getString(R.string.setting_summary_text_auth_plain_secret), newValue));
                    return true;
                }
            });

            loadSettings();
        }

        private void loadSettings() {
            if (serverAddress.getText() != null) {
                serverAddress.setSummary(serverAddress.getText());
            }

            if (plainKey.getText() != null) {
                plainKey.setSummary(String.format("%s: %s", getString(R.string.setting_summary_text_auth_plain_id), plainKey.getText()));

            }

            if (plainSecret.getText() != null) {
                plainSecret.setSummary(String.format("%s: %s", getString(R.string.setting_summary_text_auth_plain_secret), plainSecret.getText()));
            }

            if (serverSwitch.isChecked()) {
                authEnableServer();
            } else {
                authEnablePlain();
            }
        }

        private void authEnableServer() {
            serverAddress.setEnabled(true);
            serverSwitch.setEnabled(true);
            serverSwitch.setChecked(true);

            plainKey.setEnabled(false);
            plainSecret.setEnabled(false);
            plainSwitch.setChecked(false);
            plainSwitch.setEnabled(false);
        }

        private void authEnablePlain() {
            plainKey.setEnabled(true);
            plainSecret.setEnabled(true);
            plainSwitch.setEnabled(true);
            plainSwitch.setChecked(true);

            serverSwitch.setChecked(false);
            serverAddress.setEnabled(false);
            serverSwitch.setEnabled(false);
        }

    }
}
