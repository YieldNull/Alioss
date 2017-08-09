package com.yieldnull.alioss.sample;

import android.annotation.SuppressLint;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.yieldnull.alioss.OssConfig;
import com.yieldnull.alioss.OssService;

public class MainPreferenceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        OSSCredentialProvider provider = new OssConfig.DefaultCustomSignerCredentialProvider(
//                "http://192.168.1.104/auth/custom");

        @SuppressLint("HardwareIds") OSSCredentialProvider provider = new OssConfig.DefaultFederationCredentialProvider(
                "http://192.168.1.104/auth/sts",
                "yieldnull-test",
                Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));

        OssService.init(new OssConfig.Builder(
                "http://oss-cn-hangzhou.aliyuncs.com",
                "yieldnull-test", provider
        ).build());

//        OssService.startService(this);


        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

    }

    /**
     * Created by YieldNull on 09/08/2017.
     */
    public static class SettingsFragment extends PreferenceFragment {

        private EditTextPreference bucketText;
        private ListPreference endpointList;

        private ListPreference filesizeList;
        private ListPreference alarmList;
        private EditTextPreference testOnlineText;

        private PreferenceScreen folderIncludeScreen;
        private PreferenceScreen folderExcludeScreen;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.pref_main);

            endpointList = (ListPreference) findPreference(getString(R.string.setting_key_list_endpoint));
            bucketText = (EditTextPreference) findPreference(getString(R.string.setting_key_text_bucket));
            filesizeList = (ListPreference) findPreference(getString(R.string.setting_key_list_filesize));
            alarmList = (ListPreference) findPreference(getString(R.string.setting_key_list_alarm));
            testOnlineText = (EditTextPreference) findPreference(getString(R.string.setting_key_text_testonline));
            folderIncludeScreen = (PreferenceScreen) findPreference(getString(R.string.setting_key_subscreen_folder_include));
            folderExcludeScreen = (PreferenceScreen) findPreference(getString(R.string.setting_key_subscreen_folder_exclude));


            endpointList.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    endpointList.setValue(String.valueOf(newValue));
                    preference.setSummary(String.format("%s: %s", getString(R.string.setting_summary_list_endpoint),
                            endpointList.getEntry()));
                    return false;
                }
            });

            bucketText.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    bucketText.setText(String.valueOf(newValue));
                    bucketText.setSummary(String.format("%s: %s", getString(R.string.setting_summary_text_bucket), newValue));
                    return false;
                }
            });


            filesizeList.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    filesizeList.setValue(String.valueOf(newValue));
                    filesizeList.setSummary(String.format("%s: %s", getString(R.string.setting_summary_list_filesize), filesizeList.getEntry()));
                    return false;
                }
            });

            alarmList.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    alarmList.setValue(String.valueOf(newValue));
                    alarmList.setSummary(String.format("%s: %s", getString(R.string.setting_summary_list_alarm), alarmList.getEntry()));
                    return false;
                }
            });

            testOnlineText.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    testOnlineText.setText(String.valueOf(newValue));
                    testOnlineText.setSummary(String.format("%s: %s", getString(R.string.setting_summary_text_testonline), testOnlineText.getText()));
                    return false;
                }
            });

            folderIncludeScreen.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    return false;
                }
            });

            loadSettings();
        }

        private void loadSettings() {
            if (endpointList.getEntry() != null) {
                endpointList.setSummary(String.format("%s: %s", getString(R.string.setting_summary_list_endpoint),
                        endpointList.getEntry()));
            }

            if (bucketText.getText() != null) {
                bucketText.setSummary(String.format("%s: %s", getString(R.string.setting_summary_text_bucket), bucketText.getText()));
            }



            if (filesizeList.getEntry() != null) {
                filesizeList.setSummary(String.format("%s: %s", getString(R.string.setting_summary_list_filesize), filesizeList.getEntry()));
            }

            if (alarmList.getEntry() != null) {
                alarmList.setSummary(String.format("%s: %s", getString(R.string.setting_summary_list_alarm), alarmList.getEntry()));
            }


            if (testOnlineText.getText() != null) {
                testOnlineText.setSummary(String.format("%s: %s", getString(R.string.setting_summary_text_testonline), testOnlineText.getText()));
            }
        }

    }
}
