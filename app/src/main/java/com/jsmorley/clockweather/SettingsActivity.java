package com.jsmorley.clockweather;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.location.Location;
import android.location.LocationRequest;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SettingsActivity extends Activity {
    static final String EDIT_WIDGET = "com.jsmorley.clockweather.EDIT_WIDGET";
    private static final int LOCATION_REQUEST = 1;
    private static final int BACKGROUND = 0xff10171d;
    private static final int PANEL = 0xff1d2932;
    private static final int ACCENT = 0xff9be2d5;
    private static final int SUBTLE = 0xffc2ced4;

    private final ExecutorService searches = Executors.newSingleThreadExecutor();
    private final TextView[] preview = new TextView[3];
    private ImageView previewWeatherIcon;
    private final int[] sizes = new int[3];
    private final int[] colors = new int[3];
    private final WidgetFont[] fonts = new WidgetFont[3];
    private final boolean[] automaticColors = new boolean[3];
    private final Button[] colorButtons = new Button[3];
    private LinearLayout previewPanel;
    private TextView previewLabel;
    private int wallpaperColor;
    private int id;
    private SharedPreferences settings;
    private WeatherApi.Place selectedPlace;
    private boolean fahrenheit;
    private EditText search;
    private Button searchButton;
    private Button locationButton;
    private List<CancellationSignal> locationRequest;
    private String weatherApp;
    private Button appButton;
    private TextView placeLabel;
    private TextView status;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        setResult(RESULT_CANCELED, resultIntent());
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
        if (info == null && Intent.ACTION_MAIN.equals(getIntent().getAction())) {
            int[] ids = manager.getAppWidgetIds(new android.content.ComponentName(this, ClockWidget.class));
            if (ids.length == 1) {
                id = ids[0];
                info = manager.getAppWidgetInfo(id);
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.setFillViewport(true);
        LinearLayout page = column();
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            page.setPadding(dp(20) + safe.left, dp(16) + safe.top, dp(20) + safe.right, dp(24) + safe.bottom);
            return insets;
        });
        page.setFocusableInTouchMode(true);
        scroll.addView(page);
        setContentView(scroll);
        add(page, label("Clock & Weather", 28, Color.WHITE), 0);

        if (info == null || !ClockWidget.class.getName().equals(info.provider.getClassName())) {
            add(page, label("Touch and hold an empty spot on the Home screen. Open Widgets, find "
                    + "Clock & Weather, then add it. Tap a widget to edit its settings.", 16, SUBTLE), 14);
            return;
        }

        settings = WidgetSettings.forWidget(this, id);
        wallpaperColor = WallpaperContrast.current(this);
        weatherApp = saved == null ? settings.getString("weatherApp", WidgetSettings.DEFAULT_WEATHER_APP)
                : saved.getString("weatherApp", WidgetSettings.DEFAULT_WEATHER_APP);
        fahrenheit = saved == null ? settings.getBoolean("fahrenheit", true) : saved.getBoolean("fahrenheit");
        if (saved != null && saved.containsKey("place")) {
            selectedPlace = new WeatherApi.Place(saved.getString("place"), saved.getDouble("latitude"),
                    saved.getDouble("longitude"));
        }

        previewPanel = panel();
        add(page, previewPanel, 14);
        previewLabel = label("Preview", 13, SUBTLE);
        add(previewPanel, previewLabel, 0);
        preview[0] = clock(ClockWidget.TIME_PATTERN);
        preview[1] = clock(ClockWidget.DATE_PATTERN);
        preview[2] = label("", 18, Color.WHITE);
        String[] savedFonts = saved == null ? null : saved.getStringArray("fonts");
        for (int row = 0; row < preview.length; row++) {
            sizes[row] = saved == null ? WidgetSettings.size(settings, row) : saved.getIntArray("sizes")[row];
            colors[row] = saved == null ? WidgetSettings.color(settings, row) : saved.getIntArray("colors")[row];
            boolean[] savedModes = saved == null ? null : saved.getBooleanArray("automaticColors");
            automaticColors[row] = savedModes == null ? WidgetSettings.automaticColor(settings, row) : savedModes[row];
            fonts[row] = savedFonts == null ? WidgetSettings.font(settings, row) : WidgetFont.forFamily(savedFonts[row]);
            preview[row].setTypeface(fonts[row].typeface());
            preview[row].setGravity(Gravity.CENTER);
            preview[row].setMaxLines(1);
            preview[row].setIncludeFontPadding(false);
            preview[row].setTextSize(sizes[row]);
            preview[row].setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (row == 2) {
                LinearLayout weatherPreview = new LinearLayout(this);
                weatherPreview.setGravity(Gravity.CENTER);
                previewWeatherIcon = new ImageView(this);
                previewWeatherIcon.setImportantForAccessibility(ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO);
                weatherPreview.addView(previewWeatherIcon);
                weatherPreview.addView(preview[row], new LinearLayout.LayoutParams(-2, -2));
                add(previewPanel, weatherPreview, 0);
            } else {
                add(previewPanel, preview[row], 0);
            }
        }

        LinearLayout appearance = panel();
        add(page, appearance, 14);
        for (int row = 0; row < preview.length; row++) addStyleEditor(appearance, row);

        LinearLayout weather = panel();
        add(page, weather, 14);
        add(weather, label("Weather location", 18, Color.WHITE), 0);
        placeLabel = label("", 15, SUBTLE);
        add(weather, placeLabel, 6);
        locationButton = button("Use current location", this::useCurrentLocation);
        add(weather, locationButton, 6);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("City or ZIP code");
        if (saved != null) search.setText(saved.getString("query", ""));
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            findPlaces();
            return true;
        });
        searchButton = button("Search", this::findPlaces);
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        searchRow.addView(searchButton);
        add(weather, searchRow, 6);
        status = label("", 14, SUBTLE);
        status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(weather, status, 0);

        RadioGroup units = new RadioGroup(this);
        units.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton f = unitButton("°F", units);
        RadioButton c = unitButton("°C", units);
        units.check(fahrenheit ? f.getId() : c.getId());
        units.setOnCheckedChangeListener((group, checked) -> {
            fahrenheit = checked == f.getId();
            updateWeatherPreview();
        });
        add(weather, units, 2);
        add(weather, label("App opened by weather tap", 15, SUBTLE), 8);
        appButton = button(weatherAppLabel(), this::chooseWeatherApp);
        add(weather, appButton, 0);
        TextView credit = label("Weather data by Open-Meteo", 13, ACCENT);
        credit.setOnClickListener(view -> startActivity(new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://open-meteo.com/"))));
        add(weather, credit, 6);
        updateColorPreview();
        WallpaperContrast.refresh(this, color -> {
            if (isFinishing() || isDestroyed()) return;
            wallpaperColor = color;
            updateColorPreview();
        });

        Button save = button(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(getIntent().getAction())
                && !settings.contains("size0") ? "Add widget" : "Save changes", this::save);
        save.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        save.setTextColor(BACKGROUND);
        add(page, save, 14);
    }

    private void addStyleEditor(LinearLayout parent, int row) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label("", 15, Color.WHITE);
        line.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        heading.setText(WidgetSettings.ROWS[row] + "  ·  " + sizes[row] + " sp");
        Button font = new Button(this);
        font.setAllCaps(false);
        font.setMaxWidth(dp(148));
        font.setSingleLine(true);
        font.setEllipsize(android.text.TextUtils.TruncateAt.END);
        font.setOnClickListener(view -> chooseFont(row, font));
        updateFontButton(row, font);
        line.addView(font);
        Button color = button("", () -> chooseColor(row));
        colorButtons[row] = color;
        line.addView(color);
        add(parent, line, row == 0 ? 0 : 6);

        SeekBar slider = new SeekBar(this);
        slider.setContentDescription(WidgetSettings.ROWS[row] + " font size");
        slider.setMax(WidgetSettings.MAX_SIZES[row] - WidgetSettings.MIN_SIZES[row]);
        slider.setProgress(sizes[row] - WidgetSettings.MIN_SIZES[row]);
        slider.setProgressTintList(ColorStateList.valueOf(ACCENT));
        slider.setThumbTintList(ColorStateList.valueOf(ACCENT));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                sizes[row] = progress + WidgetSettings.MIN_SIZES[row];
                heading.setText(WidgetSettings.ROWS[row] + "  ·  " + sizes[row] + " sp");
                preview[row].setTextSize(sizes[row]);
                if (row == 2) updateWeatherPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        add(parent, slider, 2);
    }

    private void chooseFont(int row, Button button) {
        List<WidgetFont> choices = WidgetFont.available();
        int selectedFont = choices.indexOf(fonts[row]);
        ArrayAdapter<WidgetFont> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_single_choice, choices) {
            @Override public android.view.View getView(int position, android.view.View convertView,
                    android.view.ViewGroup parent) {
                TextView label = (TextView) super.getView(position, convertView, parent);
                label.setTypeface(choices.get(position).typeface());
                return label;
            }
        };
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.row_font_title, WidgetSettings.ROWS[row]))
                .setSingleChoiceItems(adapter, selectedFont, (dialog, selected) -> {
                    fonts[row] = choices.get(selected);
                    preview[row].setTypeface(fonts[row].typeface());
                    updateFontButton(row, button);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateFontButton(int row, Button button) {
        button.setText(fonts[row].label);
        button.setContentDescription(getString(R.string.row_font_choice, WidgetSettings.ROWS[row], fonts[row].label));
    }

    private void chooseColor(int row) {
        LinearLayout content = column();
        content.setPadding(dp(18), 0, dp(18), 0);
        add(content, label(getString(R.string.auto_color_explanation), 15, SUBTLE), 6);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("#RRGGBB");
        input.setText(hex(colors[row]));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.selectAll();
        add(content, input, 6);
        LinearLayout swatches = new LinearLayout(this);
        String[] names = {"White", "Cream", "Gold", "Rose", "Mint", "Blue"};
        String[] palette = {"#FFFFFF", "#FFF2D6", "#FDD574", "#FF99AD", "#9BE2D5", "#A8D3FF"};
        for (int i = 0; i < palette.length; i++) {
            String value = palette[i];
            Button swatch = new Button(this);
            swatch.setContentDescription(names[i]);
            swatch.setMinWidth(0);
            swatch.setMinimumWidth(0);
            swatch.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(value)));
            swatch.setOnClickListener(view -> input.setText(value));
            swatches.addView(swatch, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        add(content, swatches, 0);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(WidgetSettings.ROWS[row] + " color")
                .setView(content).setNegativeButton("Cancel", null).setPositiveButton("Apply", null)
                .setNeutralButton(R.string.auto_color, (ignored, which) -> {
                    automaticColors[row] = true;
                    updateColorPreview();
                }).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = input.getText().toString().trim();
            if (!value.matches("#?[0-9a-fA-F]{6}")) {
                input.setError("Enter six hex digits, such as #FFFFFF");
                return;
            }
            colors[row] = Color.parseColor(value.startsWith("#") ? value : "#" + value);
            automaticColors[row] = false;
            updateColorPreview();
            dialog.dismiss();
        });
    }

    private int previewColor(int row) {
        return automaticColors[row] ? wallpaperColor : colors[row];
    }

    private void updateColorPreview() {
        boolean useLightBackground = false;
        for (int row = 0; row < preview.length; row++) {
            preview[row].setTextColor(previewColor(row));
            String mode = automaticColors[row] ? getString(R.string.auto_color) : hex(colors[row]);
            colorButtons[row].setText(mode);
            colorButtons[row].setContentDescription(getString(R.string.row_color_mode, WidgetSettings.ROWS[row], mode));
            useLightBackground |= automaticColors[row] && wallpaperColor == Color.BLACK;
        }
        ((GradientDrawable) previewPanel.getBackground()).setColor(useLightBackground ? 0xffedf0f2 : PANEL);
        previewLabel.setTextColor(useLightBackground ? BACKGROUND : SUBTLE);
        updateWeatherPreview();
    }

    private void findPlaces() {
        if (!searchButton.isEnabled() || locationRequest != null) return;
        String query = search.getText().toString().trim();
        if (query.length() < 2) {
            search.setError("Enter a city or ZIP code");
            return;
        }
        searchButton.setEnabled(false);
        locationButton.setEnabled(false);
        status.setText("Searching…");
        getSystemService(android.view.inputmethod.InputMethodManager.class)
                .hideSoftInputFromWindow(search.getWindowToken(), 0);
        searches.execute(() -> {
            try {
                List<WeatherApi.Place> places = WeatherApi.find(query);
                runOnUiThread(() -> showPlaces(places));
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    searchButton.setEnabled(true);
                    locationButton.setEnabled(true);
                    status.setText("Search unavailable. Try again.");
                });
            }
        });
    }

    private void showPlaces(List<WeatherApi.Place> places) {
        if (isFinishing() || isDestroyed()) return;
        searchButton.setEnabled(true);
        locationButton.setEnabled(true);
        status.setText(places.isEmpty() ? "No matching locations" : "");
        if (places.isEmpty()) return;
        CharSequence[] names = places.stream().map(WeatherApi.Place::name).toArray(CharSequence[]::new);
        new AlertDialog.Builder(this).setTitle("Choose location").setItems(names, (dialog, index) -> {
            selectedPlace = places.get(index);
            search.setText("");
            updateWeatherPreview();
        }).setNegativeButton("Cancel", null).show();
    }

    private void useCurrentLocation() {
        boolean precise = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!precise && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
            return;
        }
        LocationManager manager = getSystemService(LocationManager.class);
        List<String> providers = new ArrayList<>();
        for (String provider : List.of(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER)) {
            if (manager.isProviderEnabled(provider) && (precise || !LocationManager.GPS_PROVIDER.equals(provider))) {
                providers.add(provider);
            }
        }
        if (providers.isEmpty()) {
            status.setText("Turn on Location in Android settings or search for your city.");
            return;
        }
        List<CancellationSignal> signals = new ArrayList<>();
        locationRequest = signals;
        locationButton.setEnabled(false);
        searchButton.setEnabled(false);
        status.setText("Finding your location…");
        int[] remaining = {providers.size()};
        Location[] recent = {null};
        for (String provider : providers) {
            CancellationSignal signal = new CancellationSignal();
            signals.add(signal);
            try {
                Location cached = manager.getLastKnownLocation(provider);
                if (cached != null && SystemClock.elapsedRealtimeNanos() - cached.getElapsedRealtimeNanos() < 600_000_000_000L
                        && (recent[0] == null || cached.getElapsedRealtimeNanos() > recent[0].getElapsedRealtimeNanos())) {
                    recent[0] = cached;
                }
                int quality = LocationManager.GPS_PROVIDER.equals(provider) ? LocationRequest.QUALITY_HIGH_ACCURACY
                        : LocationRequest.QUALITY_BALANCED_POWER_ACCURACY;
                LocationRequest request = new LocationRequest.Builder(0).setQuality(quality)
                        .setDurationMillis(20_000).build();
                manager.getCurrentLocation(provider, request, signal, getMainExecutor(), location -> {
                    if (locationRequest != signals) return;
                    if (location != null || --remaining[0] == 0) {
                        finishLocationRequest(location != null ? location : recent[0]);
                    }
                });
            } catch (SecurityException | IllegalArgumentException exception) {
                if (--remaining[0] == 0) finishLocationRequest(recent[0]);
            }
        }
    }

    private void finishLocationRequest(Location location) {
        List<CancellationSignal> completed = locationRequest;
        locationRequest = null;
        completed.forEach(CancellationSignal::cancel);
        locationButton.setEnabled(true);
        searchButton.setEnabled(true);
        if (location == null) {
            status.setText("Location unavailable. Try again or search for your city.");
            return;
        }
        selectedPlace = new WeatherApi.Place("Current location",
                Math.round(location.getLatitude() * 100) / 100.0,
                Math.round(location.getLongitude() * 100) / 100.0);
        search.setText("");
        status.setText("");
        updateWeatherPreview();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request != LOCATION_REQUEST) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            useCurrentLocation();
        } else {
            status.setText("Allow location access or search for your city.");
        }
    }

    private void updateWeatherPreview() {
        placeLabel.setText(selectedPlace == null
                ? settings.getString("place", "No location selected") : selectedPlace.name());
        boolean current = selectedPlace == null && WidgetSettings.hasCurrentWeather(settings);
        if (current) {
            double celsius = settings.getFloat("temperature", 0);
            preview[2].setText(Math.round(fahrenheit ? celsius * 1.8 + 32 : celsius)
                    + (fahrenheit ? "°F" : "°C") + " (" + WeatherApi.condition(settings.getInt("code", -1)) + ")");
        } else {
            preview[2].setText(fahrenheit ? "42°F (Clear)" : "6°C (Clear)");
        }
        int iconSize = Math.max(1, Math.round(preview[2].getTextSize()));
        LinearLayout.LayoutParams iconLayout = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLayout.setMarginEnd(dp(4));
        previewWeatherIcon.setLayoutParams(iconLayout);
        previewWeatherIcon.setImageDrawable(new BitmapDrawable(getResources(), WeatherIcon.draw(
                current ? settings.getInt("code", 0) : 0, !current || settings.getBoolean("isDay", true),
                previewColor(2), iconSize)));
    }

    private String weatherAppLabel() {
        PackageManager manager = getPackageManager();
        try {
            return manager.getApplicationLabel(manager.getApplicationInfo(weatherApp, 0)).toString();
        } catch (PackageManager.NameNotFoundException exception) {
            return weatherApp.equals(WidgetSettings.DEFAULT_WEATHER_APP)
                    ? "The Weather Channel (unavailable)" : "Choose an installed app";
        }
    }

    private void chooseWeatherApp() {
        PackageManager manager = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = new ArrayList<>(manager.queryIntentActivities(launcher, 0));
        apps.sort(Comparator.comparing(app -> app.loadLabel(manager).toString(), String.CASE_INSENSITIVE_ORDER));
        List<String> names = new ArrayList<>();
        List<String> packages = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (ResolveInfo app : apps) {
            if (seen.add(app.activityInfo.packageName)) {
                packages.add(app.activityInfo.packageName);
                names.add(app.loadLabel(manager).toString());
            }
        }
        new AlertDialog.Builder(this).setTitle("Open app when weather is tapped")
                .setSingleChoiceItems(names.toArray(new String[0]), packages.indexOf(weatherApp), (dialog, index) -> {
                    weatherApp = packages.get(index);
                    appButton.setText(weatherAppLabel());
                    dialog.dismiss();
                }).setNegativeButton("Cancel", null).show();
    }

    private void save() {
        if (selectedPlace == null && !settings.contains("place")) {
            status.setText("Use your current location or search for a city before saving.");
            status.requestRectangleOnScreen(new android.graphics.Rect(0, 0, status.getWidth(), status.getHeight()), true);
            return;
        }

        SharedPreferences.Editor edit = settings.edit().putBoolean("fahrenheit", fahrenheit)
                .putString("weatherApp", weatherApp);
        for (int row = 0; row < preview.length; row++) {
            edit.putInt("size" + row, sizes[row]).putInt("color" + row, colors[row])
                    .putBoolean("autoColor" + row, automaticColors[row])
                    .putString("font" + row, fonts[row].family);
        }
        if (selectedPlace != null) {
            getSystemService(JobScheduler.class).cancel(id);
            edit.putString("place", selectedPlace.name()).putString("locationKey", UUID.randomUUID().toString())
                    .putLong("latitude", Double.doubleToLongBits(selectedPlace.latitude()))
                    .putLong("longitude", Double.doubleToLongBits(selectedPlace.longitude()))
                    .remove("temperature").remove("code").remove("isDay").remove("fetched").remove("weatherError");
        }
        edit.apply();
        ClockWidget.render(this, id);
        WeatherJob.request(this, id);
        setResult(RESULT_OK, resultIntent());
        finish();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (settings == null) return;
        String[] fontFamilies = new String[fonts.length];
        for (int row = 0; row < fonts.length; row++) fontFamilies[row] = fonts[row].family;
        state.putStringArray("fonts", fontFamilies);
        state.putIntArray("sizes", sizes);
        state.putIntArray("colors", colors);
        state.putBooleanArray("automaticColors", automaticColors);
        state.putBoolean("fahrenheit", fahrenheit);
        state.putString("weatherApp", weatherApp);
        state.putString("query", search.getText().toString());
        if (selectedPlace != null) {
            state.putString("place", selectedPlace.name());
            state.putDouble("latitude", selectedPlace.latitude());
            state.putDouble("longitude", selectedPlace.longitude());
        }
    }

    @Override protected void onDestroy() {
        if (locationRequest != null) locationRequest.forEach(CancellationSignal::cancel);
        locationRequest = null;
        searches.shutdownNow();
        super.onDestroy();
    }

    private Intent resultIntent() {
        return new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
    }

    private TextClock clock(String pattern) {
        TextClock view = new TextClock(this);
        view.setFormat12Hour(pattern);
        view.setFormat24Hour(pattern);
        return view;
    }

    private RadioButton unitButton(String text, RadioGroup group) {
        RadioButton view = new RadioButton(this);
        view.setId(android.view.View.generateViewId());
        view.setText(text);
        view.setButtonTintList(ColorStateList.valueOf(ACCENT));
        group.addView(view);
        return view;
    }

    private Button button(String text, Runnable action) {
        Button view = new Button(this);
        view.setText(text);
        view.setAllCaps(false);
        view.setOnClickListener(ignored -> action.run());
        return view;
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout panel() {
        LinearLayout view = column();
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(PANEL);
        shape.setCornerRadius(dp(18));
        view.setBackground(shape);
        view.setPadding(dp(14), dp(10), dp(14), dp(10));
        return view;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private void add(LinearLayout parent, android.view.View view, int topMargin) {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.topMargin = dp(topMargin);
        parent.addView(view, layout);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String hex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xffffff);
    }
}
