# 📚 FoxyBook

Android-читалка для FB2, EPUB и TXT с поиском по онлайн-каталогам (Flibusta, CoolLib, Fantasy-Worlds), закладками и озвучкой текста.

![Android](https://img.shields.io/badge/Android-9.0%2B-green)
![Kotlin](https://img.shields.io/badge/Kotlin-100%25-purple)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Latest-blue)

## ✨ Возможности

* 📖 Удобный интерфейс чтения с горизонтальной и вертикальной прокруткой
* 📚 Форматы FB2, EPUB, TXT (в том числе файлы в windows-1251 и KOI8-R)
* 🌐 Отрисовка HTML-глав, встроенные иллюстрации EPUB/FB2/MOBI
* 🔍 Быстрый переход по главам, поиск по тексту книги
* 🔖 Закладки и сохранение позиции чтения
* 🗂 Онлайн-каталоги: Flibusta, CoolLib, Fantasy-Worlds (OPDS) — новинки, поиск по книгам/авторам/сериям/жанрам, скачивание
* 🔊 Озвучка текста (TTS) с выбором голоса, скорости и таймером сна
* 🏠 Настраиваемый главный экран приложения
* 🎨 Material Design 3, светлые и тёмные темы, три языка интерфейса (ru/en/uk)
* 💾 Локальная библиотека с коллекциями и избранным

## 🛠 Технологии

* Kotlin, Jetpack Compose, Material 3
* Coroutines / Flow
* Room (библиотека, позиции, закладки, коллекции)
* Koin (DI)
* DataStore Preferences (настройки)
* Kotlin Serialization
* Coil 3 (изображения)
* Jsoup (разбор HTML/XML)
* OkHttp (сеть)
* Media3 (озвучка)

## 🚀 Сборка

Требуется JDK 17.

```bash
git clone https://github.com/maximbogatchenko/FoxyBook.git
cd FoxyBook

# Отладочная сборка (applicationId com.foxybook.app.debug)
./gradlew assembleDebug

# Юнит-тесты
./gradlew testDebugUnitTest

# Линт
./gradlew lintDebug

# Релизная сборка: нужны ключи подписи в local.properties или в -P/переменных окружения
./gradlew assembleRelease
```

Ключи подписи задаются так (в `local.properties` или через `-P`):

```properties
RELEASE_STORE_FILE=release.keystore
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

Без этих значений релизная сборка падает с понятной ошибкой — собирать релиз с пустыми
учётными данными опаснее, чем не собрать его вовсе.

## 📦 Требования

* Android 9.0 (API 28+) или новее
* Android Studio Ladybug или новее (AGP 8.10, Kotlin 2.2)

## 🧪 Тесты

```bash
./gradlew testDebugUnitTest
```

Покрыты юнит-тестами: определение кодировки текстовых книг, разбор HTML в блоки,
разбор OPDS-фидов на реальных ответах каталогов, политика кеширования фидов,
сравнение версий для апдейтера, модели данных.

Фикстуры в `app/src/test/resources/fixtures` сняты с живых каталогов. Дополнительно
есть проверка самих живых каталогов — она не запускается по умолчанию, потому что
зависит от доступности сети и содержимого выдачи:

```bash
OPDS_LIVE_TESTS=1 ./gradlew testDebugUnitTest --tests '*LiveOpdsSmokeTest*'
```

## 🔒 Приватность

Приложение не собирает персональные данные и не использует аналитику. Сетевые запросы
(поисковые запросы, идентификаторы книг) отправляются только выбранному каталогу —
подробности в [PRIVACY.md](PRIVACY.md).

## 📄 Лицензия

См. [LICENSE](LICENSE). Проект предоставляется для образовательных и личных целей.