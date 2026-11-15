# Car Home: build 0.1

A custom home screen (launcher) for the Android 10 head unit, built from your Figma design.

**In this build:** glass home screen, clock and date, Now Playing (with album art and the
"Sound of Silence" easter egg), automatic tab switching, Google Maps next-turn banner,
the bottom buttons, the Apps screen and Settings.
**Next build:** the live offline dark map inside the Navigation tab.

---

## 1. Add the Geist font (required, takes a minute)

1. Download **Geist** from Google Fonts (fonts.google.com/specimen/Geist → "Get font" → Download).
2. In the zip, open the `static` folder and copy these four files into
   `app/src/main/res/font/`, **renamed exactly** like this (Android only allows lowercase and underscores):

| File in the zip       | Rename to            |
|-----------------------|----------------------|
| `Geist-Regular.ttf`   | `geist_regular.ttf`  |
| `Geist-Medium.ttf`    | `geist_medium.ttf`   |
| `Geist-SemiBold.ttf`  | `geist_semibold.ttf` |
| `Geist-Bold.ttf`      | `geist_bold.ttf`     |

The project won't build until these four files are there.

## 2. Open and build

1. Install **Android Studio** (free) and open this `CarHome` folder (File → Open).
2. Let it finish syncing (the first time downloads Gradle and libraries, so it can take a few minutes).
   If it suggests upgrading the Android Gradle Plugin, you can accept.
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
   The APK appears in `app/build/outputs/apk/debug/app-debug.apk`.

## 3. Install it on the head unit

Pick one:

- **USB stick:** copy `app-debug.apk` to a USB stick, plug it into the unit, open it with the unit's
  file manager and install (allow "Unknown sources" if asked).
- **Cable + adb:** enable Developer options on the unit, connect it, then press **Run** in Android
  Studio, or run `adb install -r app-debug.apk`.

## 4. First-time setup on the unit

1. **Make it the home screen:** press the unit's Home button and choose **Car Home → Always**.
   If the unit keeps going back to its own launcher, open Settings in Car Home → **Default home app**.
   Some units also have a launcher choice inside their "Factory settings" menu.
2. **Allow notification access** (needed for Now Playing and the turn banner): tap the music circle on
   the home screen, or Car Home Settings → **Notification access**, and turn on **Car Home**.
   If the unit hides that screen, use adb instead:
   ```
   adb shell cmd notification allow_listener dev.carhome.launcher/dev.carhome.launcher.media.MediaListenerService
   ```
3. **Wallpaper:** copy your wallpaper photos (the clean images, without the UI on top) to the unit,
   then Car Home Settings → **Wallpaper**.
4. **Buttons:** the first tap on Connect Smartphone, Bluetooth or Radio asks which app it should open.
   Long-press any of them later to change it.

---

## Default wallpaper

Put one image at `app/src/main/res/drawable-nodpi/wallpaper_default.jpg` (the name must be exactly
`wallpaper_default`, lowercase, `.jpg` or `.png`). Best size: **2048×1200**, the 2x export of your
1024×600 frame. It shows until you pick a photo in Settings → Wallpaper, and
Settings → **Use default wallpaper** switches back to it.

## Software updates from the car

Settings → **Software update** checks GitHub Releases, downloads the new APK and opens the installer.

1. Set `REPO` in `app/src/main/java/dev/carhome/launcher/update/Updater.kt` to your
   `owner/repository`. **It must be a public repository.** To keep your code private, make a
   second public repository just for releases (for example `CarHome-releases`) and point `REPO` at that.
2. For each new version: raise `versionCode` and `versionName` in `app/build.gradle.kts`, build a
   **signed** APK with your key, and publish a GitHub release tagged `v` + versionName (for example `v0.3`)
   with the APK attached.
3. The first time, Android asks you to allow Car Home to install apps. Allow it, go back, and tap
   **Install** again.

---

## How the design maps to the code

| Figma | Code |
|---|---|
| Frame 1024×600 | `ui/theme/Theme.kt` → `DesignCanvas` scales the 1024×600 canvas to the real screen, so 1 Figma unit = 1dp |
| Colors, Geist type styles | `ui/theme/Theme.kt` (`Fg`, `Muted`, `Placeholder`, `Type`) |
| Glass effect | `ui/glass/Glass.kt`: frosted wallpaper + black 20% + light edge at 45° |
| Layout and sizes | `ui/home/HomeScreen.kt` (the layout comment at the top lists every value) |
| Blur strength | `wallpaper/Wallpaper.kt` → `blurred()` |

**Icons:** this build uses Google's Material icons as stand-ins. To use your exact Figma icons, export
them as SVG and send them over, and I'll convert them.
