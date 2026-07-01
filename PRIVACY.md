# Privacy Policy for FoxyBook

**Last updated:** July 1, 2026

## Overview

FoxyBook is an Android e-book reader that works with your local files. This privacy policy explains how the application handles your data.

## Data Collection

**FoxyBook does not collect, store, or transmit any personal data.**

- The app works entirely offline.
- No accounts, registration, or sign-in is required.
- No analytics, crash reporting, or telemetry services are integrated.
- No advertising or tracking SDKs are used.

## File Access

FoxyBook reads e-book files (FB2, EPUB, MOBI) that you explicitly open from your device's storage. The app:

- Opens files you select through the system file picker or from the app's local library.
- Stores metadata about your imported books (title, author, cover thumbnail, reading progress) locally on your device.
- Does **not** upload your books, documents, or any file contents to any server.

## Local Storage

The app uses your device's internal storage to save:

- **Reading preferences** — font size, theme, brightness, and other UI settings (via Jetpack DataStore).
- **Library metadata** — information about books you have imported into the app's library.
- **Reading progress** — your current position and bookmarks within each book.

All this data resides exclusively on your device and can be cleared at any time through Android's app settings (**Settings → Apps → FoxyBook → Storage → Clear data**).

## Permissions

On Android 10 (API 29) and below, FoxyBook may request `READ_EXTERNAL_STORAGE` permission to access book files stored on your device. On Android 11+ the app uses scoped storage or the system file picker, requiring no special storage permissions.

## Third-Party Services

FoxyBook does **not** integrate any third-party services, analytics frameworks, crash reporters, or advertising networks.

The app uses the following open-source libraries, none of which transmit data off your device:

| Library | Purpose |
|---|---|
| Jetpack Compose & Material 3 | UI framework |
| Kotlin Serialization | Local data parsing |
| DataStore | Local preferences storage |
| Coil | Local cover image loading |
| Jsoup | Local HTML/CSS parsing for book rendering |
| OkHttp | Used only for local file access |

## Children's Privacy

FoxyBook does not collect any personal information from anyone, including children under 13.

## Changes to This Policy

If this policy changes in the future, the "Last updated" date at the top will be updated. Since the app does not collect data, any future changes would only clarify or expand the explanation of how the app functions.

## Contact

If you have questions about this privacy policy, please open an issue on the GitHub repository:

[https://github.com/maximbogatchenko/FoxyBook](https://github.com/maximbogatchenko/FoxyBook)
