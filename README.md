# Count Wearables

Count Wearables is a futuristic-themed inventory management application designed for tracking clothing and wearable items. Featuring a high-contrast "cyberpunk" aesthetic with neon accents, it allows users to log, categorize, and manage their collection of digital-age apparel.

## Features

- **Item Inventory**: Track names, categories (sectors), quantities, sizes, and colors.
- **Data Logging**: Detailed metadata for each item, including timestamps and custom notes.
- **Cyberpunk UI**: A unique visual style using neon cyan and high-contrast dark themes.
- **Persistence**: Powered by Room database for offline data storage.
- **Image Support**: Visual identification of items using Glide for image loading.

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Android Material Components with ViewBinding
- **Architecture**: MVVM (ViewModel, LiveData)
- **Database**: Room
- **Image Loading**: Glide
- **Concurrency**: Kotlin Coroutines

## Project Structure

- `com.countwearables.app.ui`: Activity and Fragment classes.
- `com.countwearables.app.data`: Data models, Room DAOs, and Database configuration (likely).
- `com.countwearables.app.viewmodel`: ViewModel logic (likely).

## Getting Started

1. Clone the repository.
2. Open in Android Studio (Hedgehog or newer recommended).
3. Build and run on an Android device or emulator (API 24+).

## License

This project is licensed under the MIT License - see the LICENSE file for details.
