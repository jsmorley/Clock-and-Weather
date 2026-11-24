package com.jsmorley.clockweather;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class WeatherJob extends JobService {
    private static final long MIN_REFRESH_AGE = 25 * 60 * 1000L;
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<JobParameters, Future<?>> active = new HashMap<>();

    static void request(Context context, int id) {
        SharedPreferences settings = WidgetSettings.forWidget(context, id);
        long age = System.currentTimeMillis() - settings.getLong("fetched", 0);
        if (!settings.contains("place") || (age >= 0 && age < MIN_REFRESH_AGE)) return;
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler.getPendingJob(id) == null) {
            JobInfo job = new JobInfo.Builder(id, new ComponentName(context, WeatherJob.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setBackoffCriteria(60_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                    .build();
            scheduler.schedule(job);
        }
    }

    @Override public boolean onStartJob(JobParameters params) {
        int id = params.getJobId();
        SharedPreferences settings = WidgetSettings.forWidget(this, id);
        String location = settings.getString("locationKey", null);
        if (location == null) return false;
        double latitude = Double.longBitsToDouble(settings.getLong("latitude", 0));
        double longitude = Double.longBitsToDouble(settings.getLong("longitude", 0));
        active.put(params, workers.submit(() -> {
            WeatherApi.Current current;
            try {
                current = WeatherApi.current(latitude, longitude);
            } catch (Exception exception) {
                Log.w("ClockWeather", "Weather refresh failed", exception);
                current = null;
            }
            WeatherApi.Current result = current;
            main.post(() -> finish(params, location, result));
        }));
        return true;
    }

    private void finish(JobParameters params, String location, WeatherApi.Current result) {
        if (active.remove(params) == null) return;
        int id = params.getJobId();
        SharedPreferences settings = WidgetSettings.forWidget(this, id);
        boolean sameLocation = location.equals(settings.getString("locationKey", null));
        if (sameLocation) {
            SharedPreferences.Editor edit = settings.edit().putBoolean("weatherError", result == null);
            if (result != null) {
                edit.putFloat("temperature", (float) result.celsius()).putInt("code", result.code())
                        .putBoolean("isDay", result.isDay())
                        .putLong("fetched", System.currentTimeMillis());
            }
            edit.apply();
            ClockWidget.render(this, id);
        }
        jobFinished(params, sameLocation && result == null);
    }

    @Override public boolean onStopJob(JobParameters params) {
        Future<?> work = active.remove(params);
        if (work != null) work.cancel(true);
        return true;
    }

    @Override public void onDestroy() {
        active.clear();
        workers.shutdownNow();
        super.onDestroy();
    }
}
