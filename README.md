# Offline Party Game

A fully offline open-source Android party game written in Kotlin with Jetpack Compose.

License: **MIT**. See [LICENSE](LICENSE).

## Overview

This project implements a small, production-like Android app for a party game with these goals:

- fully offline
- no ads
- no analytics
- no telemetry
- no network calls
- readable architecture
- externalized UI strings
- externalized category/topic content
- easy extension for new categories and future game modes

The initial game mode is:

- Polish: **Opowiadanie**
- English: **Storytelling**

The app currently includes two sample categories:

- Cars / Samochody
- Animals / Zwierzęta
- Science / Nauka
- Food / Jedzenie

## Key gameplay rules

Main round flow:

1. Main menu
2. Mode selection
3. Category selection
4. Pre-round countdown
5. Topic display
6. Time-up message when needed
7. Automatic move to the next topic
8. Summary screen at the end of the round

A topic can be marked as completed by:

- double tap in the center zone
- shaking the phone
- the large on-screen "Completed" button

The completion signal method is configurable in Options.

## Current customization options

The current app build already includes these runtime settings:

- language: System / Polish / English
- completion signal: double tap / shake / button
- topics per round
- topic display duration
- pre-round countdown duration
- time-up message duration
- haptic feedback on completion
- keep screen awake during the round
- background color 1
- background color 2
- font color
- sounds on/off
- sound volume

## Anti-repeat logic

The app stores shown topic history in Room.

Rule:

- the same topic in the same category should not appear again for **8 hours**
- the rule survives app restarts
- if all topics are blocked by the 8-hour rule, the app automatically resets only that restriction for selection
- within the current round, the app still tries to avoid duplicates first
- only if the round has already exhausted all unique topics in the category does the selector allow a same-round repeat

Implementation summary:

1. Load all topics in the category
2. Exclude topics already used in the current round
3. Exclude topics shown in the last 8 hours
4. If empty, retry without the 8-hour filter
5. If still empty, retry from the full pool

This guarantees the selector never crashes because a filtered pool is empty.

## Architecture overview

The project uses a small layered structure:

- **UI layer**
  - Compose screens
  - activity-scoped ViewModels
  - navigation
  - sensor/UI interaction wiring
- **Domain layer**
  - game models
  - topic selection logic
  - round coordinator / state transitions
  - interfaces for repositories
- **Data layer**
  - Room persistence for shown-topic history and active round snapshot
  - DataStore for settings
  - asset-backed content repository for categories and topics

Important design choices:

- `RoundCoordinator` owns round progression rules
- `TopicSelector` is pure, deterministic business logic apart from the injected random provider
- category metadata and topics live in assets, not in Kotlin source
- settings are sanitized when loaded from DataStore
- an active round is persisted so the app can restore it after process death or background/resume

## Tech stack

- Kotlin
- Jetpack Compose
- Navigation Compose
- Room
- Preferences DataStore
- no DI framework
- no networking stack

## Minimum Android version

- **minSdk = 26** (Android 8.0)

## Build instructions

### Requirements

- Android Studio with Android SDK matching compileSdk 36
- JDK 17
- Android Gradle Plugin 9.1.0
- Gradle wrapper from this repository

### Build in Android Studio

1. Open the project root in Android Studio.
2. Let Gradle sync.
3. Run the `app` configuration on a device or emulator.

### Build from command line

```bash
./gradlew assembleDebug
```

### Run tests

```bash
./gradlew testDebugUnitTest
```

## Localization

UI strings are localized with Android string resources:

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-pl/strings.xml`

The app supports both system locale and an in-app language setting (System / Polish / English).

### Topic localization

Topics are stored as UTF-8 text lines in assets:

```text
stable_id|polish text|english text
```

Example:

```text
car_001|Ferrari|Ferrari
car_002|Samochód elektryczny|Electric car
```

At runtime the app chooses Polish text for Polish locales and English text otherwise.

### How to add a new language later

1. Create a new `values-xx/strings.xml` directory for UI strings.
2. Extend topic files to include another language format if desired, or switch the topic file format to something richer such as CSV/JSON.
3. Update the topic content loader to read the additional language field.
4. Add any new category display names in the category index file.

For the current scope, topic files intentionally stay simple with only Polish and English columns.

## Content storage

### Category metadata

Category metadata is stored in:

- `app/src/main/assets/categories/categories.txt`

Format:

```text
category_id|file_name|polish name|english name|optional_drawable_name
```

Example:

```text
cars|cars.txt|Samochody|Cars|ic_category_cars
animals|animals.txt|Zwierzęta|Animals|ic_category_animals
```

The optional drawable name points to a resource in `res/drawable`. You can add a PNG directly or import an SVG into Android Studio as a VectorDrawable resource and reference the resulting drawable name.

### Topic files

Topic files are stored in:

- `app/src/main/assets/topics/`

Current files:

- `cars.txt`
- `animals.txt`
- `science.txt`
- `food.txt`

## How to add a new category

1. Create a new UTF-8 topic file in `app/src/main/assets/topics/`, for example `movies.txt`.
2. Add lines in the format:

```text
movie_001|Matrix|The Matrix
movie_002|Gladiator|Gladiator
```

3. Optionally add a preview image to `res/drawable` and reference it in `app/src/main/assets/categories/categories.txt`:

```text
movies|movies.txt|Filmy|Movies|ic_category_movies
```

4. Rebuild the app.

No core game logic changes are required.

## How to add new topics

Just append new lines to the appropriate topic file.

Rules:

- one topic per line
- keep `stable_id` unique within that category
- keep UTF-8 encoding
- avoid empty fields

Invalid lines are skipped safely.

## Error handling

The app explicitly handles these cases:

- missing category index file
- missing topic file
- malformed topic line
- duplicated `stable_id`
- empty category after validation
- categories with too few valid topics
- unsupported shake sensor
- sanitized / corrupted settings fallback
- empty selection pools
- round restore failures
- timer boundary transitions while app was in background

Developer-facing asset issues are logged with `Log.e` / `Log.w`. User-facing failures are shown as localized dialogs/messages instead of crashes.

## Active round restore behavior

An active round is persisted in Room. On app resume or relaunch:

- the app attempts to restore the round
- time-based phases are advanced according to the persisted phase end timestamp
- if enough time passed while the app was in background, the round may progress through multiple phases automatically
- if a summary is reached, the persisted active round is cleared

This behavior is intentional. The timers represent real elapsed time rather than "pause on background".

## Orientation and lifecycle

The app is **not locked to portrait**.

The session screen has dedicated portrait and landscape layouts. In landscape mode, the round stats are compressed into a single top row and the topic area stays centered with larger text. This makes it practical to rotate the phone during active gameplay without breaking the flow.

Active rounds still survive background/resume and process death because the current round snapshot is persisted and restored on launch.

## Accessibility notes

Implemented:

- large text for topic display
- large tap targets
- center-zone content description
- timer content description
- readable high-contrast layout
- simple structure compatible with TalkBack
- responsive session layout for both portrait and landscape play

## Testing

Included unit tests:

- topic parsing
- topic selection with 8-hour exclusion
- reset behavior when pools are exhausted
- round progression logic

### Why no UI tests right now

The core risk in this app is not complex rendering but deterministic game logic and persistence behavior. For the first open-source version, the highest-value tests are pure unit tests over parser, selector, and round progression rules. UI tests can be added later, but were intentionally omitted to keep the project lightweight and focused.

## Tradeoffs and limitations

- Only one game mode is implemented right now.
- Topic files currently support only Polish and English columns.
- Category validation logs developer-facing details, but the user sees simpler messages.
- The app restores active rounds automatically instead of pausing timers in background.
- The app blocks system back during an active round to avoid accidental navigation into an inconsistent state.
- Shake support falls back to double tap for the active round if the device does not expose an accelerometer.

## Future extension ideas

- more game modes
- richer summary statistics
- import/export of custom topic packs
- better asset validation tooling
- UI tests
- dedicated tablet layouts beyond the current responsive phone layouts
- more category artwork and stronger visual identity on menu screens
- richer transition animations and session polish
