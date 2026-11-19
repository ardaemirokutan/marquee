# Salon

A minimal full-screen home screen (launcher) for Google TV / Android TV, written in plain Java with no Gradle and no libraries.

![Salon](docs/screenshot.png)

## What it does

- One row of app banners at the bottom. The focused app's colour (taken from its banner) fills the background, and the focused banner glows in that colour. Other focus effects: lift, or a simple white ring.
- Above the row: the app's name and a short description for well-known streaming apps (English and Turkish).
- Top row: the current person's name and quick buttons for Settings, Wi‑Fi, Display & sound, Apps and the Salon settings screen. Clock and date top right.
- Hold OK on an app: Move to front, Hide, App info, Uninstall.
- Remembers the last opened app ("LAST OPENED" above its name) and can optionally keep it first in the row.
- Settings screen: person, editable list of people, focus effect, background (app colour or black), description on/off, 24/12-hour clock, last opened first, and per-app hide/show and order (Left/Right moves an app).
- The interface follows the device language: English by default, Turkish when the device is set to Turkish.
- App banners are read from the installed apps at run time; no app logos are shipped with Salon.

## Build

Needs a JDK 17+ (built with 21), the Android SDK with platform `android-34` and build-tools `34.0.0`, and Python 3 (used to add `classes.dex` to the APK).

```sh
cp local.env.example local.env   # set JAVA_HOME and ANDROID_HOME (or export them yourself)
sh build.sh                      # Git Bash on Windows, or any POSIX shell
```

The result is `out/salon.apk`. Without `KEYSTORE_PASS`, it is signed with a debug keystore created in `.keystore/`. To sign with your own key, set `KEYSTORE` (alias `salon`) and `KEYSTORE_PASS`. Keep using the same key, or Android will refuse to update an installed copy.

## Install

```sh
adb connect <tv-ip>:5555
adb install -r out/salon.apk
adb shell cmd package set-home-activity com.arda.salon/.Home
adb shell settings put secure enabled_accessibility_services com.arda.salon/com.arda.salon.HomeWatch
```

Google TV keeps the Home button for its own launcher, so choosing Salon as the home app is not enough there. `HomeWatch` is an accessibility service that only listens for "a new window came to the front" events from the Google TV launcher packages (`com.google.android.apps.tv.launcherx`, `com.google.android.tvlauncher`); when one appears, it brings Salon to the front. It cannot read screen content (`canRetrieveWindowContent="false"`). You may see the Google TV home for a moment before Salon appears.

Note: `settings put secure enabled_accessibility_services` replaces the whole list, so include any other services you already use (separated by `:`).

## Uninstall

```sh
adb shell settings delete secure enabled_accessibility_services   # or put back your previous list
adb uninstall com.arda.salon
```

The font is [Inter](https://github.com/rsms/inter), under the SIL Open Font License (`assets/OFL.txt`). Salon itself is MIT licensed (`LICENSE`).

---

## Türkçe

Google TV / Android TV için sade, tam ekran bir ana ekran (launcher). Düz Java ile yazıldı; Gradle ve kütüphane yok.

### Ne yapar

- Altta tek sıra uygulama banner'ı. Seçili uygulamanın rengi (banner'ından alınır) arka planı doldurur, seçili banner o renkte parlar. Diğer efektler: yükselme ya da sade beyaz çerçeve.
- Satırın üstünde uygulamanın adı ve bilinen yayın uygulamaları için kısa açıklama (Türkçe ve İngilizce).
- Üstte: geçerli kişinin adı ve Ayarlar, Wi‑Fi, Ekran ve ses, Uygulamalar, Ana ekran (Salon ayarları) düğmeleri. Sağ üstte saat ve tarih.
- Bir uygulamada OK'a uzun basınca: Başa taşı, Gizle, Uygulama bilgisi, Kaldır.
- Son açılan uygulamayı hatırlar (adının üstünde "SON AÇILAN"), istenirse sıranın başında tutar.
- Ayarlar: kişi, düzenlenebilir kişi listesi, seçme efekti, arka plan (uygulama rengi ya da siyah), açıklama yazısı, 24/12 saat, son açılanı başa al, uygulama başına gizle/göster ve sıra (◀ ▶ ile taşınır).
- Arayüz cihazın dilini izler: varsayılan İngilizce, cihaz Türkçeyse Türkçe.
- Banner'lar çalışırken yüklü uygulamalardan okunur; Salon'la birlikte uygulama logosu dağıtılmaz.

### Derleme

JDK 17+ (21 ile derlendi), `android-34` platformu ve `34.0.0` build-tools içeren Android SDK, Python 3 gerekir.

```sh
cp local.env.example local.env   # JAVA_HOME ve ANDROID_HOME'u yaz (ya da kendin export et)
sh build.sh                      # Windows'ta Git Bash
```

Çıktı `out/salon.apk`. `KEYSTORE_PASS` yoksa `.keystore/` altında oluşturulan debug anahtarıyla imzalanır. Kendi anahtarın için `KEYSTORE` (alias `salon`) ve `KEYSTORE_PASS` ver. Hep aynı anahtarı kullan; yoksa Android kurulu sürümün üstüne güncellemeyi reddeder.

### Kurulum

```sh
adb connect <tv-ip>:5555
adb install -r out/salon.apk
adb shell cmd package set-home-activity com.arda.salon/.Home
adb shell settings put secure enabled_accessibility_services com.arda.salon/com.arda.salon.HomeWatch
```

Google TV, Home tuşunu kendi ana ekranına ayırır; Salon'u ana uygulama seçmek orada yetmez. `HomeWatch` bir erişilebilirlik servisidir ve yalnız Google TV ana ekran paketlerinden gelen "öne yeni pencere geldi" olaylarını dinler; böyle bir olayda Salon'u öne getirir. Ekran içeriğini okuyamaz (`canRetrieveWindowContent="false"`). Salon gelmeden önce Google TV ana ekranı bir an görünebilir.

Not: `settings put secure enabled_accessibility_services` listenin tamamını değiştirir; kullandığın başka servis varsa onları da `:` ile ekle.

### Kaldırma

```sh
adb shell settings delete secure enabled_accessibility_services   # ya da önceki listeni geri yaz
adb uninstall com.arda.salon
```

Yazı tipi [Inter](https://github.com/rsms/inter), SIL Open Font License ile (`assets/OFL.txt`). Salon MIT lisanslıdır (`LICENSE`).
