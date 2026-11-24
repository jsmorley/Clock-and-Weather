package com.jsmorley.clockweather;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

final class WidgetSettings {
    static final String[] ROWS = {"Time", "Date", "Weather"};
    static final int[] DEFAULT_SIZES = {195, 30, 30};
    static final int[] MIN_SIZES = {32, 10, 10};
    static final int[] MAX_SIZES = {220, 36, 36};
    static final String DEFAULT_WEATHER_APP = "com.weather.Weather";

    static SharedPreferences forWidget(Context context, int id) {
        return context.getSharedPreferences("widget_" + id, Context.MODE_PRIVATE);
    }

    static int size(SharedPreferences settings, int row) {
        return settings.getInt("size" + row, DEFAULT_SIZES[row]);
    }

    static int color(SharedPreferences settings, int row) {
        return settings.getInt("color" + row, Color.WHITE);
    }

    static WidgetFont font(SharedPreferences settings, int row) {
        return WidgetFont.forFamily(settings.getString("font" + row, null));
    }

    static boolean automaticColor(SharedPreferences settings, int row) {
        return settings.getBoolean("autoColor" + row, !settings.contains("color" + row));
    }

    static boolean hasAutomaticColor(SharedPreferences settings) {
        for (int row = 0; row < ROWS.length; row++) {
            if (automaticColor(settings, row)) return true;
        }
        return false;
    }

    static String weather(SharedPreferences settings) {
        if (!settings.contains("place")) return "Choose a weather location";
        if (!settings.contains("temperature")) {
            return settings.getBoolean("weatherError", false) ? "Weather unavailable" : "Loading weather…";
        }
        if (!hasCurrentWeather(settings)) return "Weather unavailable";
        double celsius = settings.getFloat("temperature", 0);
        boolean fahrenheit = settings.getBoolean("fahrenheit", true);
        long temperature = Math.round(fahrenheit ? celsius * 1.8 + 32 : celsius);
        return temperature + (fahrenheit ? "°F" : "°C") + " ("
                + WeatherApi.condition(settings.getInt("code", -1)) + ")";
    }

    static boolean hasCurrentWeather(SharedPreferences settings) {
        return settings.contains("temperature")
                && System.currentTimeMillis() - settings.getLong("fetched", 0) <= 2 * 60 * 60 * 1000L;
    }
}
