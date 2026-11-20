package com.arda.marquee;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/** Open-Meteo (no API key). Blocking calls: run them off the main thread. */
final class Weather {
    static String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096];
            for (int n; (n = in.read(b)) > 0; ) o.write(b, 0, n);
            return o.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    static String plain(String s) {
        return java.text.Normalizer.normalize(s.trim().toLowerCase(java.util.Locale.ROOT).replace('ı', 'i'), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** {name, lat, lon} of the best match for a city name, or null. */
    static String[] find(String q) throws Exception {
        JSONArray r = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=tr&name=" + URLEncoder.encode(q, "UTF-8"))).optJSONArray("results");
        if (r == null || r.length() == 0) return null;
        JSONObject o = r.getJSONObject(0);
        // a district search can return its centre town under another name; show the district the user asked for
        String name = o.getString("name"), d = o.optString("admin2");
        if (!d.isEmpty() && plain(d).startsWith(plain(q))) name = d;
        return new String[]{name, String.valueOf(o.getDouble("latitude")), String.valueOf(o.getDouble("longitude"))};
    }

    /** Current weather as text, like "21°  ⛅". */
    static String now(String lat, String lon) throws Exception {
        JSONObject c = new JSONObject(get("https://api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code&latitude=" + lat + "&longitude=" + lon)).getJSONObject("current");
        return Math.round(c.getDouble("temperature_2m")) + "°  " + glyph(c.getInt("weather_code"));
    }

    /** WMO weather code to a unicode symbol. */
    static String glyph(int c) {
        if (c == 0) return "☀";
        if (c <= 2) return "⛅";
        if (c == 3) return "☁";
        if (c == 45 || c == 48) return "🌫";
        if (c >= 95) return "⛈";
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return "❄";
        return "🌧"; // drizzle, rain, showers
    }
}
