package com.arda.marquee;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Two-pane settings: categories left, the focused category's rows right. Up/Down stays in a column, Right enters the rows, Left/Back returns. */
public class Prefs extends Activity {
    static final String[] FX = {"glow", "lift", "ring"};
    static final int[] FX_NAMES = {R.string.fx_glow, R.string.fx_lift, R.string.fx_ring};
    static final String[] ANIM = {"normal", "fast", "off"};
    static final int[] ANIM_NAMES = {R.string.speed_normal, R.string.speed_fast, R.string.off};

    static SharedPreferences p(Context c) { return c.getSharedPreferences("marquee", MODE_PRIVATE); }
    /** People, one per line; until edited, a single "Profile" in the device language. */
    static List<String> people(Context c) {
        String s = p(c).getString("people", "");
        return new ArrayList<>(s.isEmpty() ? Arrays.asList(c.getString(R.string.profile)) : Arrays.asList(s.split("\n")));
    }
    static String person(Context c) {
        List<String> all = people(c);
        String s = p(c).getString("person", "");
        return all.contains(s) ? s : all.get(0);
    }
    static String fx(Context c) { return p(c).getString("fx", "glow"); }
    static boolean colorBg(Context c) { return p(c).getBoolean("colorBg", true); }
    static boolean desc(Context c) { return p(c).getBoolean("desc", true); }
    static boolean clock24(Context c) { return p(c).getBoolean("clock24", true); }
    static boolean lastFirst(Context c) { return p(c).getBoolean("lastFirst", false); }
    static String last(Context c) { return p(c).getString("last", ""); }
    static String anim(Context c) { return p(c).getString("anim", "normal"); }
    /** Multiplier for every animation duration: Normal 1, Fast 0.5, Off 0 (instant). */
    static float speed(Context c) { String s = anim(c); return s.equals("off") ? 0f : s.equals("fast") ? 0.5f : 1f; }
    static boolean launchFx(Context c) { return p(c).getBoolean("launchFx", true); }
    static boolean sweep(Context c) { return p(c).getBoolean("sweep", true); }
    static final String[] PER_PERSON = {"hidden", "order"};
    /** Key of a per-person setting. The first time a person uses it, the current global value is copied in so nothing disappears. */
    @SuppressWarnings("unchecked")
    static String pk(Context c, String base) {
        SharedPreferences s = p(c);
        String k = person(c) + "/" + base;
        Object v = s.getAll().get(base);
        if (!s.contains(k) && v != null) {
            SharedPreferences.Editor e = s.edit();
            if (v instanceof String) e.putString(k, (String) v); else e.putStringSet(k, (Set<String>) v);
            e.apply();
        }
        return k;
    }
    /** Renames a person's own keys; to == null deletes them. */
    @SuppressWarnings("unchecked")
    static void moveKeys(Context c, String from, String to) {
        SharedPreferences s = p(c);
        SharedPreferences.Editor e = s.edit();
        for (String base : PER_PERSON) {
            Object v = s.getAll().get(from + "/" + base);
            if (v == null) continue;
            e.remove(from + "/" + base);
            if (to == null) continue;
            if (v instanceof String) e.putString(to + "/" + base, (String) v); else e.putStringSet(to + "/" + base, (Set<String>) v);
        }
        e.apply();
    }
    static final int[] SAVER = {0, 2, 5, 10};
    static int saver(Context c) { return p(c).getInt("saver", 5); }
    static boolean greet(Context c) { return p(c).getBoolean("greet", true); }
    /** Weather location: city name, latitude, longitude; empty name = weather hidden. */
    static String place(Context c) { return p(c).getString("wName", ""); }
    static Set<String> hidden(Context c) { return new HashSet<>(p(c).getStringSet(pk(c, "hidden"), new HashSet<>())); }
    /** Saved app order; empty until the user moves something. */
    static List<String> order(Context c) {
        String s = p(c).getString(pk(c, "order"), "");
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }

    static final int[] CATS = {R.string.people, R.string.cat_look, R.string.cat_apps, R.string.cat_time,
            R.string.saver, R.string.cat_anim, R.string.cat_system, R.string.cat_about};

    float u, k = 1;
    Typeface inter;
    LinearLayout cats, list;
    ScrollView pane;
    final DecelerateInterpolator ease = new DecelerateInterpolator(2f);
    List<String> pkgs, names; // what Home currently lists, in order (passed in)
    int cat, shown = -1; // focused category, and the one the right pane shows
    boolean editLoc; // the editor is for the weather location, not a person
    boolean peopleMode; // right pane shows the People editor
    boolean armed; // "Reset this person's list" waits for a second OK
    boolean building; // rebuilding the right pane: focus jumps around on its own, ignore it
    String editing; // People editor: name being renamed, "" while adding, null when not editing
    String moving; // package being moved with Up/Down
    EditText field;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        u = getResources().getDisplayMetrics().widthPixels / 100f;
        k = speed(this);
        inter = Typeface.createFromAsset(getAssets(), "Inter.ttf");
        pkgs = new ArrayList<>(getIntent().getStringArrayListExtra("pkgs"));
        names = new ArrayList<>(getIntent().getStringArrayListExtra("names"));
        getWindow().setBackgroundDrawable(null); // root paints the background; saves a full-screen fill
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(0xFF0B0B0D);
        root.setClipChildren(false); // focused rows grow a little; clipping squared their right corners
        root.setPadding((int) (5 * u), (int) (4 * u), (int) (5 * u), 0);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setClipChildren(false);
        TextView title = text(2.4f, 700, Color.WHITE);
        title.setText(R.string.prefs_title);
        title.setLetterSpacing(-0.02f);
        left.addView(title);
        cats = new LinearLayout(this);
        cats.setOrientation(LinearLayout.VERTICAL);
        cats.setClipChildren(false);
        for (int i = 0; i < CATS.length; i++) cats.addView(catRow(i));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = (int) (2 * u);
        left.addView(cats, cp);
        root.addView(left, new LinearLayout.LayoutParams((int) (22 * u), ViewGroup.LayoutParams.MATCH_PARENT));

        pane = new ScrollView(this);
        pane.setVerticalScrollBarEnabled(false);
        pane.setClipToPadding(false);
        pane.setClipChildren(false);
        pane.setPadding((int) u, 0, (int) u, (int) (6 * u)); // side room for the focused row's 2% growth
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.forceHasOverlappingRendering(false); // cross-fade applies alpha per child: no offscreen buffer
        pane.addView(list);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        pl.leftMargin = (int) (4 * u);
        root.addView(pane, pl);
        setContentView(root);
        build();
        cats.getChildAt(0).requestFocus();
    }

    long d(long ms) { return (long) (ms * k); }

    TextView catRow(int i) {
        TextView t = text(1.5f, 500, 0xFF8E8E93);
        t.setText(CATS[i]);
        t.setPadding((int) (1.6f * u), (int) (0.9f * u), (int) (1.6f * u), (int) (0.9f * u));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(1.2f * u);
        g.setColor(0);
        t.setBackground(g);
        t.setPivotX(0);
        t.setFocusable(true);
        t.setOnFocusChangeListener((v, has) -> {
            if (building) return;
            if (has && cat != i) { cat = i; peopleMode = false; editing = null; editLoc = false; moving = null; armed = false; swap(); }
            paintCats();
        });
        t.setOnClickListener(v -> enterRows());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (0.4f * u);
        t.setLayoutParams(lp);
        return t;
    }

    /** Focused category: white pill. The one whose rows are open: soft pill. Others: grey text. */
    void paintCats() {
        for (int j = 0; j < cats.getChildCount(); j++) {
            TextView t = (TextView) cats.getChildAt(j);
            boolean f = t.isFocused(), sel = j == cat;
            ((GradientDrawable) t.getBackground()).setColor(f ? 0xFFF5F5F7 : sel ? 0x1FFFFFFF : 0);
            t.setTextColor(f ? 0xFF111114 : sel ? 0xFFF5F5F7 : 0xFF8E8E93);
            t.animate().scaleX(f ? 1.04f : 1f).scaleY(f ? 1.04f : 1f).setDuration(d(200)).setInterpolator(ease).start();
        }
    }

    /** Category changed: fade the right pane out, rebuild, fade in. */
    void swap() {
        list.animate().cancel();
        if (k == 0) { list.setAlpha(1f); list.setTranslationX(0); build(); pane.scrollTo(0, 0); return; }
        list.animate().alpha(0f).translationX(u).setDuration(d(90)).setInterpolator(ease).withEndAction(() -> {
            build();
            pane.scrollTo(0, 0);
            list.animate().alpha(1f).translationX(0).setDuration(d(200)).start();
        }).start();
    }

    void enterRows() {
        if (shown != cat) { list.animate().cancel(); list.setAlpha(1f); list.setTranslationX(0); build(); }
        for (int i = 0; i < list.getChildCount(); i++) if (list.getChildAt(i).isFocusable()) { list.getChildAt(i).requestFocus(); return; }
    }

    void focusCat() { cats.getChildAt(cat).requestFocus(); }

    /** Next focusable sibling up or down; stops at the ends instead of jumping to the other column. */
    void step(ViewGroup col, View f, int dir) {
        for (int i = col.indexOfChild(f) + dir; i >= 0 && i < col.getChildCount(); i += dir) {
            View v = col.getChildAt(i);
            if (v.isFocusable() && v.getVisibility() == View.VISIBLE) { v.requestFocus(); return; }
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            int c = e.getKeyCode();
            if (moving != null) {
                if (c == KeyEvent.KEYCODE_DPAD_UP || c == KeyEvent.KEYCODE_DPAD_DOWN) { moveApp(c == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1); return true; }
                if (c == KeyEvent.KEYCODE_DPAD_CENTER || c == KeyEvent.KEYCODE_ENTER || c == KeyEvent.KEYCODE_DPAD_LEFT || c == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (e.getRepeatCount() == 0) endMove(); // repeats belong to the long press that started moving
                    return true;
                }
            }
            View f = getCurrentFocus();
            ViewParent col = f == null || f == field ? null : f.getParent();
            if (col == cats || col == list) {
                if (c == KeyEvent.KEYCODE_DPAD_UP || c == KeyEvent.KEYCODE_DPAD_DOWN) { step((ViewGroup) col, f, c == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1); return true; }
                if (c == KeyEvent.KEYCODE_DPAD_RIGHT) { if (col == cats) enterRows(); return true; }
                if (c == KeyEvent.KEYCODE_DPAD_LEFT) { if (col == list) focusCat(); return true; }
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override public void onBackPressed() {
        if (moving != null) endMove();
        else if (editing != null && editLoc) { editing = null; editLoc = false; rebuildFocusing(getString(R.string.location)); }
        else if (editing != null) { String back = editing.isEmpty() ? getString(R.string.add_person) : editing; editing = null; rebuildFocusing(back); }
        else if (peopleMode) { peopleMode = false; rebuildFocusing(getString(R.string.people)); }
        else if (list.hasFocus()) focusCat();
        else super.onBackPressed();
    }

    void build() {
        building = true;
        shown = cat;
        k = speed(this);
        list.removeAllViews(); // the window hands focus to the first category meanwhile; building keeps that from switching category
        building = false;
        field = null;
        if (peopleMode) { buildPeople(); return; }
        if (editLoc && editing != null) { head(R.string.location); addField(); return; }
        head(CATS[cat]);
        switch (cat) {
            case 0:
                row(getString(R.string.person), person(this), R.string.hint_person, -1, () -> put("person", next(people(this), person(this))));
                row(getString(R.string.people), String.join(", ", people(this)), R.string.hint_people, -1, () -> peopleMode = true);
                break;
            case 1:
                row(getString(R.string.effect), fxName(fx(this)), R.string.hint_effect, -1, () -> put("fx", next(Arrays.asList(FX), fx(this))));
                row(getString(R.string.background), getString(colorBg(this) ? R.string.bg_app : R.string.bg_black), R.string.hint_bg, -1, () -> p(this).edit().putBoolean("colorBg", !colorBg(this)).apply());
                toggle(R.string.description, "desc", desc(this), R.string.hint_desc);
                break;
            case 2:
                toggle(R.string.last_first, "lastFirst", lastFirst(this), R.string.hint_last);
                row(getString(R.string.reset_list), armed ? getString(R.string.reset_confirm) : "", R.string.hint_reset, -1, this::reset);
                header(getString(R.string.pill_apps).toUpperCase(getResources().getConfiguration().getLocales().get(0)));
                Set<String> hid = hidden(this);
                for (int i = 0; i < pkgs.size(); i++) {
                    String pkg = pkgs.get(i), n = names.get(i);
                    boolean mv = pkg.equals(moving);
                    View r = row(n, mv ? "▲  ▼" : getString(hid.contains(pkg) ? R.string.hidden : R.string.visible), mv ? R.string.hint_moving : R.string.hint_app, -1, () -> {
                        Set<String> h = hidden(this);
                        if (!h.remove(pkg)) h.add(pkg);
                        p(this).edit().putStringSet(pk(this, "hidden"), h).apply();
                    });
                    r.setOnLongClickListener(v -> { moving = pkg; rebuildFocusing(n); return true; });
                }
                break;
            case 3:
                row(getString(R.string.location), place(this).isEmpty() ? getString(R.string.location_none) : place(this), R.string.hint_location, -1, () -> { editLoc = true; editing = place(this); });
                row(getString(R.string.clock), getString(clock24(this) ? R.string.clock_24 : R.string.clock_12), 0, -1, () -> p(this).edit().putBoolean("clock24", !clock24(this)).apply());
                toggle(R.string.greeting, "greet", greet(this), R.string.hint_greet);
                break;
            case 4:
                row(getString(R.string.saver), saver(this) == 0 ? getString(R.string.off) : getString(R.string.saver_min, saver(this)), R.string.hint_saver, -1,
                        () -> p(this).edit().putInt("saver", SAVER[(Arrays.binarySearch(SAVER, saver(this)) + 1 + SAVER.length) % SAVER.length]).apply());
                break;
            case 5:
                int a = Math.max(0, Arrays.asList(ANIM).indexOf(anim(this)));
                row(getString(R.string.anim_speed), getString(ANIM_NAMES[a]), R.string.hint_speed, -1, () -> put("anim", ANIM[(a + 1) % ANIM.length]));
                toggle(R.string.launch_fx, "launchFx", launchFx(this), R.string.hint_launch);
                toggle(R.string.sweep, "sweep", sweep(this), R.string.hint_sweep);
                break;
            case 6:
                for (int i = 0; i < Home.SYS.length; i++) {
                    String action = Home.SYS[i];
                    row(getString(Home.SYS_NAMES[i]), "›", R.string.hint_system, -1, () -> Home.launch(this, new Intent(action)));
                }
                break;
            default:
                String ver = "";
                try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (PackageManager.NameNotFoundException ignored) { }
                row(getString(R.string.version), ver, 0, -1, () -> { });
                row(getString(R.string.licence), getString(R.string.licence_value), 0, -1, () -> { });
        }
    }

    /** First OK arms, second OK clears this person's hidden apps and order; leaving the row disarms. */
    void reset() {
        if (!armed) { armed = true; return; }
        armed = false;
        String hk = pk(this, "hidden"), ok = pk(this, "order");
        p(this).edit().putStringSet(hk, new HashSet<>()).putString(ok, "").apply();
        // the order Home falls back to: known apps first, then by name
        List<Integer> ix = new ArrayList<>();
        for (int i = 0; i < pkgs.size(); i++) ix.add(i);
        ix.sort((x, y) -> { int a = rank(pkgs.get(x)), b = rank(pkgs.get(y)); return a != b ? a - b : names.get(x).compareToIgnoreCase(names.get(y)); });
        List<String> p2 = new ArrayList<>(), n2 = new ArrayList<>();
        for (int i : ix) { p2.add(pkgs.get(i)); n2.add(names.get(i)); }
        pkgs = p2; names = n2;
    }

    static int rank(String pkg) { int i = Home.ORDER.indexOf(pkg); return i < 0 ? 999 : i; }

    void moveApp(int dir) {
        int at = pkgs.indexOf(moving), to = at + dir;
        if (at < 0 || to < 0 || to >= pkgs.size()) return;
        pkgs.add(to, pkgs.remove(at)); names.add(to, names.remove(at));
        p(this).edit().putString(pk(this, "order"), String.join(",", pkgs)).apply();
        rebuildFocusing(names.get(to));
    }

    void endMove() {
        int at = pkgs.indexOf(moving);
        moving = null;
        rebuildFocusing(at < 0 ? "" : names.get(at));
    }

    /** People editor: OK on a name renames it, holding OK deletes it (one always stays), last row adds one. */
    void buildPeople() {
        head(R.string.people);
        if (editing != null) addField();
        for (String n : people(this)) {
            if (n.equals(editing)) continue; // the field above stands in for the row being renamed
            View r = row(n, "", R.string.h_people, -1, () -> editing = n);
            r.setOnLongClickListener(v -> {
                List<String> l = people(this);
                if (l.size() > 1 && l.remove(n)) { savePeople(l); moveKeys(this, n, null); } // person() falls back to the first if this was the current one
                rebuildFocusing(l.get(0));
                return true;
            });
        }
        row(getString(R.string.add_person), "+", 0, -1, () -> editing = "");
    }

    void addField() {
        EditText e = new EditText(this);
        e.setTypeface(inter);
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, 1.6f * u);
        e.setTextColor(0xFFF5F5F7);
        e.setHintTextColor(0xFF8E8E93);
        e.setHint(editLoc ? R.string.location_hint : R.string.name_hint);
        e.setSingleLine(true);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setText(editing);
        e.setSelection(e.length());
        e.setPadding((int) (2 * u), (int) (1.1f * u), (int) (2 * u), (int) (1.1f * u));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(1.2f * u);
        g.setColor(0x26FFFFFF);
        g.setStroke((int) (0.15f * u), 0xFFF5F5F7);
        e.setBackground(g);
        e.setOnEditorActionListener((v, id, ev) -> { commit(e.getText().toString()); return true; });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (0.6f * u);
        list.addView(e, lp);
        field = e;
    }

    void commit(String text) {
        if (editing == null) return; // the IME can fire more than one action for one press
        if (editLoc) { commitLoc(text.trim()); return; }
        String old = editing;
        editing = null;
        String n = text.trim();
        List<String> l = people(this);
        if (!n.isEmpty() && !l.contains(n)) {
            if (old.isEmpty()) l.add(n);
            else {
                if (old.equals(person(this))) put("person", n);
                l.set(l.indexOf(old), n);
                moveKeys(this, old, n);
            }
            savePeople(l);
        }
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(list.getWindowToken(), 0);
        rebuildFocusing(!n.isEmpty() ? n : old.isEmpty() ? getString(R.string.add_person) : old);
    }

    /** Looks the city up (network, off the main thread); empty text clears the location. */
    void commitLoc(String q) {
        editing = null;
        editLoc = false;
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(list.getWindowToken(), 0);
        if (q.isEmpty()) p(this).edit().remove("wName").remove("wLat").remove("wLon").apply();
        rebuildFocusing(getString(R.string.location));
        if (q.isEmpty()) return;
        new Thread(() -> {
            String[] r = null;
            try { r = Weather.find(q); } catch (Exception ignored) { }
            String[] found = r;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (found == null) { Toast.makeText(this, R.string.location_fail, Toast.LENGTH_SHORT).show(); return; }
                p(this).edit().putString("wName", found[0]).putString("wLat", found[1]).putString("wLon", found[2]).apply();
                if (cat == 3 && !peopleMode && editing == null && list.hasFocus()) rebuildFocusing(getString(R.string.location));
            });
        }).start();
    }

    void savePeople(List<String> l) { put("people", String.join("\n", l)); }

    String onOff(boolean on) { return getString(on ? R.string.on : R.string.off); }

    void toggle(int label, String key, boolean on, int hint) {
        row(getString(label), onOff(on), hint, on ? 1 : 0, () -> p(this).edit().putBoolean(key, !on).apply());
    }

    /** A setting: label left, value right, hint under it while focused. pill: -1 plain value, 0/1 an off/on switch. */
    View row(String label, String value, int hint, int pill, Runnable onOk) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setTag(label);
        r.setPadding((int) (2 * u), (int) (1.1f * u), (int) (2 * u), (int) (1.1f * u));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(1.2f * u);
        r.setBackground(g);
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = text(1.6f, 500, 0xFFF5F5F7);
        l.setText(label);
        line.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text(pill < 0 ? 1.45f : 1.15f, pill < 0 ? 400 : 600, 0xFFA1A1A6);
        v.setText(value);
        GradientDrawable pg = new GradientDrawable();
        if (pill >= 0) {
            pg.setCornerRadius(2 * u);
            v.setBackground(pg);
            v.setPadding((int) (1.1f * u), (int) (0.35f * u), (int) (1.1f * u), (int) (0.35f * u));
        }
        line.addView(v);
        r.addView(line);
        TextView h = text(1.15f, 400, 0xFF6E6E73);
        h.setVisibility(View.GONE);
        if (hint != 0) h.setText(hint);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = (int) (0.4f * u);
        r.addView(h, hp);
        r.setFocusable(true);
        View.OnFocusChangeListener paint = (x, has) -> {
            g.setColor(has ? 0xFFF5F5F7 : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111114 : 0xFFF5F5F7);
            if (pill < 0) v.setTextColor(has ? 0xFF6E6E73 : 0xFFA1A1A6);
            else {
                boolean on = pill == 1;
                pg.setColor(on ? (has ? 0xFF111114 : 0xFFF5F5F7) : (has ? 0x1F000000 : 0x26FFFFFF));
                v.setTextColor(on != has ? 0xFF111114 : 0xFFF5F5F7);
            }
            if (hint != 0) h.setVisibility(has ? View.VISIBLE : View.GONE);
            if (!has && !building && r.getParent() != null && armed && label.equals(getString(R.string.reset_list))) { armed = false; v.setText(""); }
            r.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f).setDuration(d(180)).setInterpolator(ease).start();
        };
        paint.onFocusChange(r, false);
        r.setOnFocusChangeListener(paint);
        r.setOnClickListener(x -> { onOk.run(); rebuildFocusing(label); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (0.6f * u);
        list.addView(r, lp);
        return r;
    }

    void rebuildFocusing(String label) {
        build();
        if (field != null) {
            EditText e = field;
            e.requestFocus();
            e.post(() -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(e, InputMethodManager.SHOW_IMPLICIT));
            return;
        }
        View v = list.findViewWithTag(label);
        for (int i = 0; v == null && i < list.getChildCount(); i++) if (list.getChildAt(i).isFocusable()) v = list.getChildAt(i);
        if (v != null) v.requestFocus();
    }

    /** Right pane title, level with the Settings title on the left. */
    void head(int s) {
        TextView t = text(2.4f, 700, Color.WHITE);
        t.setText(s);
        t.setLetterSpacing(-0.02f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (1.4f * u);
        list.addView(t, lp);
    }

    void header(String s) {
        TextView h = text(1.05f, 600, 0xFF8E8E93);
        h.setText(s);
        h.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (2.2f * u);
        lp.bottomMargin = (int) (0.2f * u);
        list.addView(h, lp);
    }

    void put(String k, String v) { p(this).edit().putString(k, v).apply(); }

    static String next(List<String> all, String cur) { return all.get((all.indexOf(cur) + 1) % all.size()); }

    String fxName(String id) { return getString(FX_NAMES[Math.max(0, Arrays.asList(FX).indexOf(id))]); }

    TextView text(float size, int weight, int color) {
        TextView t = new TextView(this);
        t.setTypeface(inter);
        t.setFontVariationSettings("'wght' " + weight);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
}
