package com.wassup789.android.facebooknotifier;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphUser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class BackgroundService extends Service {
    public static String TAG = "BackgroundService";

    private SharedPreferences settings;
    private static Timer timer = new Timer();
    private static Boolean isRunning = false;
    private static int currentId = 1;

    private final IBinder mBinder = new LocalBinder();
    ResultReceiver resultReceiver;
    public class LocalBinder extends Binder {
        BackgroundService getService() {
            return BackgroundService.this;
        }
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Service started");

        sendMessage(0, "Sleeping");

        settings = getSharedPreferences("settings", Context.MODE_PRIVATE);

        Boolean toStart = settings.getBoolean("refreshToggle", MainActivity.default_refreshToggle);
        Integer timeMinutes = settings.getInt("refreshInterval", MainActivity.default_refreshInterval);

        if (toStart && timeMinutes > 0)
            timer.scheduleAtFixedRate(timerTask, 1000, timeMinutes * 60 * 1000);
        else if (timeMinutes < 1)
            Log.e("BackgroundService", "Invalid refresh interval, value is negative or zero.");
    }

    public void onDestroy() {
        sendMessage(0, "Killed");
        Log.i(TAG, "Service killed");
    }

    private TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
            if(!isRunning)
                isRunning = true;
            else
                return;

            sendMessage(0, "Working");

            final Session session = Session.openActiveSessionFromCache(BackgroundService.this);
            if (session != null && !session.isClosed() && session.isOpened()) {
                Log.i(TAG, "Checking for new Notifications");
                showNotifications(session);
            }
            isRunning = false;
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id: " + startId + "; " + intent);
        if(intent != null)
            resultReceiver = intent.getParcelableExtra("receiver");
        return START_STICKY;
    }

    public void sendMessage(int resultCode, String value) {
        Bundle bundle = new Bundle();
        bundle.putString("data", value);
        if(resultReceiver != null)
            resultReceiver.send(resultCode, bundle);
    }

    public void showNotifications(final Session session) {
        final Bundle parameters = new Bundle();
        parameters.putString("include_read", "true");
        parameters.putString("fields", "id");

        new Request(session, "/me/notifications", parameters, HttpMethod.GET, new Request.Callback() {
            @Override
            public void onCompleted(Response response) {
                try {
                    //Get the list of notifications
                    JSONArray data = (JSONArray) response.getGraphObject().asMap().get("data");
                    ArrayList<String> shownNotifications = getStringArray("shownNotifications");

                    // Loop through array to get ID
                    for(int i = 0; i < data.length(); i++) {
                        String id = ((JSONObject) data.get(i)).getString("id");

                        // Check if notification has been displayed before
                        boolean doesExist = false;
                        for(int j = 0; j < shownNotifications.size(); j++) {
                            if(shownNotifications.get(j).equals(id))
                                doesExist = true;
                        }

                        if(doesExist)
                            continue;
                        else {
                            shownNotifications.add(id);
                            if(shownNotifications.size() > 20)
                                shownNotifications.remove(0);

                            saveStringArray("shownNotifications", shownNotifications);
                        }

                        //Then get this specific notification's information
                        new Request(session, "/" + id, new Bundle(), HttpMethod.GET, new Request.Callback() {
                            @Override
                            public void onCompleted(Response response) {
                                try {
                                    final JSONObject data = response.getGraphObject().getInnerJSONObject();
                                    String appId = data.getJSONObject("application").getString("id");

                                    //Then get the ID for the category to get an icon
                                    new Request(session, "/" + appId, new Bundle(), HttpMethod.GET, new Request.Callback() {
                                        @Override
                                        public void onCompleted(Response response) {
                                            try {
                                                // All the notification stuff goes here.
                                                JSONObject notifData = response.getGraphObject().getInnerJSONObject();
                                                String logoUrl = notifData.getString("logo_url");
                                                URL url = new URL(logoUrl);
                                                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                                                connection.setDoInput(true);
                                                connection.connect();
                                                InputStream in = connection.getInputStream();
                                                Bitmap largeIcon = BitmapFactory.decodeStream(in);

                                                Intent notificationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(data.getString("link")));

                                                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                                                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(BackgroundService.this)
                                                        .setLargeIcon(largeIcon)
                                                        .setSmallIcon(R.drawable.ic_facebook)
                                                        .setColor(Color.parseColor("#3B5998"))
                                                        .setContentTitle("Facebook")
                                                        .setContentText(data.getString("title"))
                                                        .setContentIntent(PendingIntent.getActivity(BackgroundService.this, 0, notificationIntent, 0))
                                                        .setContentInfo(formatISO8601(data.getString("updated_time")))
                                                        .setAutoCancel(true)
                                                        .setStyle(new NotificationCompat.BigTextStyle()
                                                                .setBigContentTitle("Facebook")
                                                                .bigText(data.getString("title"))
                                                        );
                                                if (data.has("object") && data.getJSONObject("object").has("message")) {
                                                    String message = data.getJSONObject("object").getString("message");
                                                    mBuilder = mBuilder.setContentText(message.replaceAll("\n", ""))
                                                            .setStyle(new NotificationCompat.BigTextStyle()
                                                                    .setBigContentTitle(data.getString("title"))
                                                                    .bigText(message)
                                                            );
                                                }

                                                notificationManager.notify(currentId++, mBuilder.build());
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            } catch (MalformedURLException e) {
                                                e.printStackTrace();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }).executeAndWait();
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        }).executeAndWait();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }).executeAndWait();
    }

    public String formatISO8601(String iso_date) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZ").parse(iso_date);
            date.setHours(date.getHours());
            Date now = new Date();

            String hour = "" + (date.getHours() > 11 ? (date.getHours() - 12 + 1) : (date.getHours() + 1));
            //hour = (hour.length() == 1 ? "0" : "") + hour;

            String minute = "" + date.getMinutes();
            minute = (minute.length() == 1 ? "0" : "") + minute;

            String ampm = (date.getHours() > 11) ? "PM" : "AM";

            Long seconds = (now.getTime() / 1000) - (date.getTime() / 1000);
            String output = "";
            if(seconds < (24*60*60) && date.getDate() == now.getDate())
                output = String.format("today at %s:%s%s", hour, minute, ampm);
            else if(seconds < 2*24*60*60 && date.getDate() == now.getDate()-1)
                output = String.format("yesterday at %s:%s%s", hour, minute, ampm);
            else if(seconds < 7*24*60*60)
                output = String.format("%s days ago at %s:%s%s", seconds/(24*60*60)+1, hour, minute, ampm);
            else{
                String monthName = new DateFormatSymbols().getMonths()[date.getMonth()];
                String daySuffix = "";
                if (date.getDate() >= 11 && date.getDate() <= 13) {
                    daySuffix = "th";
                }
                switch (date.getDate() % 10) {
                    case 1:  daySuffix = "st";
                    case 2:  daySuffix = "nd";
                    case 3:  daySuffix = "rd";
                    default: daySuffix = "th";
                }

                output = String.format("%s %s%s, %s at %s:%s%s", monthName, date.getDate(), daySuffix, date.getYear()+1900, hour, minute, ampm);
            }

            return "Posted " + output;
        } catch (ParseException e) {
            e.printStackTrace();
            return "";
        }
    }

    public void saveStringArray(String key, ArrayList<String> arr) {
        JSONArray jsArr = new JSONArray(arr);
        SharedPreferences.Editor settingsEditor = settings.edit();
        settingsEditor.putString(key, jsArr.toString());
        settingsEditor.commit();
    }

    public ArrayList<String> getStringArray(String key) {
        try {
            JSONArray jsArr = new JSONArray(settings.getString(key, MainActivity.default_shownNotifications));
            ArrayList<String> list = new ArrayList<String>();
            for (int i = 0; i < jsArr.length(); i++) {
                list.add(jsArr.getString(i));
            }
            return list;
        } catch (JSONException e) {
            e.printStackTrace();
            return new ArrayList<String>();
        }
    }
}
