package com.jsmorley.clockweather;

import android.app.PendingIntent;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RemoteViews;

public final class ClockWidget extends AppWidgetProvider {
    static final String TIME_PATTERN = "h:mm";
    static final String DATE_PATTERN = "EEEE MMMM d";
    static final int[] VIEWS = {R.id.widget_time, R.id.widget_date, R.id.widget_weather};

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        boolean needsWallpaper = false;
        for (int id : ids) {
            render(context, id);
            WeatherJob.request(context, id);
            needsWallpaper |= WidgetSettings.hasAutomaticColor(WidgetSettings.forWidget(context, id));
        }
        if (needsWallpaper) WallpaperContrast.refresh(context, null);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
            int id, Bundle options) {
        render(context, id);
    }

    @Override public void onDeleted(Context context, int[] ids) {
        for (int id : ids) {
            context.getSystemService(JobScheduler.class).cancel(id);
            context.deleteSharedPreferences("widget_" + id);
        }
    }

    static void render(Context context, int id) {
        SharedPreferences settings = WidgetSettings.forWidget(context, id);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        Bundle options = manager.getAppWidgetOptions(id);
        String weather = WidgetSettings.weather(settings);
        int weatherCode = settings.getInt("code", -1);
        boolean showIcon = weatherCode >= 0 && WidgetSettings.hasCurrentWeather(settings);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
        WidgetFont timeFont = WidgetSettings.font(settings, 0);
        WidgetFont dateFont = WidgetSettings.font(settings, 1);
        WidgetFont weatherFont = WidgetSettings.font(settings, 2);
        WidgetFont[] fonts = {timeFont, dateFont, weatherFont};
        CharSequence[] patterns = {timeFont.style(TIME_PATTERN), dateFont.style(DATE_PATTERN)};
        for (int row = 0; row < patterns.length; row++) {
            views.setCharSequence(VIEWS[row], "setFormat12Hour", patterns[row]);
            views.setCharSequence(VIEWS[row], "setFormat24Hour", patterns[row]);
        }
        views.setTextViewText(R.id.widget_weather, weatherFont.style(weather));

        float density = context.getResources().getDisplayMetrics().density;
        float width = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300) - 6) * density;
        float height = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160) - 6) * density;
        String[] samples = {"", "Wednesday September 30", weather};
        float[] sizes = new float[VIEWS.length];
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float textHeight = 0;
        float clockHeight = 0;
        for (int row = 0; row < VIEWS.length; row++) {
            float pixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                    WidgetSettings.size(settings, row), context.getResources().getDisplayMetrics());
            paint.setTypeface(fonts[row].typeface());
            paint.setTextSize(pixels);
            float available = width;
            float requested = paint.measureText(samples[row]);
            if (row == 2 && showIcon) {
                available -= 4 * density;
                requested += pixels;
            }
            if (row != 0) pixels *= Math.min(1, Math.max(1, available) / requested);
            sizes[row] = pixels;
            paint.setTextSize(pixels);
            float lineHeight = paint.getFontMetrics().descent - paint.getFontMetrics().ascent;
            if (row == 0) clockHeight = lineHeight;
            textHeight += lineHeight;
        }
        paint.setTypeface(timeFont.typeface());
        paint.setTextSize(sizes[0]);
        // Reuse space below the digits without overlapping the selected font's glyphs.
        Rect digitBounds = new Rect();
        String digits = "0123456789:";
        paint.getTextBounds(digits, 0, digits.length(), digitBounds);
        float clockOverlap = Math.max(0, paint.getFontMetrics().descent - digitBounds.bottom) * 0.84f
                * Math.min(1, Math.max(1, width) / paint.measureText("88:88"));
        float scale = Math.min(1, Math.max(1, height) / (textHeight - clockOverlap));
        views.setViewLayoutHeight(R.id.time_box, (float) Math.ceil(clockHeight * scale),
                TypedValue.COMPLEX_UNIT_PX);
        views.setViewLayoutMargin(R.id.widget_date, RemoteViews.MARGIN_TOP, -clockOverlap * scale,
                TypedValue.COMPLEX_UNIT_PX);
        int wallpaperColor = WidgetSettings.hasAutomaticColor(settings)
                ? WallpaperContrast.current(context) : android.graphics.Color.WHITE;
        int weatherColor = WidgetSettings.automaticColor(settings, 2)
                ? wallpaperColor : WidgetSettings.color(settings, 2);
        for (int row = 0; row < VIEWS.length; row++) {
            if (row != 0) views.setTextViewTextSize(VIEWS[row], TypedValue.COMPLEX_UNIT_PX, sizes[row] * scale);
            views.setTextColor(VIEWS[row], WidgetSettings.automaticColor(settings, row)
                    ? wallpaperColor : WidgetSettings.color(settings, row));
        }
        views.setViewVisibility(R.id.weather_icon, showIcon ? android.view.View.VISIBLE : android.view.View.GONE);
        if (showIcon) {
            int iconSize = Math.max(1, Math.round(sizes[2] * scale));
            views.setViewLayoutWidth(R.id.weather_icon, iconSize, TypedValue.COMPLEX_UNIT_PX);
            views.setViewLayoutHeight(R.id.weather_icon, iconSize, TypedValue.COMPLEX_UNIT_PX);
            views.setImageViewBitmap(R.id.weather_icon, WeatherIcon.draw(weatherCode,
                    settings.getBoolean("isDay", true), weatherColor, iconSize));
        }
        Intent intent = new Intent(context, SettingsActivity.class)
                .setAction(SettingsActivity.EDIT_WIDGET)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, id, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        String weatherApp = settings.getString("weatherApp", WidgetSettings.DEFAULT_WEATHER_APP);
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(weatherApp);
        if (launch == null) launch = intent;
        views.setOnClickPendingIntent(R.id.weather_row, PendingIntent.getActivity(context, ~id, launch,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        manager.updateAppWidget(id, views);
    }
}
