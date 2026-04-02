# Verbus

Verbus is an offline Android party game built with Kotlin and Jetpack Compose.

License: **MIT**. See [LICENSE](LICENSE).

## Screenshots
<p align="center">
  <img src="./screenshots/Screenshot_20260402_191417.png" alt="Menu" width="160">
  <img src="./screenshots/Screenshot_20260402_191428.png" alt="Game modes" width="160">
  <img src="./screenshots/Screenshot_20260402_191432.png" alt="Categories" width="160">
</p>

<img src="./screenshots/Screenshot_20260402_191452.png" alt="Game" width="160">
<img src="./screenshots/Screenshot_20260402_191508.png" alt="Summary" width="160">

## What is in the repo right now

- one playable mode: **Storytelling / Opowiadanie**
- built-in categories: **Cars**, **Animals**, **Science**, **Food**, **Memes**, **+18**
- offline-only content loaded from `assets/`
- settings stored in DataStore
- active round + topic history stored in Room
- UI languages: Polish and English

## Build

Requirements:

- Android Studio
- JDK 17

Commands:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Minimum Android version: **API 26**.

## Where the editable content lives

### Categories index

`app/src/main/assets/categories/categories.txt`

Format:

```text
category_id|file_name|polish name|english name|optional drawable resource name
```

Current example:

```text
cars|cars.txt|Samochody|Cars|ic_category_cars
animals|animals.txt|Zwierzęta|Animals|ic_category_animals
science|science.txt|Nauka|Science|ic_category_science
food|food.txt|Jedzenie|Food|ic_category_food
```

### Topic files

`app/src/main/assets/topics/*.txt`

Format:

```text
stable_id|polish text|english text
```

Example:

```text
car_001|Ferrari|Ferrari
car_002|Samochód elektryczny|Electric car
```

## How to add a new category

1. Create a new UTF-8 file in `app/src/main/assets/topics/`, for example `movies.txt`.
2. Add topics in the format `stable_id|pl|en`.
3. Add a new line to `app/src/main/assets/categories/categories.txt`.

Example:

```text
movies|movies.txt|Filmy|Movies|ic_category_movies
```

4. If the category should have an icon:
  - add the drawable to `app/src/main/res/drawable/`
  - register it in `previewDrawableMap` in `app/src/main/java/io/github/offlinepartygame/ui/components/CommonComponents.kt`
  - if it is a monochrome vector that should follow theme tinting, also add it to `isMonochromeVector(...)` in the same file
5. Rebuild the app.

Without the `CommonComponents.kt` mapping, the drawable name from `categories.txt` will not be shown in UI.

## How to add topics to an existing category

Append new lines to the correct file in `app/src/main/assets/topics/`.

Rules:

- one topic per line
- unique `stable_id` within that file
- UTF-8 encoding
- no empty required fields

## Validation rules used by the app

- lines starting with `#` are treated as comments
- blank lines are ignored
- malformed lines are skipped
- duplicate category IDs are skipped
- duplicate topic IDs inside one file are skipped
- a category is not playable if its topic file is missing or if no valid topics remain after parsing
- a category with fewer than 5 valid topics still works, but logs a warning

## Other files you may need to touch

### UI translations

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-pl/strings.xml`

### Main content-related code

- `app/src/main/java/io/github/offlinepartygame/data/content/TopicFileParser.kt`
- `app/src/main/java/io/github/offlinepartygame/data/content/AssetContentRepository.kt`
- `app/src/main/java/io/github/offlinepartygame/ui/components/CommonComponents.kt`

## Notes

- Topic history is used to reduce repeats between rounds.
- The current repeat block window is 8 hours.
- Active round state is restored after app resume/relaunch.
