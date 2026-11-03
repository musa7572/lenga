# StudyLock 📚

A modern Android application that helps users focus on studying by blocking distracting apps until educational content is completed. Built with Jetpack Compose, Kotlin, and Material Design 3.

## 🎯 Features

### Core Functionality
- **App Blocking System**: Blocks distracting apps using Device Admin API
- **Study Modules**: Structured learning content with prerequisites
- **Quiz System**: Knowledge assessment with reward-based unlocking
- **Progress Tracking**: Comprehensive analytics and achievement system
- **Content Reader**: Time-based reading completion tracking

### Study Management
- **Module-based Learning**: Organized study materials with difficulty levels
- **Prerequisites System**: Modules unlock based on completion of prerequisites
- **Quick Reference**: Fast access to formulas and key concepts
- **Study Timer**: Track study sessions with pause/resume functionality

### Gamification
- **Achievement System**: Earn badges for milestones
- **Study Streaks**: Daily tracking and rewards
- **Progress Analytics**: Detailed statistics and insights
- **Reward System**: Earn app access time through study completion

### Security & Accessibility
- **Emergency Unlock**: PIN-based bypass system
- **Accessibility Service**: Enhanced app blocking capabilities
- **Data Persistence**: Local storage with DataStore
- **Export/Import**: Backup and restore functionality

## 🏗️ Architecture

### Technology Stack
- **UI Framework**: Jetpack Compose with Material Design 3
- **Language**: Kotlin
- **Architecture**: MVVM with Repository pattern
- **Async**: Kotlin Coroutines & Flow
- **Persistence**: DataStore Preferences
- **Dependency Injection**: Manual DI with service connections

### Project Structure
```
app/src/main/java/com/example/studylock/
├── admin/                    # Device admin functionality
├── data/
│   ├── model/               # Data classes and entities
│   └── repository/          # Data access layer
├── service/                 # Background services
├── ui/
│   ├── components/          # Reusable UI components
│   ├── screens/             # Screen composables
│   ├── theme/               # Styling and colors
│   └── navigation/          # Navigation setup
├── viewmodel/               # ViewModels and state management
└── receiver/                # Broadcast receivers
```

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog | 2023.1.1 or later
- Android SDK API 26+ (targeting API 36)
- Kotlin 2.0.0+

### Installation
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Build and run on device or emulator

### Permissions Required
- **Device Administrator**: Required for app blocking functionality
- **Accessibility Service**: Enhanced app usage monitoring
- **Usage Stats**: App usage tracking and analytics
- **Foreground Service**: Background blocking service

## 📱 Usage

### First Time Setup
1. Grant Device Administrator permissions
2. Enable Accessibility Service (optional but recommended)
3. Set up emergency PIN
4. Choose study modules and start learning

### Study Flow
1. Select a study module from the dashboard
2. Read required content for minimum time
3. Complete quiz assessment (70%+ to pass)
4. Earn reward time for app access
5. Track progress and achievements

### Emergency Access
- Use emergency PIN when urgent access is needed
- Emergency usage is tracked for accountability
- Can be disabled/enabled in settings

## 🎨 UI Components

### Core Screens
- **StudyDashboardScreen**: Main dashboard with modules and progress
- **ContentReaderScreen**: Reading material with time tracking
- **QuizScreen**: Interactive quiz interface
- **ProgressScreen**: Analytics and achievements
- **EmergencyUnlockScreen**: PIN-based emergency access

### Reusable Components
- **StudyTimer**: Animated timer with progress indicator
- **QuizCard**: Question display with multiple choice
- **ModuleCard**: Study module representation
- **ProgressBar**: Animated progress indicators
- **LockedAppOverlay**: Blocking interface

## 📊 Data Models

### Key Entities
- **StudyModule**: Learning units with prerequisites
- **StudyContent**: Reading materials and resources
- **QuizState**: Quiz session management
- **ProgressData**: User progress statistics
- **Achievement**: Gamification badges

### Data Persistence
- **DataStore**: Modern replacement for SharedPreferences
- **JSON Serialization**: Complex data structures
- **Flow-based**: Reactive data updates

## 🔧 Configuration

### Build Configuration
- **compileSdk**: 36
- **minSdk**: 26
- **targetSdk**: 36
- **Kotlin**: 2.0.0
- **Compose BOM**: 2024.04.01

### Dependencies
```kotlin
// Core Android
implementation(libs.androidx.core.ktx)
implementation(libs.androidx.lifecycle.runtime.ktx)
implementation(libs.androidx.activity.compose)

// Compose
implementation(platform(libs.androidx.compose.bom))
implementation(libs.androidx.ui)
implementation(libs.androidx.material3)

// Navigation
implementation("androidx.navigation:navigation-compose:2.7.7")

// ViewModel
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

// DataStore
implementation("androidx.datastore:datastore-preferences:1.0.0")
```

## 🧪 Testing

### Unit Tests
- ViewModel logic testing
- Repository data operations
- Utility function validation

### UI Tests
- Compose UI testing
- Navigation flow testing
- Component interaction testing

## 📈 Performance

### Optimizations
- **Lazy Loading**: Efficient list rendering
- **Coroutines**: Non-blocking operations
- **Memory Management**: Proper service cleanup
- **Battery Efficiency**: Optimized background operations

### Analytics
- Study session tracking
- App usage patterns
- Quiz performance metrics
- Achievement progress

## 🔒 Security

### Data Protection
- **Local Storage**: No network data transmission
- **PIN Security**: Hashed emergency PIN storage
- **Permission Model**: Minimal required permissions
- **Privacy**: No personal data collection

### Device Security
- **Device Admin**: System-level app control
- **Accessibility**: Enhanced monitoring capabilities
- **Service Security**: Foreground service with notifications

## 🤝 Contributing

### Development Guidelines
1. Follow Kotlin coding conventions
2. Use Compose best practices
3. Implement proper error handling
4. Add unit tests for new features
5. Update documentation

### Code Style
- **Kotlin**: Official style guide
- **Compose**: Material Design 3 guidelines
- **Architecture**: Clean Architecture principles
- **Comments**: KDoc for public APIs

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

### Common Issues
- **Device Admin**: Must be enabled for blocking to work
- **Accessibility**: Required for enhanced features
- **Emergency PIN**: Keep it secure but memorable
- **Battery Optimization**: Exclude from battery saving

### Troubleshooting
1. Check all permissions are granted
2. Verify Device Admin is activated
3. Ensure Accessibility Service is running
4. Clear app cache if experiencing issues

## 🗺️ Roadmap

### Upcoming Features
- [ ] Cloud sync for progress data
- [ ] Collaborative study modules
- [ ] Advanced analytics dashboard
- [ ] Custom study schedules
- [ ] Integration with calendar apps
- [ ] Social features and leaderboards

### Technical Improvements
- [ ] Hilt dependency injection
- [ ] Room database integration
- [ ] Custom app blocking algorithms
- [ ] Machine learning for study patterns
- [ ] Offline-first architecture

## 📞 Contact

For questions, suggestions, or issues:
- Create an issue in the repository
- Check the troubleshooting guide
- Review the documentation

---

**StudyLock** - Transform your device from distraction to learning tool. 🎓
