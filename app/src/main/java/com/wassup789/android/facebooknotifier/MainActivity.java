package com.wassup789.android.facebooknotifier;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.widget.LoginButton;
import com.wassup789.android.facebooknotifier.objectClasses.DoubleListItem;
import com.wassup789.android.facebooknotifier.objectClasses.DoubleListItemAdapter;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private static String TAG = "MainActivity";
    public static Boolean hasStartedBackground = false;

    public Boolean fromListViewChecked = false;

    public static boolean default_refreshToggle = true;
    public static int default_refreshInterval = 1;
    public static String default_shownNotifications = "[]";

    public int listIndex_facebookLogin;

    public boolean previousState;
    private UiLifecycleHelper uiHelper;
    private Session.StatusCallback callback = new Session.StatusCallback() {
        @Override
        public void call(final Session session, final SessionState state, final Exception exception) {
            onSessionStateChange(session, state, exception);
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        uiHelper.onActivityResult(requestCode, resultCode, data);
    }

    private void onSessionStateChange(Session session, SessionState state, Exception exception) {
        updateLoginListItem(session);

        if (state.isOpened() && previousState != state.isOpened()) {
            previousState = state.isOpened();

            Intent intent = new Intent(this, BackgroundService.class);
            stopService(intent);
            startService(intent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if(!hasStartedBackground) {
            Intent intent = new Intent(this, BackgroundService.class);
            startService(intent);
            hasStartedBackground = true;
        }

        LoginButton loginButton = (LoginButton) findViewById(R.id.authButton);
        loginButton.setPublishPermissions(Arrays.asList("manage_notifications"));
        loginButton.setSessionStatusCallback(callback);

        uiHelper = new UiLifecycleHelper(MainActivity.this, callback);
        uiHelper.onCreate(savedInstanceState);

        final Session session = Session.getActiveSession();

        SharedPreferences settings = getSharedPreferences("settings", Context.MODE_PRIVATE);

        final ArrayList<DoubleListItem> data = new ArrayList<DoubleListItem>();
        data.add(new DoubleListItem("divider_facebook", true,  "Facebook", null, false));
        data.add(new DoubleListItem("facebook_login", false, ((session == null || session.isClosed() || !session.isOpened()) ? getString(R.string.facebookLogin_label) : getString(R.string.facebookLogin_label_alt)), null, false));
        listIndex_facebookLogin = data.size() - 1;
        data.add(new DoubleListItem("divider_general", true,  "General", null, false));
        data.add(new DoubleListItem("general_toggleNotifier", false, getString(R.string.enableNotifier_label), null, true).setSwitchValue(settings.getBoolean("refreshToggle", default_refreshToggle)));
        data.add(new DoubleListItem("general_editRefreshInterval", false, getString(R.string.refreshToggle_label), getString(R.string.refreshToggle_desc), false));

        ListView list = DoubleListItemAdapter.getListView(this, (ListView) findViewById(R.id.settingsListView), data, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                int position = Integer.parseInt(compoundButton.getText().toString());
                if (!fromListViewChecked) {
                    switch (data.get(position).name) {
                        case "general_toggleNotifier":
                            setNotifierEnabled(!isChecked);
                            break;
                    }
                } else
                    fromListViewChecked = false;
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View view, int position, long l) {
                DoubleListItem item = (DoubleListItem) adapter.getItemAtPosition(position);

                switch (item.name) {
                    case "facebook_login":
                        LoginButton loginButton = (LoginButton) findViewById(R.id.authButton);
                        loginButton.performClick();
                        break;
                    case "general_toggleNotifier":
                        if (item.listSwitch != null) {
                            setNotifierEnabled(!item.listSwitch.isChecked());
                            fromListViewChecked = true;
                            item.listSwitch.setChecked(!item.listSwitch.isChecked());
                        } else
                            Toast.makeText(MainActivity.this, "Unable to save setting", Toast.LENGTH_LONG).show();
                        break;
                    case "general_editRefreshInterval":
                        setRefreshInterval();
                        break;
                }
            }
        });
    }

    public void updateLoginListItem(Session session) {
        View v = ((ListView) findViewById(R.id.settingsListView)).getChildAt(listIndex_facebookLogin);
        TextView listTitle = (TextView) v.findViewById(R.id.listViewTitle);

        listTitle.setText((session == null || session.isClosed() || !session.isOpened()) ? getString(R.string.facebookLogin_label) : getString(R.string.facebookLogin_label_alt));
    }

    public void setNotifierEnabled(boolean value){
        SharedPreferences settings = getSharedPreferences("settings", Context.MODE_PRIVATE);
        SharedPreferences.Editor settingsEditor = settings.edit();
        settingsEditor.putBoolean("refreshToggle", value);
        settingsEditor.commit();

        Intent intent = new Intent(this, BackgroundService.class);

        stopService(intent);
        if (value)
            startService(intent);
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
    }

    public void setRefreshInterval(){
        SharedPreferences settings = getSharedPreferences("settings", Context.MODE_PRIVATE);
        new MaterialDialog.Builder(this)
                .title(R.string.dialog_setrefreshinterval)
                .content(R.string.dialog_setrefreshinterval_content)
                .inputType(InputType.TYPE_CLASS_NUMBER)
                .inputRange(1, 5)
                .positiveText(R.string.dialog_save)
                .input(getString(R.string.dialog_setrefreshinterval_default), "" + settings.getInt("refreshInterval", default_refreshInterval), false, new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(MaterialDialog dialog, CharSequence input) {
                        int value = Integer.parseInt(input.toString());
                        if (value <= 0) {
                            Toast.makeText(MainActivity.this, "Invalid interval value", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        SharedPreferences settings = getSharedPreferences("settings", Context.MODE_PRIVATE);
                        SharedPreferences.Editor settingsEditor = settings.edit();
                        settingsEditor.putInt("refreshInterval", value);
                        settingsEditor.commit();
                        Toast.makeText(MainActivity.this, "Saved", Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(MainActivity.this, BackgroundService.class);

                        stopService(intent);
                        startService(intent);
                    }
                }).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                new MaterialDialog.Builder(this)
                        .title(getString(R.string.dialog_about_title))
                        .positiveText(getString(R.string.dialog_dismiss))
                        .content(getString(R.string.dialog_about_desc))
                        .show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
