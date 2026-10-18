package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherManager {

    private static final String TAG = "TvHomeWeather";
    private static final String PREF_NAME = "weather_cache";

    public static class WeatherData {
        public String temperature = "";
        public String condition = "";
        public String sunrise = "";
        public String sunset = "";
        public String navakarsi = "";
        public String city = "";
        public long sunriseMillis = 0;
        public long sunsetMillis = 0;

        public boolean isValid() {
            return !sunrise.isEmpty() && !sunset.isEmpty() && sunriseMillis > 0 && sunsetMillis > 0;
        }
    }

    public interface WeatherCallback {
        void onWeatherUpdated(WeatherData data);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void load(Context context, WeatherCallback callback) {
        // 1. Immediately return cached data if present
        WeatherData cached = getCachedData(context);
        if (cached.isValid()) {
            callback.onWeatherUpdated(cached);
        } else {
            // Compute astronomical values immediately so there is never an empty screen
            WeatherData initial = computeAstronomicalSolar(12.9753, 77.591, "Bengaluru");
            callback.onWeatherUpdated(initial);
        }

        // 2. Fetch fresh weather & solar times in background
        executor.execute(() -> {
            try {
                // Get lat/lon (defaults to Bengaluru / Airtel network IP)
                double lat = 12.9753;
                double lon = 77.591;
                String city = "Bengaluru";

                try {
                    String ipResp = httpGet("http://ip-api.com/json");
                    JSONObject ipJson = new JSONObject(ipResp);
                    if ("success".equalsIgnoreCase(ipJson.optString("status"))) {
                        lat = ipJson.optDouble("lat", lat);
                        lon = ipJson.optDouble("lon", lon);
                        city = ipJson.optString("city", city);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "IP location lookup fallback to default: " + e.getMessage());
                }

                String apiUrl = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&daily=sunrise,sunset&current_weather=true&timezone=auto",
                        lat, lon);
                String meteoResp = httpGet(apiUrl);
                JSONObject root = new JSONObject(meteoResp);

                WeatherData fresh = new WeatherData();
                fresh.city = city;

                JSONObject cw = root.optJSONObject("current_weather");
                if (cw != null) {
                    double temp = cw.optDouble("temperature", Double.NaN);
                    if (!Double.isNaN(temp)) {
                        fresh.temperature = Math.round(temp) + "°C";
                    }
                    int code = cw.optInt("weathercode", -1);
                    fresh.condition = describeWmoCode(code);
                }

                JSONObject daily = root.optJSONObject("daily");
                if (daily != null) {
                    JSONArray sunrises = daily.optJSONArray("sunrise");
                    JSONArray sunsets = daily.optJSONArray("sunset");

                    if (sunrises != null && sunrises.length() > 0 && sunsets != null && sunsets.length() > 0) {
                        String rawSunrise = sunrises.getString(0); // e.g. 2026-10-18T06:10
                        String rawSunset = sunsets.getString(0);   // e.g. 2026-10-18T17:59

                        SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
                        SimpleDateFormat dispFmt = new SimpleDateFormat("h:mm a", Locale.US);

                        Date riseDate = isoFmt.parse(rawSunrise);
                        Date setDate = isoFmt.parse(rawSunset);

                        if (riseDate != null) {
                            fresh.sunriseMillis = riseDate.getTime();
                            fresh.sunrise = dispFmt.format(riseDate);

                            // Navakarsi = Sunrise + 48 minutes
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(riseDate);
                            cal.add(Calendar.MINUTE, 48);
                            fresh.navakarsi = dispFmt.format(cal.getTime());
                        }

                        if (setDate != null) {
                            fresh.sunsetMillis = setDate.getTime();
                            fresh.sunset = dispFmt.format(setDate);
                        }
                    }
                }

                if (!fresh.isValid()) {
                    WeatherData astro = computeAstronomicalSolar(lat, lon, city);
                    fresh.sunrise = astro.sunrise;
                    fresh.sunset = astro.sunset;
                    fresh.navakarsi = astro.navakarsi;
                    fresh.sunriseMillis = astro.sunriseMillis;
                    fresh.sunsetMillis = astro.sunsetMillis;
                }

                saveCache(context, fresh);

                mainHandler.post(() -> callback.onWeatherUpdated(fresh));

            } catch (Exception e) {
                Log.e(TAG, "Error fetching weather data", e);
                WeatherData fallback = computeAstronomicalSolar(12.9753, 77.591, "Bengaluru");
                mainHandler.post(() -> callback.onWeatherUpdated(fallback));
            }
        });
    }

    private static String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "tv-vibehack/1.0");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private static String describeWmoCode(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mainly Clear";
            case 2: return "Partly Cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Foggy";
            case 51: case 53: case 55: return "Drizzle";
            case 61: case 63: case 65: return "Rain";
            case 80: case 81: case 82: return "Showers";
            case 95: case 96: case 99: return "Thunderstorm";
            default: return "";
        }
    }

    /**
     * Standard NOAA Solar calculator for exact offline calculation of Sunrise, Sunset, and Navakarsi.
     */
    public static WeatherData computeAstronomicalSolar(double lat, double lon, String city) {
        WeatherData data = new WeatherData();
        data.city = city;
        try {
            Calendar cal = Calendar.getInstance();
            int dayOfYear = cal.get(Calendar.DAY_OF_YEAR);
            double tzOffsetHours = (double) TimeZone.getDefault().getOffset(System.currentTimeMillis()) / (1000 * 3600);

            double gamma = (2 * Math.PI / 365.0) * (dayOfYear - 1);
            double eqtime = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma) - 0.032077 * Math.sin(gamma)
                    - 0.014615 * Math.cos(2 * gamma) - 0.040849 * Math.sin(2 * gamma));
            double decl = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma)
                    - 0.006758 * Math.cos(2 * gamma) + 0.000907 * Math.sin(2 * gamma)
                    - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma);

            double latRad = Math.toRadians(lat);
            double zenithRad = Math.toRadians(90.833); // official atmospheric refraction zenith

            double cosHa = (Math.cos(zenithRad) / (Math.cos(latRad) * Math.cos(decl))) - (Math.tan(latRad) * Math.tan(decl));
            cosHa = Math.max(-1.0, Math.min(1.0, cosHa));
            double ha = Math.toDegrees(Math.acos(cosHa));

            double sunriseMinutes = 720 - 4 * (lon + ha) - eqtime + (tzOffsetHours * 60);
            double sunsetMinutes = 720 - 4 * (lon - ha) - eqtime + (tzOffsetHours * 60);

            int srH = (int) (sunriseMinutes / 60) % 24;
            int srM = (int) (sunriseMinutes % 60);
            int ssH = (int) (sunsetMinutes / 60) % 24;
            int ssM = (int) (sunsetMinutes % 60);

            SimpleDateFormat dispFmt = new SimpleDateFormat("h:mm a", Locale.US);
            Calendar cRise = Calendar.getInstance();
            cRise.set(Calendar.HOUR_OF_DAY, srH);
            cRise.set(Calendar.MINUTE, srM);
            cRise.set(Calendar.SECOND, 0);
            cRise.set(Calendar.MILLISECOND, 0);
            data.sunriseMillis = cRise.getTimeInMillis();
            data.sunrise = dispFmt.format(cRise.getTime());

            // Navakarsi = Sunrise + 48 minutes
            cRise.add(Calendar.MINUTE, 48);
            data.navakarsi = dispFmt.format(cRise.getTime());

            Calendar cSet = Calendar.getInstance();
            cSet.set(Calendar.HOUR_OF_DAY, ssH);
            cSet.set(Calendar.MINUTE, ssM);
            cSet.set(Calendar.SECOND, 0);
            cSet.set(Calendar.MILLISECOND, 0);
            data.sunsetMillis = cSet.getTimeInMillis();
            data.sunset = dispFmt.format(cSet.getTime());

        } catch (Exception e) {
            Log.e(TAG, "Solar math error", e);
        }
        return data;
    }

    private static WeatherData getCachedData(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        WeatherData d = new WeatherData();
        d.temperature = sp.getString("temp", "");
        d.condition = sp.getString("cond", "");
        d.sunrise = sp.getString("sunrise", "");
        d.sunset = sp.getString("sunset", "");
        d.navakarsi = sp.getString("navakarsi", "");
        d.city = sp.getString("city", "");
        d.sunriseMillis = sp.getLong("sunrise_ms", 0);
        d.sunsetMillis = sp.getLong("sunset_ms", 0);
        return d;
    }

    private static void saveCache(Context context, WeatherData d) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("temp", d.temperature)
                .putString("cond", d.condition)
                .putString("sunrise", d.sunrise)
                .putString("sunset", d.sunset)
                .putString("navakarsi", d.navakarsi)
                .putString("city", d.city)
                .putLong("sunrise_ms", d.sunriseMillis)
                .putLong("sunset_ms", d.sunsetMillis)
                .apply();
    }
}
