package com.shivai.shivix;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Everything the website can ask the phone to do. Only the app's own page can reach this (external sites are opened in the browser). */
public class NativeBridge {
    private final Activity act;
    private static final Map<String, String> APPS = new HashMap<>();
    static {
        APPS.put("youtube", "com.google.android.youtube");
        APPS.put("maps", "com.google.android.apps.maps");
        APPS.put("google maps", "com.google.android.apps.maps");
        APPS.put("chrome", "com.android.chrome");
        APPS.put("gmail", "com.google.android.gm");
        APPS.put("whatsapp", "com.whatsapp");
        APPS.put("instagram", "com.instagram.android");
        APPS.put("facebook", "com.facebook.katana");
        APPS.put("telegram", "org.telegram.messenger");
        APPS.put("spotify", "com.spotify.music");
        APPS.put("netflix", "com.netflix.mediaclient");
        APPS.put("snapchat", "com.snapchat.android");
        APPS.put("twitter", "com.twitter.android");
        APPS.put("x", "com.twitter.android");
        APPS.put("amazon", "in.amazon.mShop.android.shopping");
        APPS.put("flipkart", "com.flipkart.android");
        APPS.put("paytm", "net.one97.paytm");
        APPS.put("phonepe", "com.phonepe.app");
        APPS.put("google pay", "com.google.android.apps.nbu.paisa.user");
        APPS.put("gpay", "com.google.android.apps.nbu.paisa.user");
        APPS.put("zoom", "us.zoom.videomeetings");
        APPS.put("meet", "com.google.android.apps.tachyon");
        APPS.put("drive", "com.google.android.apps.docs");
        APPS.put("photos", "com.google.android.apps.photos");
        APPS.put("play store", "com.android.vending");
        APPS.put("calculator", "com.google.android.calculator");
        APPS.put("clock", "com.google.android.deskclock");
        APPS.put("calendar", "com.google.android.calendar");
        APPS.put("contacts", "com.google.android.contacts");
        APPS.put("messages", "com.google.android.apps.messaging");
        APPS.put("phone", "com.google.android.dialer");
    }

    public NativeBridge(Activity activity) { this.act = activity; }

    private static String res(boolean ok, String message) {
        try { return new JSONObject().put("ok", ok).put("message", message).toString(); }
        catch (Exception e) { return "{\"ok\":false,\"message\":\"Error\"}"; }
    }
    private static String ok(String m) { return res(true, m); }
    private static String fail(String m) { return res(false, m); }

    @JavascriptInterface
    public String run(String kind, String json) {
        try {
            JSONObject a = new JSONObject(json == null || json.isEmpty() ? "{}" : json);
            switch (kind == null ? "" : kind) {
                case "call": return call(a);
                case "sms": return sms(a);
                case "whatsapp": return whatsapp(a);
                case "open_app": return openApp(a.optString("target"));
                case "alarm": return alarm(a);
                case "timer": return timer(a);
                case "maps": return maps(a);
                case "torch": return torch("on".equals(a.optString("value")));
                case "volume": return volume(a.optString("value"));
                case "media": return media(a.optString("value"));
                case "camera": return start(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), "Opened the camera.");
                case "gallery": return gallery();
                case "settings": return settings(a.optString("target"));
                case "browser": return browser(a.optString("target"));
                default: return fail("I do not know how to do that yet.");
            }
        } catch (Throwable t) {
            return fail("That did not work on this phone.");
        }
    }

    @JavascriptInterface
    public String scheduleReminders(String json) {
        try { ReminderReceiver.schedule(act, new JSONArray(json == null ? "[]" : json)); return ok("Scheduled."); }
        catch (Throwable t) { return fail("Could not schedule reminders."); }
    }

    // ---- helpers ----
    private String start(Intent i, String done) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { act.startActivity(i); return ok(done); }
        catch (ActivityNotFoundException e) { return fail("No app on this phone can do that."); }
    }

    private boolean installed(String pkg) {
        try { act.getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }

    /** Returns a phone number, "" when contacts permission is missing, or null when no contact matches. */
    private String resolveNumber(String target) {
        if (target == null || target.trim().isEmpty()) return null;
        String t = target.trim();
        if (t.matches("^\\+?[\\d\\s()\\-]{6,}$")) return t.replaceAll("[^\\d+]", "");
        if (act.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return "";
        String[] cols = {ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME};
        for (int pass = 0; pass < 2; pass++) {
            String sel = pass == 0 ? ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " = ? COLLATE NOCASE"
                                   : ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
            String arg = pass == 0 ? t : "%" + t + "%";
            try (Cursor c = act.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, cols, sel, new String[]{arg}, null)) {
                if (c != null && c.moveToFirst()) return c.getString(0).replaceAll("[^\\d+]", "");
            } catch (Exception ignored) { }
        }
        return null;
    }

    private String contactProblem(String num, String target) {
        if (num == null) return "I could not find a contact called " + target + ". Check the name or tell me the number.";
        return "Allow contacts for this app (Settings > Apps > SHIVIX > Permissions), or tell me the number.";
    }

    // ---- actions ----
    private String call(JSONObject a) {
        String target = a.optString("target");
        if (target.isEmpty()) return start(new Intent(Intent.ACTION_DIAL), "Opened the dialer.");
        String num = resolveNumber(target);
        if (num == null || num.isEmpty()) return fail(contactProblem(num, target));
        return start(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num))), "Opened the dialer for " + target + ". Press call yourself.");
    }

    private String sms(JSONObject a) {
        String target = a.optString("target");
        String num = "";
        if (!target.isEmpty()) {
            num = resolveNumber(target);
            if (num == null || num.isEmpty()) return fail(contactProblem(num, target));
        }
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(num)));
        String text = a.optString("text");
        if (!text.isEmpty()) i.putExtra("sms_body", text);
        return start(i, "Opened your messages" + (target.isEmpty() ? "" : " to " + target) + ". Press send yourself.");
    }

    private String whatsapp(JSONObject a) {
        String target = a.optString("target"), text = a.optString("text");
        String digits = "";
        if (!target.isEmpty()) {
            String num = resolveNumber(target);
            if (num == null || num.isEmpty()) return fail(contactProblem(num, target));
            digits = num.replace("+", "");
            if (digits.length() == 10 && "IN".equals(Locale.getDefault().getCountry())) digits = "91" + digits;
        }
        String url = "https://wa.me/" + digits + (text.isEmpty() ? "" : "?text=" + Uri.encode(text));
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (installed("com.whatsapp")) i.setPackage("com.whatsapp");
        else if (installed("com.whatsapp.w4b")) i.setPackage("com.whatsapp.w4b");
        return start(i, "Opened WhatsApp" + (target.isEmpty() ? "" : " for " + target) + ". Press send yourself.");
    }

    private String openApp(String name) {
        if (name == null || name.trim().isEmpty()) return fail("Which app?");
        String n = name.trim().toLowerCase(Locale.ROOT);
        PackageManager pm = act.getPackageManager();
        String pkg = APPS.get(n);
        Intent launch = pkg == null ? null : pm.getLaunchIntentForPackage(pkg);
        if (launch == null) {
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
            ResolveInfo best = null; int score = 0;
            for (ResolveInfo r : list) {
                String label = String.valueOf(r.loadLabel(pm)).toLowerCase(Locale.ROOT);
                int s = label.equals(n) ? 3 : label.startsWith(n) ? 2 : label.contains(n) ? 1 : 0;
                if (s > score) { score = s; best = r; }
            }
            if (best != null) launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
        }
        if (launch == null) return fail("I could not find an app called " + name + " on this phone.");
        return start(launch, "Opened " + name + ".");
    }

    private String alarm(JSONObject a) {
        int h = a.optInt("hour", -1), m = a.optInt("minute", 0);
        if (h < 0 || h > 23 || m < 0 || m > 59) return fail("That alarm time is not valid.");
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m).putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        String label = a.optString("text");
        if (!label.isEmpty()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label);
        return start(i, String.format(Locale.US, "Opened your clock to set the alarm for %02d:%02d. Confirm it there.", h, m));
    }

    private String timer(JSONObject a) {
        int s = a.optInt("seconds", 0);
        if (s < 1) return fail("That timer length is not valid.");
        Intent i = new Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, s).putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        String label = a.optString("text");
        if (!label.isEmpty()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label);
        return start(i, "Opened your clock to start the timer. Confirm it there.");
    }

    private String maps(JSONObject a) {
        String q = a.optString("target");
        if (q.isEmpty()) return fail("Which place?");
        boolean nav = a.optBoolean("navigate", false);
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse((nav ? "google.navigation:q=" : "geo:0,0?q=") + Uri.encode(q)));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { act.startActivity(i); return ok(nav ? "Starting directions to " + q + "." : "Showing " + q + " on the map."); }
        catch (ActivityNotFoundException e) {
            String url = nav ? "https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(q) : "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q);
            return start(new Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Opened maps in the browser.");
        }
    }

    private String torch(boolean on) {
        try {
            CameraManager cm = (CameraManager) act.getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                Boolean flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                if (flash != null && flash && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cm.setTorchMode(id, on);
                    return ok(on ? "Torch on." : "Torch off.");
                }
            }
            return fail("This phone has no torch I can use.");
        } catch (Throwable t) { return fail("I could not switch the torch."); }
    }

    private String volume(String v) {
        AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
        int s = AudioManager.STREAM_MUSIC, max = am.getStreamMaxVolume(s);
        try {
            switch (v) {
                case "up": am.adjustStreamVolume(s, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); return ok("Volume up.");
                case "down": am.adjustStreamVolume(s, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); return ok("Volume down.");
                case "mute": am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI); return ok("Muted.");
                case "unmute": am.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI); return ok("Unmuted.");
                case "max": am.setStreamVolume(s, max, AudioManager.FLAG_SHOW_UI); return ok("Volume at maximum.");
                default: {
                    int pct = Math.max(0, Math.min(100, Integer.parseInt(v.trim())));
                    am.setStreamVolume(s, Math.round(max * pct / 100f), AudioManager.FLAG_SHOW_UI);
                    return ok("Volume set to " + pct + "%.");
                }
            }
        } catch (Throwable t) { return fail("I could not change the volume."); }
    }

    private String media(String cmd) {
        int code;
        switch (cmd) {
            case "play": code = KeyEvent.KEYCODE_MEDIA_PLAY; break;
            case "pause": code = KeyEvent.KEYCODE_MEDIA_PAUSE; break;
            case "toggle": code = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE; break;
            case "next": code = KeyEvent.KEYCODE_MEDIA_NEXT; break;
            case "previous": code = KeyEvent.KEYCODE_MEDIA_PREVIOUS; break;
            case "stop": code = KeyEvent.KEYCODE_MEDIA_STOP; break;
            default: return fail("I do not know that music command.");
        }
        try {
            AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
            return ok("Done.");
        } catch (Throwable t) { return fail("I could not control the music."); }
    }

    private String gallery() {
        Intent i = new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { act.startActivity(i); return ok("Opened your gallery."); }
        catch (ActivityNotFoundException e) { return start(new Intent(Intent.ACTION_PICK).setType("image/*"), "Opened your photos."); }
    }

    private String settings(String page) {
        String action;
        switch (page == null ? "" : page) {
            case "wifi": action = Settings.ACTION_WIFI_SETTINGS; break;
            case "bluetooth": action = Settings.ACTION_BLUETOOTH_SETTINGS; break;
            case "display": action = Settings.ACTION_DISPLAY_SETTINGS; break;
            case "sound": action = Settings.ACTION_SOUND_SETTINGS; break;
            case "battery": action = Settings.ACTION_BATTERY_SAVER_SETTINGS; break;
            case "location": action = Settings.ACTION_LOCATION_SOURCE_SETTINGS; break;
            case "apps": action = Settings.ACTION_APPLICATION_SETTINGS; break;
            case "airplane": action = Settings.ACTION_AIRPLANE_MODE_SETTINGS; break;
            default: action = Settings.ACTION_SETTINGS;
        }
        return start(new Intent(action), "Opened settings.");
    }

    private String browser(String url) {
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return fail("Only web addresses can be opened.");
        return start(new Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Opened in your browser.");
    }
}
