package com.arda.salon;

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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
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

    static class App { String pkg, name, line; Intent launch; Drawable banner; int color; }

    // Named on purpose: d8 34.0.0 crashes on javac 21's metadata for anonymous classes.
    static class Round extends ViewOutlineProvider {
        final float r;
        Round(float r) { this.r = r; }
        @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
    }

    float u; // 1% of screen width, so the layout matches the 16:9 preview at any resolution
    Typeface inter;
    GradientDrawable bg;
    int bgColor = Color.BLACK;
    TextView name, line, eye;
    FrameLayout root, menu; // menu: the long-press overlay while it is open
    LinearLayout top, clock, hero, row;
    HorizontalScrollView scroll;
    TextClock time;
    final List<App> apps = new ArrayList<>();
    final List<View> items = new ArrayList<>();
    ValueAnimator bgAnim;
    TextView who;
    String fx = "glow";
    boolean colorBg = true;
    boolean intro = true; // entrance animation plays once per onCreate, not on every resume
    int lastFocus = 0;
    final Intent prefsIntent = new Intent();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        u = getResources().getDisplayMetrics().widthPixels / 100f;
        inter = Typeface.createFromAsset(getAssets(), "Inter.ttf");
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        View back = new View(this);
        bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.BLACK, Color.BLACK});
        back.setBackground(bg);
        root.addView(back, match());
        View shade1 = new View(this);
        shade1.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xE6000000, 0x59000000, 0x00000000}));
        root.addView(shade1, match());
        View shade2 = new View(this);
        shade2.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x99000000, 0x00000000, 0x00000000}));
        root.addView(shade2, match());

        top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        who = text(1.3f, 500, 0xFFF5F5F7, 0);
        who.setPadding(0, 0, (int) (0.8f * u), 0);
        top.addView(who);
        // Google TV lacks the standard Display action and blocks its Bluetooth screen to other apps; these are the screens it allows.
        top.addView(pill(R.string.pill_settings, new Intent(android.provider.Settings.ACTION_SETTINGS)));
        top.addView(pill(R.string.pill_wifi, new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)));
        top.addView(pill(R.string.pill_display, new Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)));
        top.addView(pill(R.string.pill_apps, new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
        prefsIntent.setClass(this, Prefs.class);
        top.addView(pill(R.string.pill_home, prefsIntent));
        root.addView(top, at(Gravity.TOP | Gravity.START, 5, 3f));

        clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.END);
        time = new TextClock(this);
        style(time, 2.6f, 500, 0xFFF5F5F7, -0.02f);
        TextClock date = new TextClock(this);
        style(date, 1.2f, 400, 0xFFA1A1A6, 0.01f);
        date.setFormat24Hour("EEEE, d MMMM"); date.setFormat12Hour("EEEE, d MMMM");
        clock.addView(time); clock.addView(date);
        root.addView(clock, at(Gravity.TOP | Gravity.END, 4.5f, 3.2f));

        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        eye = text(1.15f, 600, 0xB3FFFFFF, 0.06f);
        eye.setText(R.string.eye_app);
        name = text(6.4f, 700, 0xFFFFFFFF, -0.035f);
        line = text(1.55f, 400, 0xC7FFFFFF, 0.005f);
        hero.addView(eye); hero.addView(name); hero.addView(line);
        FrameLayout.LayoutParams hp = at(Gravity.TOP | Gravity.START, 5, 15);
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
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        sp.bottomMargin = 0;
        root.addView(scroll, sp);
        root.setClipChildren(false);
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        load(); // picks up anything changed in Prefs
    }

    String lastSig = "";

    void load() {
        // Home resumes several times per Home press; only rebuild when a setting changed, so focus isn't yanked away.
        Map<String, Object> all = new HashMap<>(Prefs.p(this).getAll());
        if (!Prefs.lastFirst(this)) all.remove("last"); // opening an app only rebuilds the row when that reorders it
        String sig = all.toString();
        if (sig.equals(lastSig) && !apps.isEmpty()) return;
        lastSig = sig;
        fx = Prefs.fx(this);
        colorBg = Prefs.colorBg(this);
        who.setText(Prefs.person(this));
        String tf = Prefs.clock24(this) ? "HH:mm" : "h:mm a"; // set both, so the setting wins over the system's 24-hour switch
        time.setFormat24Hour(tf); time.setFormat12Hour(tf);
        line.setVisibility(Prefs.desc(this) ? View.VISIBLE : View.GONE);
        if (!colorBg) { if (bgAnim != null) bgAnim.cancel(); bgColor = Color.BLACK; bg.setColors(new int[]{0xFF000000, 0xFF0B0B0D}); }
        row.removeAllViews(); items.clear(); apps.clear();
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(q, 0);
        List<String> saved = Prefs.order(this);
        List<String> order = new ArrayList<>(saved.isEmpty() ? ORDER : saved);
        String last = Prefs.last(this);
        if (Prefs.lastFirst(this) && !last.isEmpty()) { order.remove(last); order.add(0, last); }
        found.sort((a, c) -> {
            int ia = order.indexOf(a.activityInfo.packageName), ic = order.indexOf(c.activityInfo.packageName);
            if (ia < 0) ia = 999; if (ic < 0) ic = 999;
            return ia != ic ? ia - ic : a.loadLabel(pm).toString().compareToIgnoreCase(c.loadLabel(pm).toString());
        });
        for (ResolveInfo r : found) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || pkg.equals("com.google.android.videos")) continue; // Play Movies is retired
            App a = new App();
            a.pkg = pkg;
            String known = NAMES.get(pkg);
            a.name = known != null ? known : r.loadLabel(pm).toString();
            a.line = known != null ? getString(DESCS.get(pkg)) : "";
            a.launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (a.launch == null) continue;
            Drawable d = r.activityInfo.loadBanner(pm);
            if (d == null) d = r.activityInfo.applicationInfo.loadBanner(pm);
            a.banner = d != null ? d : r.loadIcon(pm);
            a.color = mainColor(a.banner);
            apps.add(a);
        }
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
            rise(top, -1.5f, 0); rise(clock, -1.5f, 0); rise(hero, 2f, 100);
            for (int i = 0; i < items.size(); i++) rise(items.get(i), 3f, 200 + i * 40L);
        }
    }

    void rise(View v, float from, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(from * u);
        v.animate().alpha(1f).translationY(0).setStartDelay(delay).setDuration(700).setInterpolator(new DecelerateInterpolator(2f)).start();
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
        gd.setColors(new int[]{(a.color & 0xFFFFFF) | 0xB0000000, a.color & 0xFFFFFF});
        gd.setGradientRadius(h * 1.25f); // must fit inside the glow's own box, or its edge shows as a straight cut
        gd.setGradientCenter(0.5f, 0.5f);
        glow.setBackground(gd);
        glow.setAlpha(0f);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams((int) (w * 1.7f), (int) (h * 2.6f), Gravity.CENTER);
        gp.leftMargin = gp.rightMargin = -(int) (w * 0.35f);
        gp.topMargin = gp.bottomMargin = -(int) (h * 0.8f);
        tile.addView(glow, gp);
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
        face.setOutlineProvider(new Round(1.4f * u));
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(1.4f * u);
        ring.setStroke((int) (0.25f * u), Color.WHITE);
        face.setClipToOutline(true);
        face.setAlpha(0.6f);
        tile.addView(face, new FrameLayout.LayoutParams(w, h, Gravity.CENTER));
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
            DecelerateInterpolator ease = new DecelerateInterpolator(2.2f);
            float s = fx.equals("lift") ? 1.18f : fx.equals("ring") ? 1.06f : 1.12f;
            tile.animate().scaleX(has ? s : 1f).scaleY(has ? s : 1f).translationY(has && fx.equals("lift") ? -1.2f * u : 0)
                    .translationZ(has && fx.equals("lift") ? 2 * u : 0).setDuration(450).setInterpolator(ease).start();
            glow.animate().alpha(has && fx.equals("glow") ? 1f : 0f).setDuration(500).start();
            face.animate().alpha(has || !fx.equals("glow") ? 1f : 0.6f).setDuration(400).start();
            face.setForeground(has && fx.equals("ring") ? ring : null);
            lab.animate().alpha(has ? 1f : 0f).translationY(has ? 0 : -0.4f * u).setDuration(350).start();
            if (has) { lastFocus = items.indexOf(box); show(a); }
        });
        // press feedback, then launch; start delay reset because rise() left one on this view's animator
        box.setOnClickListener(v -> box.animate().scaleX(0.95f).scaleY(0.95f).setStartDelay(0).setDuration(120).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> box.animate().scaleX(1f).scaleY(1f).setDuration(120).withEndAction(() -> open(a, tile)).start()).start());
        box.setOnLongClickListener(v -> { openMenu(a); return true; });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.rightMargin = gap;
        box.setLayoutParams(bp);
        items.add(box);
        return box;
    }

    void show(App a) {
        eye.setText(a.pkg.equals(Prefs.last(this)) ? R.string.eye_last : R.string.eye_app);
        name.animate().cancel(); line.animate().cancel();
        name.animate().alpha(0f).translationY(u).setDuration(120).withEndAction(() -> {
            name.setText(a.name); line.setText(a.line);
            name.setTranslationY(u); line.setTranslationY(u); line.setAlpha(0f);
            name.animate().alpha(1f).translationY(0).setDuration(600).setInterpolator(new DecelerateInterpolator(2.5f)).start();
            line.animate().alpha(1f).translationY(0).setStartDelay(80).setDuration(600).setInterpolator(new DecelerateInterpolator(2.5f)).start();
        }).start();
        if (bgAnim != null) bgAnim.cancel();
        if (!colorBg) return;
        bgAnim = ValueAnimator.ofArgb(bgColor, a.color);
        bgAnim.setDuration(900);
        bgAnim.addUpdateListener(v -> {
            bgColor = (int) v.getAnimatedValue();
            bg.setColors(new int[]{mix(bgColor, Color.BLACK, 0.78f), mix(bgColor, Color.BLACK, 0.2f)});
        });
        bgAnim.start();
    }

    void open(App a, View from) {
        Prefs.p(this).edit().putString("last", a.pkg).apply();
        eye.setText(R.string.eye_last);
        if (Prefs.lastFirst(this)) lastFocus = 0; // it is first once the row is rebuilt
        try {
            startActivity(a.launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle());
        } catch (Exception ignored) { }
    }

    @Override public void onBackPressed() { closeMenu(); } // this is the home screen: Back only closes the menu

    /** Long-press panel over the screen; the rest of the screen can't take focus while it is open. */
    void openMenu(App a) {
        if (menu != null) return;
        menu = new FrameLayout(this);
        menu.setBackgroundColor(0x99000000);
        menu.setClickable(true);
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
            Prefs.p(this).edit().putString("order", String.join(",", o)).apply();
            lastFocus = 0;
            closeMenu();
        });
        menuItem(panel, R.string.menu_hide, () -> {
            Set<String> h = Prefs.hidden(this);
            h.add(a.pkg);
            Prefs.p(this).edit().putStringSet("hidden", h).apply();
            closeMenu();
        });
        // these screens can change the app list: clearing lastSig after closeMenu's load makes the next resume rebuild
        menuItem(panel, R.string.menu_info, () -> { closeMenu(); lastSig = ""; launch(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)); });
        menuItem(panel, R.string.menu_uninstall, () -> { closeMenu(); lastSig = ""; launch(new Intent(Intent.ACTION_DELETE, uri)); });
        menu.addView(panel, new FrameLayout.LayoutParams((int) (26 * u), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(menu, match());
        panel.getChildAt(1).requestFocus();
        top.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        menu.setAlpha(0f);
        panel.setScaleX(0.92f); panel.setScaleY(0.92f);
        menu.animate().alpha(1f).setDuration(180).start();
        panel.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new DecelerateInterpolator(2f)).start();
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
        m.getChildAt(0).animate().scaleX(0.92f).scaleY(0.92f).setDuration(150).start();
        m.animate().alpha(0f).setDuration(150).withEndAction(() -> root.removeView(m)).start();
        load(); // rebuilds only if Move to front / Hide changed a setting
        if (!items.isEmpty()) items.get(Math.min(lastFocus, items.size() - 1)).requestFocus();
    }

    /** A quick-settings button at the top; Up from the app row reaches it. */
    View pill(int label, Intent target) {
        TextView p = button(getString(label), () -> launch(target));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = (int) (1.1f * u);
        p.setLayoutParams(lp);
        return p;
    }

    /** Falls back to the main settings screen if the target isn't there. */
    void launch(Intent target) {
        // CLEAR_TASK: otherwise Settings comes back on whatever screen was left open
        boolean own = target.getComponent() != null && getPackageName().equals(target.getComponent().getPackageName());
        try { startActivity(own ? target : target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); }
        catch (Exception e) {
            try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); } catch (Exception ignored) { }
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
            p.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(250).start();
        });
        p.setOnClickListener(v -> ok.run());
        return p;
    }

    /** The banner's most vivid colour (Netflix red, not its white background); grey if it has none. */
    static int mainColor(Drawable d) {
        if (d == null) return 0xFF8E8E93;
        Bitmap bm = Bitmap.createBitmap(48, 27, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, 48, 27);
        d.draw(new Canvas(bm));
        float best = 0; int pick = 0xFF8E8E93; float[] hsv = new float[3];
        for (int y = 0; y < 27; y++) for (int x = 0; x < 48; x++) {
            int c = bm.getPixel(x, y);
            if (Color.alpha(c) < 200) continue;
            Color.colorToHSV(c, hsv);
            float score = hsv[1] * hsv[2];
            if (score > best) { best = score; pick = c; }
        }
        return best < 0.25f ? 0xFF8E8E93 : pick | 0xFF000000;
    }

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
        t.setTypeface(inter);
        t.setFontVariationSettings("'wght' " + weight);
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
