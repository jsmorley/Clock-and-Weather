package com.jsmorley.clockweather;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class WeatherApi {
    record Place(String name, double latitude, double longitude) {
        @Override public String toString() { return name; }
    }
    record Current(double celsius, int code, boolean isDay) {}

    static List<Place> find(String query) throws Exception {
        JSONArray results = get("https://geocoding-api.open-meteo.com/v1/search?count=10&name="
                + URLEncoder.encode(query, StandardCharsets.UTF_8.name())).optJSONArray("results");
        List<Place> places = new ArrayList<>();
        if (results == null) return places;
        for (int i = 0; i < results.length(); i++) {
            JSONObject result = results.getJSONObject(i);
            String name = result.getString("name");
            String region = result.optString("admin1");
            String country = result.optString("country");
            if (!region.isEmpty() && !region.equals(name)) name += ", " + region;
            if (!country.isEmpty()) name += ", " + country;
            places.add(new Place(name, result.getDouble("latitude"), result.getDouble("longitude")));
        }
        return places;
    }

    static Current current(double latitude, double longitude) throws Exception {
        JSONObject current = get("https://api.open-meteo.com/v1/forecast?latitude=" + latitude
                + "&longitude=" + longitude + "&current=temperature_2m,weather_code,is_day").getJSONObject("current");
        return new Current(current.getDouble("temperature_2m"), current.getInt("weather_code"),
                current.optInt("is_day", 1) == 1);
    }

    private static JSONObject get(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        try (InputStream input = connection.getInputStream()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) {
                if (output.size() + length > 128 * 1024) throw new java.io.IOException("Response too large");
                output.write(buffer, 0, length);
            }
            return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    static String condition(int code) {
        return switch (code) {
            case 0 -> "Clear";
            case 1 -> "Mostly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75, 77 -> "Snow";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
            default -> "Unknown";
        };
    }
}
