package science.anthonyalves.vinedownloader;


import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.File;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class MainActivity extends AppCompatPreferenceActivity {

    static Activity mActivity;
    final int READ_EXTERNAL_STORAGE_CODE = 867;
    SharedPreferences mSharedPreferences;
    Preference dirChooser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = this;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new GeneralPreferenceFragment()).commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_launch_vine) {
            PackageManager pm = getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(Constants.PACKAGE_NAME);
            if (launchIntent != null) {
                startActivity(launchIntent);
            } else {
                Toast.makeText(MainActivity.this, "Vine app not found :/", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || GeneralPreferenceFragment.class.getName().equals(fragmentName);
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public class GeneralPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);

            dirChooser = (Preference) findPreference(Constants.DOWNLOAD_LOCATION_KEY);

            File sharedPrefsDir = new File(mActivity.getFilesDir(), "../shared_prefs");
            File sharedPrefsFile = new File(sharedPrefsDir, getPreferenceManager().getSharedPreferencesName() + ".xml");
            if (sharedPrefsFile.exists()) {
                sharedPrefsFile.setReadable(true, false);
            }
            String dir = mSharedPreferences.getString(Constants.DOWNLOAD_LOCATION_KEY, Constants.DEFAULT_LOCATION);
            dirChooser.setSummary(dir);

            dirChooser.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        final String permissionCode = Manifest.permission.READ_EXTERNAL_STORAGE;

                        if (checkSelfPermission(permissionCode) != PackageManager.PERMISSION_GRANTED) {
                            if (shouldShowRequestPermissionRationale(permissionCode)) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                                builder.setMessage(getString(R.string.request_read_ext_message))
                                        .setTitle(getString(R.string.request_read_ext_title));
                                builder.setPositiveButton(getString(R.string.grant), new DialogInterface.OnClickListener() {
                                    @TargetApi(Build.VERSION_CODES.M)
                                    public void onClick(DialogInterface dialog, int id) {
                                        requestPermissions(new String[]{permissionCode}, READ_EXTERNAL_STORAGE_CODE);
                                    }
                                });
                                builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        // nothing
                                    }
                                });

                                AlertDialog dialog = builder.create();
                                dialog.show();
                                return true;
                            }
                        }
                    }
                    chooseDirectory();
                    return true;
                }
            });
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);

            switch (requestCode) {
                case READ_EXTERNAL_STORAGE_CODE:
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        chooseDirectory();
                    }
                    break;
                default:
                    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                    break;
            }
        }

        private void chooseDirectory() {
            SimpleFileDialog fileDialog = new SimpleFileDialog(mActivity, "FolderChoose",
                    new SimpleFileDialog.SimpleFileDialogListener() {
                        @Override
                        public void onChosenDir(String chosenDir) {
                            // The code in this function will be executed when the dialog OK button is pushed
                            Toast.makeText(mActivity, "Folder: " + chosenDir, Toast.LENGTH_LONG).show();
                            SharedPreferences.Editor editor = mSharedPreferences.edit();
                            editor.putString(mActivity.getString(R.string.download_location_key), chosenDir); // value to store
                            editor.apply();
                            dirChooser.setSummary(chosenDir);
                        }
                    });
            String dir = mSharedPreferences.getString(getString(R.string.download_location_key), Constants.DEFAULT_LOCATION);
            fileDialog.chooseFile_or_Dir(dir);
        }
    }
}
