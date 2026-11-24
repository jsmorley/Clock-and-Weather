package com.jsmorley.clockweather;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class WidgetFont {
    static final WidgetFont DEFAULT = new WidgetFont("sans-serif", "Roboto");
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("sans-serif-light", "Sans serif light"),
            Map.entry("sans-serif-medium", "Sans serif medium"),
            Map.entry("sans-serif-thin", "Sans serif thin"),
            Map.entry("sans-serif-black", "Sans serif black"),
            Map.entry("sans-serif-condensed", "Sans serif condensed"),
            Map.entry("monospace", "Monospace"),
            Map.entry("casual", "Handwritten"),
            Map.entry("cursive", "Script"),
            Map.entry("roboto-flex", "Roboto Flex"),
            Map.entry("sec", "One UI Sans"),
            Map.entry("sec-no-flip", "One UI Sans (fixed family)"),
            Map.entry("sec-num", "Samsung Numerals"),
            Map.entry("sec-num-fixed", "Samsung Numerals (fixed width)"),
            Map.entry("source-sans-pro", "Source Sans Pro"),
            Map.entry("source-sans-pro-semi-bold", "Source Sans Pro Semibold")
    );

    private static class Catalog {
        static final Map<String, WidgetFont> FAMILIES = discover();
    }

    final String family;
    final String label;
    private final Typeface typeface;

    private WidgetFont(String family, String label) {
        this.family = family;
        this.label = label;
        typeface = Typeface.create(family, Typeface.NORMAL);
    }

    static WidgetFont forFamily(String family) {
        return Catalog.FAMILIES.getOrDefault(family, DEFAULT);
    }

    static List<WidgetFont> available() {
        List<WidgetFont> fonts = new ArrayList<>();
        List<WidgetFont> candidates = new ArrayList<>(Catalog.FAMILIES.values());
        candidates.sort(Comparator.comparing(font -> font.label, String.CASE_INSENSITIVE_ORDER));
        fonts.add(DEFAULT);
        for (WidgetFont candidate : candidates) {
            if (fonts.stream().noneMatch(font -> font.typeface.equals(candidate.typeface))) {
                fonts.add(candidate);
            }
        }
        return fonts;
    }

    private static Map<String, WidgetFont> discover() {
        Map<String, WidgetFont> families = new LinkedHashMap<>();
        families.put(DEFAULT.family, DEFAULT);
        for (String name : List.of("sans-serif-light", "sans-serif-medium", "sans-serif-condensed",
                "serif", "monospace", "casual", "cursive")) {
            addFamily(families, name);
        }
        for (String path : List.of("/system/etc/font_fallback.xml", "/system/etc/fonts.xml",
                "/system/etc/fonts_additional.xml", "/product/etc/fonts_customization.xml",
                "/system_ext/etc/fonts_customization.xml", "/vendor/etc/fonts_customization.xml")) {
            try (FileInputStream stream = new FileInputStream(path)) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(stream, null);
                for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                    if (event != XmlPullParser.START_TAG) continue;
                    String tag = parser.getName();
                    // Unweighted aliases are compatibility names for an already listed family.
                    String weight = parser.getAttributeValue(null, "weight");
                    boolean weightedAlias = tag.equals("alias") && weight != null && !weight.equals("400");
                    if (tag.equals("family") || tag.equals("family-list") || weightedAlias) {
                        addFamily(families, parser.getAttributeValue(null, "name"));
                    }
                }
            } catch (IOException | XmlPullParserException | SecurityException unavailable) {
                // Android versions and device makers expose different configuration files.
            }
        }
        return families;
    }

    private static void addFamily(Map<String, WidgetFont> families, String family) {
        if (family == null || family.trim().isEmpty() || families.containsKey(family)) return;
        String label = LABELS.get(family);
        if (label == null) {
            StringBuilder words = new StringBuilder();
            for (String part : family.split("[-_ ]+")) {
                if (part.isEmpty()) continue;
                if (words.length() > 0) words.append(' ');
                words.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
            }
            label = words.toString();
        }
        WidgetFont font = new WidgetFont(family, label);
        if (!font.typeface.equals(DEFAULT.typeface)) families.put(family, font);
    }

    Typeface typeface() {
        return typeface;
    }

    CharSequence style(String text) {
        if (this == DEFAULT) return text;
        SpannableString styled = new SpannableString(text);
        styled.setSpan(new TypefaceSpan(family), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return styled;
    }

    @Override public String toString() {
        return label;
    }
}
