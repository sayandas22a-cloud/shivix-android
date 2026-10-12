# SHIVIX Android app: how to build the APK

This folder is a normal Android Studio project. It needs no Node, no Capacitor and no extra libraries.

## Build it (about 15 minutes the first time)
1. Install **Android Studio** (free): https://developer.android.com/studio
2. Open Android Studio, choose **Open**, and pick this folder (the one that contains `settings.gradle`).
3. Wait while it downloads things ("Gradle sync", needs internet, 5 to 10 minutes). Accept any "install SDK / update plugin" offers. If it offers to upgrade the Android Gradle Plugin, say yes.
4. Menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. When it finishes, click **locate** in the pop-up. The file is `app/build/outputs/apk/debug/app-debug.apk`.
6. Send that file to your phone and open it. Android will ask you to allow installing from this source: allow it. (Or plug the phone in with USB debugging on and press the green Run button.)

## Two modes (one line to change)
Open `gradle.properties` and look at the last line, `START_URL`:
- `START_URL=file:///android_asset/preview.html` (default, **test mode**). The app shows the offline demo page that is bundled inside it. The AI replies are scripted, but **the phone actions are real**: say "call Mom", "set an alarm for 7:30", "turn on the torch", "open the YouTube app". Use this to check every action on your phone before you deploy the website.
- `START_URL=https://shiv-ai-creation.netlify.app` (**real mode**). After you deploy the finished website, put your address here and build again. The app then shows the real SHIVIX with the real AI and login. The website updates itself; you only rebuild the APK when you change this app.

## What the app asks for, and why
- **Microphone**: voice conversations. **Contacts**: so "call Mom" finds Mom's number. **Notifications**: reminders.
- Calls, texts and WhatsApp are only **prepared**: the dialer or message screen opens and you press call or send yourself. Alarms and timers open your clock app for you to confirm.
- Nothing is sent or called without you tapping.

## Things it cannot do yet
- Listening for a wake word while the app is closed (this needs a background service; planned as a later step).
- Reading the screen or controlling other apps' insides.

## If the build fails
- "Unsupported Java / JDK": File > Settings > Build Tools > Gradle > Gradle JDK, choose the one that comes with Android Studio (JDK 17 or newer).
- "SDK not found / license": in Android Studio open Tools > SDK Manager and install "Android 14 (API 34)", then accept the licences.
- "Could not resolve ... plugin": you are offline or a VPN blocks Google. Connect normally and press **Sync Project with Gradle Files** (the elephant icon).
- Anything else: copy the red error text from the Build tab and send it to me.
