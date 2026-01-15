# RgDroid 

**RgDroid** is a high-performance code search utility for Android developers. 

Unlike standard file managers or editors that freeze on large folders, RgDroid parses gigabytes of source code in milliseconds using a native binary bridge.

![Screenshot](https://via.placeholder.com/800x400.png?text=Add+Your+Screenshots+Here)
##  Features

* **⚡ Native Performance:** Runs a statically linked `aarch64` ripgrep binary directly on the Android Kernel.
* ** Power Search:** Supports Regex, Case Insensitivity, Hidden Files, and Binary filtering.
* ** Modern UI:** Built 100% with **Jetpack Compose** and Material 3.
* ** Syntax Highlighting:** Integrated **Sora-Editor** (used in Acode) for reading code with full Java/Kotlin highlighting.
* ** Smart Navigation:** Native Folder Picker and file size safety checks (warns before opening 10MB+ files).
* ** Binary Safety:** Automatically detects and prevents opening `.apk`, `.dex`, or `.so` files to avoid crashes.

##  Tech Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material 3)
* **Search Engine:** [ripgrep](https://github.com/BurntSushi/ripgrep) (v15.0.0 aarch64-musl)
* **Code Editor:** [Sora-Editor](https://github.com/Rosemoe/sora-editor)
* **JSON Parsing:** Gson
* **Architecture:** MVVM-ish (State Hoisting)

##  Installation

Go to the [Releases](https://github.com/yourusername/RgDroid/releases) page to download the latest APK.

##  Building from Source

1.  Clone the repository:
    ```bash
    git clone [https://github.com/yourusername/RgDroid.git](https://github.com/yourusername/RgDroid.git)
    ```
2.  Open in **Android Studio** (or AndroidIDE on mobile).
3.  Sync Gradle dependencies.
4.  Build and Run.

**Note:** The `ripgrep` binary is already bundled in `app/src/main/jniLibs/arm64-v8a/librg.so`.

##  Contributing

Pull requests are welcome! For major changes, please open an issue first to discuss what you would like to change.

1.  Fork the Project
2.  Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3.  Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4.  Push to the Branch (`git push origin feature/AmazingFeature`)
5.  Open a Pull Request

##  License

Distributed under the MIT License. See `LICENSE` for more information.

