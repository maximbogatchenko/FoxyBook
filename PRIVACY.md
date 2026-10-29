# Privacy Policy for FoxyBook

**Last updated:** October 4, 2026

## Overview

FoxyBook is an Android e-book reader. It works with books on your device and can
optionally download books from public online catalogs. This policy explains what
data the app handles and where it goes.

## Data Collection

**FoxyBook does not collect, store, or transmit personal data about you, and does
not create any user identifier.**

- No accounts, registration, or sign-in is required.
- No analytics, crash reporting, or telemetry services are integrated.
- No advertising or tracking SDKs are used.
- No device identifiers are read or sent (no IMEI, Android ID, advertising ID).

## Network Requests

The app is **not fully offline**. When you use the online catalog features, the app
sends the following to the catalog server you have selected:

| Request | What is sent | Purpose |
|---|---|---|
| Open the "New books" feed | Catalog URL, `Accept-Language`, `User-Agent` | Fetch the book feed |
| Search for books, authors, series or genres | Your **search terms and author names**, encoded in the request URL | Return search results |
| Open a book page / author / series page | Book, author, or series id in the request URL | Fetch book details |
| Download a book | Book id in the request URL | Download the file you asked for |
| Check for app updates | Request to `api.github.com` | Compare your version with the latest release |
| Download an update | Request to `github.com` | Download the release file |

Search terms and book identifiers are therefore visible to the operator of the
catalog that is currently selected in the app. Catalog hosts used by the app:

- `flibusta.is`, `flibusta.site`
- `coollib.net`, `coollib.cc`
- `fantasy-worlds.org`
- `api.github.com`, `github.com` (update checks and update downloads)

All connections use HTTPS. Requests are made only when you open a catalog screen,
perform a search, or the app checks for an update at startup. There is no
background upload of any kind, and nothing is sent when you only read local files.

Cover images for catalog books are downloaded from the same catalog servers or from
the links they provide.

## File Access

FoxyBook reads e-book files (FB2, EPUB, MOBI, TXT) that you explicitly open from your
device's storage. The app:

- Opens files you select through the system file picker, from the app's library, or
  that the app itself downloaded at your request.
- Stores metadata about your books (title, author, cover, reading progress) locally.
- Does **not** upload your books, documents, or any file contents to any server.

## Local Storage

The app uses your device's internal storage to save:

- **Reading preferences** — font size, theme, brightness, and other UI settings (Jetpack DataStore).
- **Library metadata** — books you have added to the app's library.
- **Reading progress** — your current position and bookmarks within each book.
- **A copy of books you import** through the system file picker, so they remain
  readable if the original file moves.
- **A cache of public catalog responses** — the OPDS feeds of the catalogs you
  browse (book lists, genres, search results) are stored on device so the app
  can show them without a new request. It contains no data about you and can be
  removed with the "Clear cache" action or by clearing app data. Catalogs that
  ask not to be cached (HTTP `no-store`) are never stored.

All of this data stays on your device. The "Clear cache" action in Settings removes
cached images and covers but keeps your books. To remove everything, use Android's
app settings (**Settings → Apps → FoxyBook → Storage → Clear data**) or uninstall the
app.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Catalog browsing, searching, downloading books, update checks |
| `POST_NOTIFICATIONS` | Reading-aloud playback controls |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keeps text-to-speech playback running |
| `REQUEST_INSTALL_PACKAGES` | Installing an app update downloaded from the GitHub release page. Before installation the app verifies that the downloaded file is signed with the same certificate as the installed app |

Storage permissions are not required: the app uses the system file picker and scoped
storage.

## Third-Party Services

The app contacts the catalog operators and GitHub as described above. No other
third-party services are used, and none of the open-source libraries below transmit
data on their own:

| Library | Purpose |
|---|---|
| Jetpack Compose & Material 3 | UI framework |
| Room | Local database of the library and reading progress |
| Koin | Dependency injection |
| Kotlin Serialization | Local data parsing |
| DataStore | Local preferences storage |
| Coil | Image loading for book covers |
| Jsoup | Local HTML/XML parsing for book rendering |
| OkHttp | Network access to catalogs and update checks |
| Media3 | Text-to-speech playback |

## Children's Privacy

FoxyBook does not knowingly collect personal information from anyone, including
children under 13. Because search terms are sent to the selected catalog server,
do not use the catalog features with a shared or child-operated account on a device
without supervision.

## Changes to This Policy

If this policy changes, the "Last updated" date at the top will be updated.

## Contact

If you have questions about this privacy policy, please open an issue on the GitHub
repository:

[https://github.com/maximbogatchenko/FoxyBook](https://github.com/maximbogatchenko/FoxyBook)