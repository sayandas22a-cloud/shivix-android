package com.shivai.shivix;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Shows a reminder notification at the right time, even when the app is closed. */
public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "reminders";
    private static final String PREFS = "reminders";
    private static final String KEY_IDS = "ids";

    @Override
    public void onReceive(Context context, Intent intent) {
        String text = intent.getStringExtra("text");
        if (text == null) text = "Reminder";
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(context, CHANNEL) : new android.app.Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("Reminder").setContentText(text).setContentIntent(pi).setAutoCancel(true);
        nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), b.build());
    }

    private static PendingIntent pending(Context c, String id, String text) {
        Intent i = new Intent(c, ReminderReceiver.class).putExtra("text", text);
        return PendingIntent.getBroadcast(c, Math.abs(id.hashCode()), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Replaces everything scheduled before with the given reminders: [{id, text, when(ms)}]. */
    public static void schedule(Context c, JSONArray items) throws Exception {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (String id : sp.getStringSet(KEY_IDS, new HashSet<String>())) am.cancel(pending(c, id, ""));
        Set<String> ids = new HashSet<>();
        long now = System.currentTimeMillis();
        for (int k = 0; k < items.length(); k++) {
            JSONObject o = items.getJSONObject(k);
            long when = o.getLong("when");
            if (when <= now) continue;
            String id = o.getString("id");
            PendingIntent pi = pending(c, id, o.optString("text", "Reminder"));
            // setAlarmClock fires exactly on time and needs no special permission.
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE)), pi);
            ids.add(id);
        }
        sp.edit().putStringSet(KEY_IDS, ids).apply();
    }
}
