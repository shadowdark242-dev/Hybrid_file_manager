# Hybrid File Manager

A combined Android file manager: a clean dark interface, two panes on demand and a set of
tools for keeping storage tidy. It is a fork of
[Fossify File Manager](https://github.com/FossifyOrg/File-Manager) (GPLv3).

[Русская версия ниже](#русская-версия)

## Download

The latest debug build is published automatically on every push:

**[HybridFileManager-debug.apk](https://github.com/shadowdark242-dev/Hybrid_file_manager/releases/download/latest/HybridFileManager-debug.apk)**

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

---

## Русская версия

Комбинированный файловый менеджер для Android: тёмный интерфейс, две панели по кнопке и
инструменты для наведения порядка в памяти. Форк
[Fossify File Manager](https://github.com/FossifyOrg/File-Manager) (GPLv3).

### Скачать

Свежая отладочная сборка публикуется автоматически при каждом изменении:

**[HybridFileManager-debug.apk](https://github.com/shadowdark242-dev/Hybrid_file_manager/releases/download/latest/HybridFileManager-debug.apk)**

Нужен Android 8.0 или новее. При установке разрешите установку из неизвестных источников.

### Возможности

- **Кнопки хранилищ** – скруглённые кнопки со значками: внутренняя память, карта памяти,
  подключённая USB-флешка (и корень `/`, если включён root).
- **Две панели** – вкладка «Файлы» делится на две панели одной кнопкой, быстрое копирование и
  перенос между ними. В каждой папке есть пункт `..` для перехода на уровень выше.
- **Root** – просмотр всей файловой системы при включённом root-доступе.
- **Размеры папок** – нажмите на хранилище во вкладке «Хранилище», чтобы увидеть диаграмму
  занятого места по папкам.
- **Очистка** – поиск дубликатов (сравниваются по содержимому, а не по имени) и пустых папок,
  удаление одним действием.
- **Значки APK** – у файлов `.apk` показываются значки приложений; строятся один раз в фоне и
  хранятся в кэше до следующего запуска.
- **Темы** – тёмная тема Hybrid по умолчанию, светлая тема Hybrid и обычная настройка цветов.
- Всё, что умеет Fossify File Manager: список и сетка, поиск, избранное, архивы, недавние файлы.

### Сборка

Gradle и JDK 21:

```
./gradlew assembleFossDebug
```

GitHub Actions выполняет ту же команду и прикрепляет APK к релизу `latest`.

### Лицензия

GPLv3, см. [LICENSE](LICENSE). Код основан на Fossify File Manager, подробности в
[NOTICE.md](NOTICE.md).
