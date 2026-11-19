package com.arda.salon;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The home screen's own settings. OK cycles a value; on an app row OK hides/shows it and Left/Right moves it. */
public class Prefs extends Activity {
    static final String[] FX = {"glow", "lift", "ring"};
    static final int[] FX_NAMES = {R.string.fx_glow, R.string.fx_lift, R.string.fx_ring};

    static SharedPreferences p(Context c) { return c.getSharedPreferences("salon", MODE_PRIVATE); }
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
    static Set<String> hidden(Context c) { return new HashSet<>(p(c).getStringSet("hidden", new HashSet<>())); }
    /** Saved app order; empty until the user moves something. */
    static List<String> order(Context c) {
        String s = p(c).getString("order", "");
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }

    float u;
    Typeface inter;
    LinearLayout list;
    List<String> pkgs, names; // what Home currently lists, in order (passed in)
    boolean peopleMode; // showing the People screen instead of the main list
    String editing; // People screen: name being renamed, "" while adding, null when not editing
    EditText field;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        u = getResources().getDisplayMetrics().widthPixels / 100f;
        inter = Typeface.createFromAsset(getAssets(), "Inter.ttf");
        pkgs = new ArrayList<>(getIntent().getStringArrayListExtra("pkgs"));
        names = new ArrayList<>(getIntent().getStringArrayListExtra("names"));
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0B0B0D);
        sv.setClipToPadding(false);
        sv.setPadding((int) (22 * u), (int) (4 * u), (int) (22 * u), (int) (6 * u));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        setContentView(sv);
        rebuildFocusing(""); // builds, then focuses the first row so something is lit on open
    }

    @Override public void onBackPressed() {
        if (editing != null) { String back = editing.isEmpty() ? getString(R.string.add_person) : editing; editing = null; rebuildFocusing(back); }
        else if (peopleMode) { peopleMode = false; rebuildFocusing(getString(R.string.people)); }
        else super.onBackPressed();
    }

    void build() {
        list.removeAllViews();
        field = null;
        if (peopleMode) { buildPeople(); return; }
        title(R.string.prefs_title);
        header(getString(R.string.h_look));
        row(getString(R.string.person), person(this), () -> put("person", next(people(this), person(this))));
        row(getString(R.string.people), String.join(", ", people(this)), () -> peopleMode = true);
        row(getString(R.string.effect), fxName(fx(this)), () -> put("fx", FX[(Arrays.asList(FX).indexOf(fx(this)) + 1) % FX.length]));
        row(getString(R.string.background), getString(colorBg(this) ? R.string.bg_app : R.string.bg_black), () -> p(this).edit().putBoolean("colorBg", !colorBg(this)).apply());
        row(getString(R.string.description), onOff(desc(this)), () -> p(this).edit().putBoolean("desc", !desc(this)).apply());
        row(getString(R.string.clock), getString(clock24(this) ? R.string.clock_24 : R.string.clock_12), () -> p(this).edit().putBoolean("clock24", !clock24(this)).apply());
        row(getString(R.string.last_first), onOff(lastFirst(this)), () -> p(this).edit().putBoolean("lastFirst", !lastFirst(this)).apply());
        header(getString(R.string.h_apps));
        Set<String> hid = hidden(this);
        for (int i = 0; i < pkgs.size(); i++) {
            final int at = i;
            String pkg = pkgs.get(i);
            TextView r = row(names.get(i), getString(hid.contains(pkg) ? R.string.hidden : R.string.visible), () -> {
                Set<String> h = hidden(this);
                if (!h.remove(pkg)) h.add(pkg);
                p(this).edit().putStringSet("hidden", h).apply();
            });
            r.setOnKeyListener((v, code, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                int to = code == KeyEvent.KEYCODE_DPAD_LEFT ? at - 1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? at + 1 : -2;
                if (to == -2) return false;
                if (to < 0 || to >= pkgs.size()) return true;
                pkgs.add(to, pkgs.remove(at)); names.add(to, names.remove(at));
                p(this).edit().putString("order", String.join(",", pkgs)).apply();
                rebuildFocusing(names.get(to));
                return true;
            });
        }
    }

    /** People screen: OK on a name renames it, holding OK deletes it (one always stays), last row adds one. */
    void buildPeople() {
        title(R.string.people);
        header(getString(R.string.h_people));
        if (editing != null) addField();
        for (String n : people(this)) {
            if (n.equals(editing)) continue; // the field above stands in for the row being renamed
            TextView r = row(n, "", () -> editing = n);
            r.setOnLongClickListener(v -> {
                List<String> l = people(this);
                if (l.size() > 1 && l.remove(n)) savePeople(l); // person() falls back to the first if this was the current one
                rebuildFocusing(l.get(0));
                return true;
            });
        }
        row(getString(R.string.add_person), "", () -> editing = "");
    }

    void addField() {
        EditText e = new EditText(this);
        e.setTypeface(inter);
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, 1.6f * u);
        e.setTextColor(0xFFF5F5F7);
        e.setHintTextColor(0xFF8E8E93);
        e.setHint(R.string.name_hint);
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
        String old = editing;
        editing = null;
        String n = text.trim();
        List<String> l = people(this);
        if (!n.isEmpty() && !l.contains(n)) {
            if (old.isEmpty()) l.add(n);
            else {
                if (old.equals(person(this))) put("person", n);
                l.set(l.indexOf(old), n);
            }
            savePeople(l);
        }
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(list.getWindowToken(), 0);
        rebuildFocusing(!n.isEmpty() ? n : old.isEmpty() ? getString(R.string.add_person) : old);
    }

    void savePeople(List<String> l) { put("people", String.join("\n", l)); }

    String onOff(boolean on) { return getString(on ? R.string.on : R.string.off); }

    TextView row(String label, String value, Runnable onOk) {
        TextView r = text(1.6f, 500, 0xFFF5F5F7);
        r.setText(value.isEmpty() ? label : label + "  " + value);
        r.setTag(label);
        r.setPadding((int) (2 * u), (int) (1.1f * u), (int) (2 * u), (int) (1.1f * u));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(1.2f * u);
        g.setColor(0x14FFFFFF);
        r.setBackground(g);
        r.setFocusable(true);
        r.setOnFocusChangeListener((v, has) -> {
            g.setColor(has ? 0xFFF5F5F7 : 0x14FFFFFF);
            r.setTextColor(has ? 0xFF111114 : 0xFFF5F5F7);
        });
        r.setOnClickListener(v -> { onOk.run(); rebuildFocusing(label); });
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

    void title(int s) {
        TextView title = text(3.2f, 700, Color.WHITE);
        title.setText(s);
        title.setLetterSpacing(-0.03f);
        list.addView(title);
    }

    void header(String s) {
        TextView h = text(1.05f, 600, 0xFF8E8E93);
        h.setText(s);
        h.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (2.6f * u);
        lp.bottomMargin = (int) (0.4f * u);
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
