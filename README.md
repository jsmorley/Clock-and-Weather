# Clock & Weather

A transparent Android Home screen widget with a centered 12-hour clock, full weekday and date, and current temperature and conditions. Each line has its own size and color.

## Install and add the widget

1. Install the APK on your phone. With an authorized ADB connection, run `adb install -r Clock-and-Weather-1.0.3.apk`.
2. On the Galaxy Home screen, touch and hold an empty area. Open **Widgets**, find **Clock & Weather**, and add it.
3. Tap **Use current location** and allow location access, or search for a city or ZIP code and pick a result. Then tap **Add widget**. Precise access lets Android try GPS when approximate location is unavailable.
4. Tap the clock or date to edit the widget. Use the sliders to change the sizes and the font buttons to choose a typeface for each line, including Roboto. Each color button offers **Auto**, color swatches, and six-digit RGB hex entry. Tap **Save changes**.

**Auto** uses white for dark Home screen wallpapers and black for light ones, including the weather icon when the weather line uses Auto. Android provides a contrast hint for the whole wallpaper. A wallpaper with mixed light and dark areas may work better with a manual color. If Android pauses the app in the background, the color may update at the next scheduled widget refresh.

The font picker lists the named system families and distinct variants Android lets the widget use on this phone. Fonts supplied only inside another app or a Samsung font package may be available from the phone's system font settings.

Tap the weather icon or text to open The Weather Channel. To choose a different installed app, use **App opened by weather tap** in the widget settings.

Resize the widget if you want more room for larger text. The clock also scales to fit the available width when the hour has two digits. The widget uses the phone's time zone. Temperature defaults to Fahrenheit.

Weather comes from [Open-Meteo](https://open-meteo.com/). The app sends your search term or the saved weather coordinates to Open-Meteo as needed. Tap **Use current location** again after traveling to save the new location. Android normally schedules a weather refresh about every 30 minutes. Network access and Android's battery settings can affect the timing.

## Build

Install JDK 17 or newer and Android SDK Platform 36. Set `ANDROID_HOME` to your Android SDK directory, then run `gradlew.bat assembleDebug lintDebug` on Windows or `./gradlew assembleDebug lintDebug` elsewhere. The APK is under `app/build/outputs/apk/debug/`.
