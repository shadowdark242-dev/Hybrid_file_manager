# Hybrid File Manager

A combined Android file manager: a clean dark interface, two panes on demand and a set of
tools for keeping storage tidy. It is a fork of
[Fossify File Manager](https://github.com/FossifyOrg/File-Manager) (GPLv3).

## Download

The latest build is published automatically on every push:

**[HybridFileManager.apk](https://github.com/shadowdark242-dev/Hybrid_file_manager/releases/download/latest/HybridFileManager.apk)**

Android 8.0+ is required. Allow installing from unknown sources when asked.

## Features

- **Storage buttons** – rounded buttons with icons for internal storage, SD card and connected
  USB drives (plus root `/` when root access is on).
- **Dual pane** – split the Files tab in two with one button; copy or move between panes quickly.
  Every folder shows a `..` entry to go one level up.
- **Root support** – browse the whole file system when root access is enabled.
- **Folder sizes** – tap a storage header on the Storage tab to see a donut chart of what takes
  the space, folder by folder.
- **Cleanup tab** – scan for duplicate files (compared by content, not by name) and empty folders,
  review the result and delete it in one go.
- **APK icons** – app icons are shown for `.apk` files; they are built once in the background and
  cached until the next app start.
- **Themes** – a dark Hybrid theme by default, a light Hybrid theme and the usual Fossify
  colour customization in Settings.
- Everything from Fossify File Manager: list and grid views, search, favorites, archives,
  recent files, storage analysis and more.

## Building

The project builds with Gradle and JDK 21.

```
./gradlew assembleFossDebug
```

GitHub Actions (`.github/workflows/build.yml`) runs the same command and attaches the APK to the
rolling `latest` release.

## License

GPLv3, see [LICENSE](LICENSE). The code is based on Fossify File Manager; see
[NOTICE.md](NOTICE.md) for details.

