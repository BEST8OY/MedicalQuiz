# Medical Quiz App

A Kotlin-based Android application for medical quizzes that allows users to select and load quiz databases from external storage.

## Features

- 📱 Modern Android app built with Kotlin
- 🗄️ Support for loading multiple SQLite database files
- 📂 Reads databases from `/storage/emulated/0/MedicalQuiz/databases/`
- 🎨 Material Design UI with card-based database selection
- 🔐 Proper storage permissions handling for Android 6.0+
- 🏗️ Clean architecture with ViewBinding

## Project Structure

```
MedicalQuiz/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/medicalquiz/app/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── DatabaseAdapter.kt
│   │   │   │   └── DatabaseItem.kt
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_main.xml
│   │   │   │   │   └── item_database.xml
│   │   │   │   └── values/
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   └── build.gradle.kts
├── gradle/
├── .github/
│   └── workflows/
│       └── android-ci.yml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17 or later
- Android SDK with minimum API level 24 (Android 7.0)
- Kotlin 1.9.20+

## Setup Instructions

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/MedicalQuiz.git
cd MedicalQuiz
```

### 2. Open in Android Studio

1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to the cloned repository
4. Wait for Gradle sync to complete

### 3. Prepare Test Databases

Create the following folder structure on your Android device or emulator:

```
/storage/emulated/0/MedicalQuiz/
├── databases/
│   ├── quiz1.db
│   ├── quiz2.db
│   └── ...
└── media/
    └── (media files if needed)
```

### 4. Build and Run

```bash
# Build the project
./gradlew build

# Install on connected device
./gradlew installDebug

# Or use Android Studio's Run button
```

## Building from Command Line

### Debug Build

```bash
./gradlew assembleDebug
```

The APK will be generated at: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build

```bash
./gradlew assembleRelease
```

Note: You'll need to configure signing for release builds.

## Running Tests

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Run lint checks
./gradlew lint
```

## GitHub Actions CI/CD

This project includes a GitHub Actions workflow that automatically:

- ✅ Builds the app on every push and pull request
- ✅ Runs unit tests
- ✅ Performs lint checks
- ✅ Generates and uploads APK artifacts
- ✅ Uploads lint reports

The workflow runs on:
- Pushes to `main` and `develop` branches
- Pull requests targeting `main` and `develop` branches

## Permissions

The app requires the following permissions:

- `READ_EXTERNAL_STORAGE` (Android 6.0 - 12)
- `READ_MEDIA_IMAGES/VIDEO/AUDIO` (Android 13+)
- `MANAGE_EXTERNAL_STORAGE` (Optional, for full access)

## Technologies Used

- **Language**: Kotlin 1.9.20
- **Build System**: Gradle with Kotlin DSL
- **UI**: Material Design Components, ViewBinding
- **Architecture**: MVVM-ready structure
- **Database**: SQLite
- **Coroutines**: kotlinx-coroutines-android
- **Android Components**: 
  - AndroidX Core KTX
  - AppCompat
  - RecyclerView
  - Lifecycle Components

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Future Enhancements

- [ ] Quiz screen implementation
- [ ] Database schema validation
- [ ] Quiz progress tracking
- [ ] Score history
- [ ] Media file support for questions
- [ ] Export/Import functionality
- [ ] Dark theme support

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contact

For questions or support, please open an issue on GitHub.

---

**Note**: This is a base implementation. Quiz functionality, database schema, and additional features need to be implemented based on your specific requirements.
