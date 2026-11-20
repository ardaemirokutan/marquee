package com.arda.marquee;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewPropertyAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Full-screen home: the focused app's colour fills the screen, banners in one row, focused banner glows in its own colour. */
public class Home extends Activity {
    static final List<String> ORDER = Arrays.asList("com.netflix.ninja", "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "com.exxen.android", "com.turkcell.ott",
            "com.digiturk.iq.mobil", "com.wbd.stream", "com.apple.atve.androidtv.appletv");
    static final Map<String, String> NAMES = new HashMap<>();
    static final Map<String, Integer> DESCS = new HashMap<>();
    static void info(String pkg, String name, int desc) { NAMES.put(pkg, name); DESCS.put(pkg, desc); }
    static {
        info("com.netflix.ninja", "Netflix", R.string.desc_netflix);
        info("com.google.android.youtube.tv", "YouTube", R.string.desc_youtube);
        info("com.amazon.amazonvideo.livingroom", "Prime Video", R.string.desc_prime);
        info("com.disney.disneyplus", "Disney+", R.string.desc_disney);
        info("com.exxen.android", "Exxen", R.string.desc_exxen);
        info("com.turkcell.ott", "TV+", R.string.desc_tvplus);
        info("com.digiturk.iq.mobil", "beIN Connect", R.string.desc_bein);
        info("com.wbd.stream", "HBO Max", R.string.desc_hbo);
        info("com.apple.atve.androidtv.appletv", "Apple TV", R.string.desc_appletv);
    }
    // Google TV lacks the standard Display action and blocks its Bluetooth screen to other apps; these are the screens it allows.
    static final String[] SYS = {Settings.ACTION_SETTINGS, Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_SOUND_SETTINGS, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS};
    static final int[] SYS_NAMES = {R.string.pill_settings, R.string.pill_wifi, R.string.pill_display, R.string.pill_apps};
    static final int GREY = 0xFF8E8E93;
    static final int RIBBONS = 7;

    /** One installed app. Banner and colour are loaded once per installed version (src) and kept in cache. */
    static class App {
        String pkg, name, line, src; Intent launch; Drawable banner; int color = GREY; boolean colored, pending;
        GradientDrawable glow, sweep; // the current row item's drawables, repainted when the colour arrives
    }

    // Named on purpose: d8 34.0.0 crashes on javac 21's metadata for anonymous classes.
    static class Round extends ViewOutlineProvider {
        final float r;
        Round(float r) { this.r = r; }
        @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
    }

    View shade;
    float u; // 1% of screen width, so the layout matches the 16:9 preview at any resolution
    float k = 1; // animation speed from settings: 1, 0.5 or 0 (instant)
    static final Map<Integer, Typeface> FONTS = new HashMap<>();
    /** One Typeface per weight, shared by every screen: a variation set per TextView builds a new font each time and misses the glyph cache. */
    static Typeface font(android.content.Context c, int weight) {
        Typeface t = FONTS.get(weight);
        if (t == null) { t = new Typeface.Builder(c.getAssets(), "Inter.ttf").setFontVariationSettings("'wght' " + weight).build(); FONTS.put(weight, t); }
        return t;
    }
    android.graphics.drawable.BitmapDrawable fill; // the darkening gradients as grey levels, multiplied by the app colour: one opaque full-screen layer
    int bgColor = Color.BLACK, bgFrom, bgTo;
    final ValueAnimator bgAnim = ValueAnimator.ofFloat(0f, 1f);
    TextView name, line, eye;
    FrameLayout root, menu; // menu: the long-press overlay while it is open
    LinearLayout launchFx; // the launch ribbons, built once; hidden when Home stops or comes back
    LinearLayout top, clock, hero, row;
    HorizontalScrollView scroll;
    TextClock time;
    final List<App> apps = new ArrayList<>();
    final List<View> items = new ArrayList<>();
    final Map<String, App> cache = new HashMap<>();
    App cur; // the focused app
    TextView who, wx, saverWx;
    FrameLayout saver;
    LinearLayout saverBox;
    TextClock saverTime, saverDate;
    final Handler tickH = new Handler(Looper.getMainLooper());
    long lastKey = SystemClock.uptimeMillis(), wAt;
    String wKey = "", wText = "", tf = "HH:mm";
    boolean eatUp, launching;
    String fx = "glow";
    boolean colorBg = true, launchOn = true, sweepOn = true;
    boolean intro = true; // entrance animation plays once per onCreate, not on every resume
    int lastFocus = 0;
    final Intent prefsIntent = new Intent();
    final DecelerateInterpolator ease = new DecelerateInterpolator(2.2f), soft = new DecelerateInterpolator(2.5f);
    final AccelerateDecelerateInterpolator glide = new AccelerateDecelerateInterpolator();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        u = getResources().getDisplayMetrics().widthPixels / 100f;
        getWindow().setBackgroundDrawable(null); // the back view covers the screen; saves a full-screen fill per frame
        root = new FrameLayout(this);

        // the three darkening gradients baked once over white into a small bitmap; colour x grey = the colour darkened the same way
        shade = new View(this);
        Bitmap sb = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888);
        sb.eraseColor(Color.WHITE);
        Canvas sc = new Canvas(sb);
        GradientDrawable[] gs = {
            new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xC7000000, 0x33000000}), // 78% top-left to 20% bottom-right
            new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xE6000000, 0x59000000, 0x00000000}),
            new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x99000000, 0x00000000, 0x00000000})};
        for (GradientDrawable g : gs) { g.setBounds(0, 0, 480, 270); g.draw(sc); }
        sb.setHasAlpha(false);
        fill = new android.graphics.drawable.BitmapDrawable(getResources(), sb);
        fill.setFilterBitmap(true);
        fill.setTintMode(android.graphics.PorterDuff.Mode.MULTIPLY);
        fill.setTint(Color.BLACK);
        shade.setBackground(fill);
        root.addView(shade, match());
        // one animator for every colour change: only sets a colour per frame, allocates nothing
        bgAnim.addUpdateListener(v -> { bgColor = mix(bgFrom, bgTo, v.getAnimatedFraction()); fill.setTint(bgColor); });

        top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setClipChildren(false);
        who = text(1.6f, 600, 0xFFF5F5F7, -0.01f);
        top.addView(who);
        LinearLayout pills = new LinearLayout(this);
        pills.setClipChildren(false);
        for (int i = 0; i < SYS.length; i++) pills.addView(pill(SYS_NAMES[i], new Intent(SYS[i]), i == 0));
        prefsIntent.setClass(this, Prefs.class);
        pills.addView(pill(R.string.pill_home, prefsIntent, false));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.topMargin = (int) (1.1f * u);
        top.addView(pills, pp);
        root.addView(top, at(Gravity.TOP | Gravity.START, 5, 3f));

        clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.END);
        time = new TextClock(this);
        style(time, 2.8f, 500, 0xFFF5F5F7, -0.02f);
        TextClock date = new TextClock(this);
        style(date, 1.2f, 400, 0xFFA1A1A6, 0.01f);
        date.setFormat24Hour("EEEE, d MMMM"); date.setFormat12Hour("EEEE, d MMMM");
        wx = text(1.2f, 500, 0xFFF5F5F7, 0.01f);
        wx.setVisibility(View.GONE);
        wx.setPadding((int) (1.1f * u), 0, 0, 0);
        LinearLayout sub = new LinearLayout(this);
        sub.setGravity(Gravity.CENTER_VERTICAL);
        sub.addView(date); sub.addView(wx);
        clock.addView(time);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = (int) (0.5f * u);
        clock.addView(sub, sl);
        root.addView(clock, at(Gravity.TOP | Gravity.END, 4.5f, 3.2f));

        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        eye = text(1.1f, 600, 0xB3FFFFFF, 0.08f);
        eye.setText(R.string.eye_app);
        name = text(6.4f, 700, 0xFFFFFFFF, -0.035f);
        line = text(1.55f, 400, 0xC7FFFFFF, 0.005f);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // full width, not wrap: a new name of the same height then only redraws, with no layout pass over the screen
        hero.addView(eye, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = (int) (0.6f * u);
        hero.addView(name, np);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (1f * u);
        hero.addView(line, lp);
        FrameLayout.LayoutParams hp = at(Gravity.TOP | Gravity.START, 5, 15.5f);
        hp.width = (int) (60 * u);
        root.addView(hero, hp);

        scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.setPadding((int) (5.5f * u), (int) (4 * u), (int) (5.5f * u), (int) (5 * u)); // room for the glow, which the scroll view clips
        row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        scroll.addView(row);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        root.setClipChildren(false);
        // these fade as a whole (launch, intro): alpha per child, not through a full offscreen copy each frame
        for (View v : new View[]{top, clock, hero, scroll}) v.forceHasOverlappingRendering(false);

        // screensaver: black, big clock + date + weather, one short drift a minute against burn-in
        saver = new FrameLayout(this);
        saver.setBackgroundColor(Color.BLACK);
        saver.setClickable(true);
        saver.setVisibility(View.GONE);
        saver.forceHasOverlappingRendering(false); // fade without a full-screen offscreen buffer
        saverBox = new LinearLayout(this);
        saverBox.setOrientation(LinearLayout.VERTICAL);
        saverBox.setGravity(Gravity.CENTER_HORIZONTAL);
        saverTime = new TextClock(this);
        style(saverTime, 12f, 300, 0xFFF5F5F7, -0.03f);
        saverDate = new TextClock(this);
        style(saverDate, 2f, 400, 0xFFA1A1A6, 0.01f);
        saverDate.setFormat24Hour("EEEE, d MMMM"); saverDate.setFormat12Hour("EEEE, d MMMM");
        saverWx = text(2.4f, 400, 0xFFA1A1A6, 0);
        saverWx.setPadding(0, (int) (1.2f * u), 0, 0);
        saverBox.addView(saverTime); saverBox.addView(saverDate); saverBox.addView(saverWx);
        saver.addView(saverBox, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(saver, match());
        launchFx = new LinearLayout(this);
        launchFx.setVisibility(View.GONE);
        for (int i = 0; i < RIBBONS; i++) {
            View r = new View(this);
            r.setScaleY(0f);
            launchFx.addView(r, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        root.addView(launchFx, match());
        setContentView(root);
        // an app installed, removed or updated: forget the row so the next load() rebuilds it
        android.content.IntentFilter pf = new android.content.IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED); pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED); pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        registerReceiver(pkgWatch, pf);
    }

    // named static class: d8 34.0.0 crashes on anonymous classes built by javac 21
    static class PkgWatch extends android.content.BroadcastReceiver {
        final Home h;
        PkgWatch(Home h) { this.h = h; }
        @Override public void onReceive(android.content.Context c, Intent i) {
            h.lastSig = "";
            if (h.hasWindowFocus()) h.load(); // installed while Home is on screen: show it now
        }
    }
    final PkgWatch pkgWatch = new PkgWatch(this);

    @Override protected void onDestroy() {
        unregisterReceiver(pkgWatch);
        super.onDestroy();
    }

    long d(long ms) { return (long) (ms * k); }

    @Override protected void onResume() {
        super.onResume();
        clearLaunch();
        lastKey = SystemClock.uptimeMillis();
        load(); // picks up anything changed in Prefs
        tick();
        checkSaver();
    }

    @Override protected void onPause() {
        super.onPause();
        tickH.removeCallbacksAndMessages(null);
        if (introPending) { introPending = false; for (View v : introViews()) v.setAlpha(1f); } // left before it ran: just show
        saver.animate().cancel();
        saver.setVisibility(View.GONE);
        homeShown(true);
    }

    @Override protected void onStop() {
        super.onStop();
        clearLaunch(); // the app covers Home now; returning shows the plain row
    }

    /** Once a minute, on the minute, while Home is in front: greeting, weather refresh (30 min), screensaver drift. */
    final Runnable tickRun = this::tick;
    void tick() {
        tickH.removeCallbacks(tickRun);
        updateWho();
        refreshWeather();
        if (saver.getVisibility() == View.VISIBLE) drift(true);
        tickH.postDelayed(tickRun, 60000 - System.currentTimeMillis() % 60000 + 50);
    }

    /** Sleeps until the screensaver is due; a key press in between only moves lastKey, and this re-arms for the rest. */
    final Runnable saverRun = this::checkSaver;
    void checkSaver() {
        tickH.removeCallbacks(saverRun);
        int min = Prefs.saver(this);
        if (min == 0) return;
        long left = lastKey + min * 60000L - SystemClock.uptimeMillis();
        if (left > 0) tickH.postDelayed(saverRun, left);
        else if (saver.getVisibility() != View.VISIBLE) showSaver();
    }

    void updateWho() {
        String s = Prefs.person(this);
        if (Prefs.greet(this)) {
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            int g = h >= 5 && h < 12 ? R.string.greet_morning : h >= 12 && h < 18 ? R.string.greet_afternoon : h >= 18 && h < 22 ? R.string.greet_evening : R.string.greet_night;
            s = getString(g) + ", " + s;
        }
        if (!s.contentEquals(who.getText())) who.setText(s); // same text would still cost a layout pass
    }

    /** Fetches on a background thread when the location changed or the data is over 30 min old; any failure hides the weather. */
    void refreshWeather() {
        String name = Prefs.place(this);
        if (name.isEmpty()) { wText = ""; wKey = ""; showWeather(); return; }
        String lat = Prefs.p(this).getString("wLat", ""), lon = Prefs.p(this).getString("wLon", "");
        String key = lat + "," + lon;
        if (!key.equals(wKey)) { wKey = key; wText = ""; wAt = 0; }
        showWeather();
        if (wAt != 0 && SystemClock.uptimeMillis() - wAt < 30 * 60000L) return;
        wAt = SystemClock.uptimeMillis(); // also spaces out retries after a failure
        new Thread(() -> {
            String r;
            try { r = Weather.now(lat, lon); } catch (Exception e) { r = ""; }
            String res = r;
            runOnUiThread(() -> { if (key.equals(wKey)) { wText = res; showWeather(); } });
        }).start();
    }

    void showWeather() {
        if (wText.contentEquals(wx.getText())) return;
        int vis = wText.isEmpty() ? View.GONE : View.VISIBLE;
        wx.setText(wText); wx.setVisibility(vis);
        saverWx.setText(wText); saverWx.setVisibility(vis);
    }

    void showSaver() {
        saverTime.setFormat24Hour(tf); saverTime.setFormat12Hour(tf);
        drift(false);
        saver.setAlpha(0f);
        saver.setVisibility(View.VISIBLE);
        saver.animate().alpha(1f).setDuration(d(1500)).withEndAction(() -> homeShown(false)).start();
    }

    /** Not drawn while the screensaver covers it, so its drift redraws only the black screen. Alpha, not visibility: the row keeps focus. */
    void homeShown(boolean on) {
        for (View x : new View[]{shade, top, clock, hero, scroll}) x.setAlpha(on ? 1f : 0f);
    }

    /** A new spot each minute, within the middle of the screen. */
    void drift(boolean animate) {
        int m = Calendar.getInstance().get(Calendar.MINUTE);
        saverBox.animate().translationX(((m * 37) % 21 - 10) * u).translationY(((m * 53) % 13 - 6) * u).setDuration(animate ? d(1200) : 0).setInterpolator(glide).start();
    }

    /** Any key wakes the screensaver and is swallowed; keys are also swallowed while an app is opening. */
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        lastKey = SystemClock.uptimeMillis();
        if (e.getAction() == KeyEvent.ACTION_UP && eatUp) { eatUp = false; return true; }
        if (launching) return true;
        if (saver.getVisibility() == View.VISIBLE) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                eatUp = true;
                saver.animate().cancel();
                homeShown(true);
                saver.animate().alpha(0f).setDuration(d(300)).withEndAction(() -> saver.setVisibility(View.GONE)).start();
                checkSaver();
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    String lastSig = "";

    /** User logos; only on Android 11+, where no other app can write into this app's external folder. */
    java.io.File logoDir;
    java.io.File logoDir() {
        if (logoDir == null && android.os.Build.VERSION.SDK_INT >= 30) logoDir = getExternalFilesDir("banners"); // disk + binder work: once
        return logoDir;
    }

    /** A user logo decoded at banner size at most; anything odd (too big, broken, missing) falls back to the app's banner. */
    Drawable ownLogo(java.io.File f) {
        if (f == null || !f.isFile() || f.length() > 4_000_000) return null;
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int n = 1;
            while (o.outWidth / (n * 2) >= 640 && o.outHeight / (n * 2) >= 360) n *= 2; // keep at least 640x360
            o = new android.graphics.BitmapFactory.Options();
            o.inSampleSize = n;
            Bitmap b = android.graphics.BitmapFactory.decodeFile(f.getPath(), o);
            return b != null ? new android.graphics.drawable.BitmapDrawable(getResources(), b) : null;
        } catch (Throwable t) { // includes OutOfMemoryError: a bad file must never take the home screen down
            return null;
        }
    }

    void load() {
        // Home resumes several times per Home press; only rebuild when a setting changed, so focus isn't yanked away.
        Map<String, Object> all = new HashMap<>(Prefs.p(this).getAll());
        if (!Prefs.lastFirst(this)) all.remove("last"); // opening an app only rebuilds the row when that reorders it
        java.io.File logos = logoDir();
        String sig = all.toString() + (logos != null ? logos.lastModified() : 0); // a logo added or removed
        if (sig.equals(lastSig) && !apps.isEmpty()) return;
        lastSig = sig;
        fx = Prefs.fx(this);
        colorBg = Prefs.colorBg(this);
        k = Prefs.speed(this);
        launchOn = Prefs.launchFx(this);
        sweepOn = Prefs.sweep(this);
        updateWho();
        tf = Prefs.clock24(this) ? "HH:mm" : "h:mm a"; // set both, so the setting wins over the system's 24-hour switch
        time.setFormat24Hour(tf); time.setFormat12Hour(tf);
        line.setVisibility(Prefs.desc(this) ? View.VISIBLE : View.GONE);
        if (!colorBg) { bgAnim.cancel(); bgColor = 0xFF0B0B0D; fill.setTint(bgColor); }
        row.removeAllViews(); items.clear(); apps.clear();
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<App> need = new ArrayList<>();
        for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || pkg.equals("com.google.android.videos")) continue; // Play Movies is retired
            // a user-supplied logo (files/banners/<package>.png) wins over the app's own banner
            java.io.File own = logos != null ? new java.io.File(logos, pkg + ".png") : null;
            String src = r.activityInfo.applicationInfo.sourceDir + (own != null ? own.lastModified() : 0); // changes when the app or the logo is updated
            App a = cache.get(pkg);
            if (a == null || !src.equals(a.src)) {
                Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (launch == null) continue;
                a = new App();
                a.pkg = pkg; a.src = src; a.launch = launch;
                String known = NAMES.get(pkg);
                a.name = known != null ? known : r.loadLabel(pm).toString();
                a.line = known != null ? getString(DESCS.get(pkg)) : "";
                Drawable d = ownLogo(own);
                if (d == null) d = r.activityInfo.loadBanner(pm);
                if (d == null) d = r.activityInfo.applicationInfo.loadBanner(pm);
                a.banner = small(d != null ? d : r.loadIcon(pm));
                if (a.banner == null) a.colored = true; // grey
                else if (a.banner.getConstantState() == null) { a.color = mainColor(a.banner); a.colored = true; } // can't copy it for a background thread; before its view sets its bounds
                cache.put(pkg, a);
            }
            if (apps.contains(a)) continue; // an app with two launcher activities
            if (!a.colored && !a.pending) need.add(a);
            apps.add(a);
        }
        List<String> saved = Prefs.order(this);
        List<String> order = new ArrayList<>(saved.isEmpty() ? ORDER : saved);
        String last = Prefs.last(this);
        if (Prefs.lastFirst(this) && !last.isEmpty()) { order.remove(last); order.add(0, last); }
        apps.sort((a, c) -> {
            int ia = order.indexOf(a.pkg), ic = order.indexOf(c.pkg);
            if (ia < 0) ia = 999; if (ic < 0) ic = 999;
            return ia != ic ? ia - ic : a.name.compareToIgnoreCase(c.name);
        });
        ArrayList<String> pkgs = new ArrayList<>(), names = new ArrayList<>();
        Set<String> hidden = Prefs.hidden(this);
        for (App a : apps) {
            pkgs.add(a.pkg); names.add(a.name);
            if (!hidden.contains(a.pkg)) row.addView(item(a));
        }
        prefsIntent.putStringArrayListExtra("pkgs", pkgs).putStringArrayListExtra("names", names);
        if (!items.isEmpty()) items.get(Math.min(lastFocus, items.size() - 1)).requestFocus();
        if (intro) {
            intro = false;
            if (k > 0) { // wait, hidden, for the system's window transition: running both at once halved the frame rate
                introPending = true;
                for (View v : introViews()) v.setAlpha(0f);
                tickH.postDelayed(introRun, 1200); // in case the window comes up without a transition
            }
        }
        colors(need);
    }

    /** Banner colours for apps seen for the first time, worked out on a background thread from copies of the banners. */
    void colors(List<App> need) {
        if (need.isEmpty()) return;
        List<Drawable> copies = new ArrayList<>();
        for (App a : need) { a.pending = true; copies.add(a.banner.getConstantState().newDrawable().mutate()); }
        new Thread(() -> {
            Bitmap bm = Bitmap.createBitmap(48, 27, Bitmap.Config.ARGB_8888);
            int[] px = new int[48 * 27], out = new int[need.size()];
            for (int i = 0; i < out.length; i++) {
                try { out[i] = mainColor(copies.get(i), bm, px); } catch (Exception e) { out[i] = GREY; }
            }
            runOnUiThread(() -> {
                for (int i = 0; i < out.length; i++) {
                    App a = need.get(i);
                    a.color = out[i]; a.colored = true; a.pending = false;
                    paint(a);
                    if (a == cur) tint(a);
                }
            });
        }).start();
    }

    /** Glow and light sweep in the app's colour. */
    void paint(App a) {
        if (a.glow != null) a.glow.setColors(new int[]{(a.color & 0xFFFFFF) | 0xB0000000, a.color & 0xFFFFFF});
        if (a.sweep != null) { int l = mix(a.color, Color.WHITE, 0.65f) & 0xFFFFFF; a.sweep.setColors(new int[]{l, l | 0x59000000, l}); }
    }

    /** The banner pre-cropped to the tile at its focused size: the GPU then never shrinks a big banner image on every frame. */
    Drawable small(Drawable d) {
        if (d == null) return null;
        int w = Math.round(7.2f * u * 1.2f), h = Math.round(w * 10f / 16f);
        int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            if (iw > 0 && ih > 0) { // centre crop, like the ImageView did
                float s = Math.max(w / (float) iw, h / (float) ih);
                int dw = Math.round(iw * s), dh = Math.round(ih * s);
                d.setBounds((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2);
            } else d.setBounds(0, 0, w, h);
            d.draw(new Canvas(b));
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Throwable t) { return d; } // keep the original if it can't be drawn here
    }

    boolean introPending;
    List<View> introViews() { List<View> l = new ArrayList<>(Arrays.asList(top, clock, hero)); l.addAll(items); return l; }
    final Runnable introRun = this::runIntro;
    void runIntro() {
        tickH.removeCallbacks(introRun);
        if (!introPending) return;
        introPending = false;
        rise(top, -1.5f, 0); rise(clock, -1.5f, 0); rise(hero, 2f, 100);
        for (int i = 0; i < items.size(); i++) rise(items.get(i), 3f, 200 + i * 40L);
    }

    @Override public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        runIntro();
    }

    void rise(View v, float from, long delay) {
        if (k == 0) return;
        v.forceHasOverlappingRendering(false); // fade each child on its own instead of through an offscreen buffer
        v.setAlpha(0f);
        v.setTranslationY(from * u);
        v.animate().alpha(1f).translationY(0).setStartDelay(d(delay)).setDuration(d(700)).setInterpolator(soft).start();
    }

    View item(App a) {
        int w = (int) (7.2f * u), h = (int) (w * 10f / 16f), gap = (int) (1.6f * u); // 10 apps fit in one row without scrolling
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClipChildren(false);
        FrameLayout tile = new FrameLayout(this);
        tile.setClipChildren(false);
        View glow = new View(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        gd.setGradientRadius(h * 1.25f); // must fit inside the glow's own box, or its edge shows as a straight cut
        gd.setGradientCenter(0.5f, 0.5f);
        glow.setBackground(gd);
        glow.setAlpha(0f);
        glow.forceHasOverlappingRendering(false);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams((int) (w * 1.7f), (int) (h * 2.6f), Gravity.CENTER);
        gp.leftMargin = gp.rightMargin = -(int) (w * 0.35f);
        gp.topMargin = gp.bottomMargin = -(int) (h * 0.8f);
        tile.addView(glow, gp);
        // card: the rounded banner plus the light sweep, clipped together
        FrameLayout card = new FrameLayout(this);
        card.setOutlineProvider(new Round(1.4f * u));
        card.setClipToOutline(true);
        card.forceHasOverlappingRendering(false);
        View face;
        if (a.banner != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(a.banner);
            face = iv;
        } else {
            TextView tv = text(1.3f, 600, 0xFFFFFFFF, 0);
            tv.setText(a.name);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundColor(0xFF2C2C2E);
            face = tv;
        }
        card.addView(face, match());
        View sweep = new View(this);
        GradientDrawable sd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0, 0, 0});
        sweep.setBackground(sd);
        sweep.setRotation(16);
        sweep.setVisibility(View.GONE);
        int sw = (int) (w * 0.5f);
        card.addView(sweep, new FrameLayout.LayoutParams(sw, h * 2, Gravity.CENTER_VERTICAL | Gravity.START));
        a.glow = gd; a.sweep = sd;
        paint(a);
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(1.4f * u);
        ring.setStroke((int) (0.25f * u), Color.WHITE);
        card.setAlpha(fx.equals("glow") ? 0.6f : 1f);
        tile.addView(card, new FrameLayout.LayoutParams(w, h, Gravity.CENTER));
        box.addView(tile, new LinearLayout.LayoutParams(w, h));
        TextView lab = text(1.35f, 500, 0xFFF5F5F7, 0);
        lab.setText(a.name);
        lab.setAlpha(0f);
        lab.setSingleLine(true);
        lab.setGravity(Gravity.CENTER_HORIZONTAL);
        // wider than the tile so long names don't widen the box and break the even spacing
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w * 2, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = lp.rightMargin = -w / 2;
        lp.topMargin = (int) (1.4f * u);
        box.addView(lab, lp);
        box.setFocusable(true);
        box.setOnFocusChangeListener((v, has) -> {
            float s = fx.equals("lift") ? 1.18f : fx.equals("ring") ? 1.06f : 1.12f;
            tile.animate().scaleX(has ? s : 1f).scaleY(has ? s : 1f).translationY(has && fx.equals("lift") ? -1.2f * u : 0)
                    .translationZ(has && fx.equals("lift") ? 2 * u : 0).setDuration(d(450)).setInterpolator(ease).start();
            glow.animate().alpha(has && fx.equals("glow") ? 1f : 0f).setDuration(d(500)).start();
            card.animate().alpha(has || !fx.equals("glow") ? 1f : 0.6f).setDuration(d(400)).start();
            card.setForeground(has && fx.equals("ring") ? ring : null);
            lab.animate().alpha(has ? 1f : 0f).translationY(has ? 0 : -0.4f * u).setDuration(d(350)).start();
            sweep.animate().cancel();
            sweep.setVisibility(View.GONE);
            if (!has) return;
            lastFocus = Math.max(0, items.indexOf(box));
            show(a);
            if (sweepOn && k > 0) { // per-app touch: one soft light in the app's colour crosses its banner, after focus settles
                sweep.setTranslationX(-sw * 1.3f);
                sweep.setVisibility(View.VISIBLE);
                sweep.animate().translationX(w + sw * 0.3f).setStartDelay(d(250)).setDuration(d(900)).setInterpolator(glide)
                        .withEndAction(() -> sweep.setVisibility(View.GONE)).start();
            }
        });
        box.setOnClickListener(v -> {
            if (launching) return;
            if (k == 0) { open(a, null); return; }
            if (launchOn && k > 0) { ribbons(a); return; }
            // press feedback, then the system's scale-up; start delay reset because rise() left one on this view's animator
            box.animate().scaleX(0.95f).scaleY(0.95f).setStartDelay(0).setDuration(d(120)).setInterpolator(ease)
                    .withEndAction(() -> box.animate().scaleX(1f).scaleY(1f).setDuration(d(120)).withEndAction(() ->
                            open(a, ActivityOptions.makeScaleUpAnimation(tile, 0, 0, tile.getWidth(), tile.getHeight()).toBundle())).start()).start();
        });
        box.setOnLongClickListener(v -> { openMenu(a); return true; });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.rightMargin = gap;
        box.setLayoutParams(bp);
        items.add(box);
        return box;
    }

    void show(App a) {
        cur = a;
        String e = getString(a.pkg.equals(Prefs.last(this)) ? R.string.eye_last : R.string.eye_app);
        if (!e.contentEquals(eye.getText())) eye.setText(e);
        name.animate().cancel(); line.animate().cancel();
        name.animate().alpha(0f).translationY(u).setDuration(d(120)).withEndAction(() -> {
            name.setText(a.name); line.setText(a.line);
            name.setTranslationY(u); line.setTranslationY(u); line.setAlpha(0f);
            name.animate().alpha(1f).translationY(0).setDuration(d(600)).setInterpolator(soft).start();
            line.animate().alpha(1f).translationY(0).setStartDelay(d(80)).setDuration(d(600)).setInterpolator(soft).start();
        }).start();
        tint(a);
    }

    /** Background towards the app's colour. */
    void tint(App a) {
        bgAnim.cancel();
        if (!colorBg) return;
        bgFrom = bgColor; bgTo = a.color;
        bgAnim.setDuration(d(900));
        bgAnim.start();
    }

    /** Launch animation: the parts of Home fade out, ribbons in shades of the app's colour drop in from the middle outwards, then the app opens. */
    void ribbons(App a) {
        launching = true;
        View[] parts = {top, clock, hero, scroll};
        for (View v : parts) v.animate().cancel();
        for (View v : parts) v.animate().alpha(0f).setStartDelay(0).setDuration(d(150)).start();
        int mid = RIBBONS / 2;
        float h = root.getHeight();
        launchFx.setVisibility(View.VISIBLE);
        for (int i = 0; i < RIBBONS; i++) {
            View r = launchFx.getChildAt(i);
            int dist = Math.abs(i - mid);
            r.setBackgroundColor(i % 2 == 0 ? mix(a.color, Color.WHITE, 0.10f + 0.05f * dist) : mix(a.color, Color.BLACK, 0.10f + 0.07f * dist));
            r.setPivotY(i % 2 == 0 ? 0 : h);
            r.setScaleY(0f);
            ViewPropertyAnimator p = r.animate().scaleY(1f).setStartDelay(d(100 + 35L * dist)).setDuration(d(320)).setInterpolator(ease);
            // ribbon 0 lands last (outermost); posted so that last frame is drawn before startActivity blocks this thread
            if (i == 0) p.withEndAction(() -> tickH.post(() -> { if (launching) open(a, ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, 0).toBundle()); }));
            p.start();
        }
        tickH.postDelayed(clearRun, d(550) + 2500); // safety net if the app never comes up; onPause removes it
    }

    final Runnable clearRun = this::clearLaunch;
    void clearLaunch() {
        launching = false; // first: a cancelled ribbon's end action must not launch
        tickH.removeCallbacks(clearRun);
        if (launchFx.getVisibility() == View.VISIBLE) {
            for (int i = 0; i < RIBBONS; i++) launchFx.getChildAt(i).animate().cancel();
            launchFx.setVisibility(View.GONE);
        }
        for (View v : new View[]{top, clock, hero, scroll}) { v.animate().cancel(); v.setAlpha(1f); }
    }

    void open(App a, Bundle options) {
        Prefs.p(this).edit().putString("last", a.pkg).apply();
        eye.setText(R.string.eye_last);
        if (Prefs.lastFirst(this)) lastFocus = 0; // it is first once the row is rebuilt
        try { startActivity(a.launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options); }
        catch (Exception e) { clearLaunch(); }
    }

    @Override public void onBackPressed() { closeMenu(); } // this is the home screen: Back only closes the menu

    /** Long-press panel over the screen; the rest of the screen can't take focus while it is open. */
    void openMenu(App a) {
        if (menu != null) return;
        menu = new FrameLayout(this);
        menu.setBackgroundColor(0x99000000);
        menu.setClickable(true);
        menu.forceHasOverlappingRendering(false);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (2 * u);
        panel.setPadding(pad, pad, pad, pad);
        panel.setClipToPadding(false); // the focused item grows 8%; clipping it at the padding squared its corners
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(1.8f * u);
        g.setColor(0xF21C1C1E);
        panel.setBackground(g);
        TextView title = text(2f, 700, 0xFFFFFFFF, -0.02f);
        title.setText(a.name);
        title.setPadding(0, 0, 0, (int) (0.6f * u));
        panel.addView(title);
        Uri uri = Uri.parse("package:" + a.pkg);
        menuItem(panel, R.string.menu_front, () -> {
            List<String> o = new ArrayList<>();
            for (App x : apps) o.add(x.pkg);
            o.remove(a.pkg); o.add(0, a.pkg);
            Prefs.p(this).edit().putString(Prefs.pk(this, "order"), String.join(",", o)).apply();
            lastFocus = 0;
            closeMenu();
        });
        menuItem(panel, R.string.menu_hide, () -> {
            Set<String> h = Prefs.hidden(this);
            h.add(a.pkg);
            Prefs.p(this).edit().putStringSet(Prefs.pk(this, "hidden"), h).apply();
            closeMenu();
        });
        // these screens can change the app list: clearing lastSig after closeMenu's load makes the next resume rebuild
        menuItem(panel, R.string.menu_info, () -> { closeMenu(); lastSig = ""; launch(this, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)); });
        menuItem(panel, R.string.menu_uninstall, () -> { closeMenu(); lastSig = ""; launch(this, new Intent(Intent.ACTION_DELETE, uri)); });
        menu.addView(panel, new FrameLayout.LayoutParams((int) (26 * u), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(menu, match());
        panel.getChildAt(1).requestFocus();
        top.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        menu.setAlpha(0f);
        panel.setScaleX(0.92f); panel.setScaleY(0.92f);
        menu.animate().alpha(1f).setDuration(d(180)).start();
        panel.animate().scaleX(1f).scaleY(1f).setDuration(d(220)).setInterpolator(ease).start();
    }

    void menuItem(LinearLayout panel, int label, Runnable ok) {
        TextView b = button(getString(label), ok);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (0.8f * u);
        panel.addView(b, lp);
    }

    void closeMenu() {
        FrameLayout m = menu;
        if (m == null) return;
        menu = null;
        m.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        top.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS); // LinearLayout's default
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS); // HorizontalScrollView's default
        m.getChildAt(0).animate().scaleX(0.92f).scaleY(0.92f).setDuration(d(150)).start();
        m.animate().alpha(0f).setDuration(d(150)).withEndAction(() -> root.removeView(m)).start();
        load(); // rebuilds only if Move to front / Hide changed a setting
        if (!items.isEmpty()) items.get(Math.min(lastFocus, items.size() - 1)).requestFocus();
    }

    /** A quick-settings button at the top; Up from the app row reaches it. */
    View pill(int label, Intent target, boolean first) {
        TextView p = button(getString(label), () -> launch(this, target));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!first) lp.leftMargin = (int) (1.1f * u);
        p.setLayoutParams(lp);
        return p;
    }

    /** Opens a screen; falls back to the main settings screen if the target isn't there. */
    static void launch(Activity act, Intent target) {
        // CLEAR_TASK: otherwise Settings comes back on whatever screen was left open
        boolean own = target.getComponent() != null && act.getPackageName().equals(target.getComponent().getPackageName());
        try { act.startActivity(own ? target : target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); }
        catch (Exception e) {
            try { act.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); } catch (Exception ignored) { }
        }
    }

    TextView button(String label, Runnable ok) {
        TextView p = text(1.15f, 500, 0xFFF5F5F7, 0.01f);
        p.setText(label);
        p.setPadding((int) (1.3f * u), (int) (0.6f * u), (int) (1.3f * u), (int) (0.6f * u));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(2 * u);
        g.setColor(0x26FFFFFF);
        p.setBackground(g);
        p.setFocusable(true);
        p.setOnFocusChangeListener((v, has) -> {
            g.setColor(has ? 0xFFF5F5F7 : 0x26FFFFFF);
            p.setTextColor(has ? 0xFF111114 : 0xFFF5F5F7);
            p.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(d(250)).start();
        });
        p.setOnClickListener(v -> ok.run());
        return p;
    }

    /** The banner's most vivid colour (Netflix red, not its white background); grey if it has none. bm and px are reused scratch space. */
    static int mainColor(Drawable d, Bitmap bm, int[] px) {
        if (d == null) return GREY;
        bm.eraseColor(0);
        d.setBounds(0, 0, 48, 27);
        d.draw(new Canvas(bm));
        bm.getPixels(px, 0, 48, 0, 0, 48, 27);
        float best = 0; int pick = GREY; float[] hsv = new float[3];
        for (int c : px) {
            if (Color.alpha(c) < 200) continue;
            Color.colorToHSV(c, hsv);
            float score = hsv[1] * hsv[2];
            if (score > best) { best = score; pick = c; }
        }
        return best < 0.25f ? GREY : pick | 0xFF000000;
    }

    static int mainColor(Drawable d) { return mainColor(d, Bitmap.createBitmap(48, 27, Bitmap.Config.ARGB_8888), new int[48 * 27]); }

    /** a towards b by t (0..1). */
    static int mix(int a, int b, float t) {
        return Color.rgb((int) (Color.red(a) * (1 - t) + Color.red(b) * t),
                (int) (Color.green(a) * (1 - t) + Color.green(b) * t),
                (int) (Color.blue(a) * (1 - t) + Color.blue(b) * t));
    }

    TextView text(float size, int weight, int color, float track) {
        TextView t = new TextView(this);
        style(t, size, weight, color, track);
        return t;
    }

    void style(TextView t, float size, int weight, int color, float track) {
        t.setTypeface(font(this, weight));
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size * u);
        t.setTextColor(color);
        t.setLetterSpacing(track);
        t.setIncludeFontPadding(false);
    }

    FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    FrameLayout.LayoutParams at(int gravity, float side, float top) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
        p.leftMargin = p.rightMargin = (int) (side * u);
        p.topMargin = (int) (top * u);
        return p;
    }
}
