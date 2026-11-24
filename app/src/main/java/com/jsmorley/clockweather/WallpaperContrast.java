package com.jsmorley.clockweather;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.IntConsumer;

final class WallpaperContrast {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService READS = Executors.newSingleThreadExecutor();
    private static final String PREFS = "wallpaper";
    private static final String COLOR = "foreground";
    private static WallpaperManager wallpaper;
    private static int foreground = Color.WHITE;
    private static int changes;

    static int current(Context context) {
        if (wallpaper == null) {
            Context app = context.getApplicationContext();
            foreground = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(COLOR, Color.WHITE);
            wallpaper = WallpaperManager.getInstance(app);
            wallpaper.addOnColorsChangedListener((colors, which) -> {
                if ((which & WallpaperManager.FLAG_SYSTEM) != 0) {
                    changes++;
                    apply(app, colors);
                }
            }, MAIN);
        }
        return foreground;
    }

    static void refresh(Context context, IntConsumer complete) {
        Context app = context.getApplicationContext();
        current(app);
        int generation = changes;
        READS.execute(() -> {
            WallpaperColors colors = wallpaper.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            MAIN.post(() -> {
                if (generation == changes) apply(app, colors);
                if (complete != null) complete.accept(foreground);
            });
        });
    }

    private static void apply(Context app, WallpaperColors colors) {
        if (colors == null) return;
        int color = foregroundForHints(colors.getColorHints());
        if (color == foreground) return;
        foreground = color;
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(COLOR, color).apply();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        for (int id : manager.getAppWidgetIds(new ComponentName(app, ClockWidget.class))) {
            if (WidgetSettings.hasAutomaticColor(WidgetSettings.forWidget(app, id))) {
                ClockWidget.render(app, id);
            }
        }
    }

    static int foregroundForHints(int hints) {
        return (hints & WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0 ? Color.BLACK : Color.WHITE;
    }
}
