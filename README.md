
<div align="center">

# ☕ Java2Dex

**Convert Java source code to DEX files — entirely on your device.**

*Built for Modders • No PC Required • 100% Offline*

![Version](https://img.shields.io/badge/Version-1.0-16A34A?style=for-the-badge&logo=android&logoColor=white)
![Platform](https://img.shields.io/badge/Platform-Android%208%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-15803D?style=for-the-badge)
![Build](https://img.shields.io/badge/Build-GitHub%20Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)

</div>

---

## ✨ What is Java2Dex?

**Java2Dex** is a lightweight Android app designed for **modders and developers** who want to
compile Java source code directly into **`.dex`** files — without needing a PC, Android Studio,
or any build tools.

Just import your `.java` files (or a `.zip` archive), tap **Convert**, and get a ready-to-use
`classes.dex` saved automatically in the **JAVA2DEX** folder.

## ⚙️ How It Works

```
 .java files ──▶ [ ECJ Compiler ] ──▶ .class files ──▶ [ Google D8 ] ──▶ classes.dex ✔
```

| Stage | Tool | Description |
|---|---|---|
| Compile | **Eclipse ECJ** | Pure-Java compiler that runs on-device |
| Dexing | **Google D8** | Converts bytecode → DEX format |

## 🚀 Features

| # | Feature |
|---|---|
| 🗂️ | **Project Dashboard** — previous projects with status chips, dates & sizes |
| ➕ | **New Project Wizard** — name, source files, optional library jars |
| 📦 | **ZIP Import** — drop a zip, sources are auto-extracted |
| ⚡ | **One-Tap Conversion** — Java → .class → .dex in seconds |
| 💾 | **Auto-Save** — output saved to `JAVA2DEX/<project>/classes.dex` |
| 📄 | **Live Build Log** — full ECJ & D8 output, step by step |
| 📋 | **Copy Error** — one tap to grab the full error log |
| 🔁 | **Re-Convert** — rebuild any project instantly |
| ⬇️ | **Export to Downloads** — share your dex with other apps |
| 🔍 | **Search** — instantly filter your projects |
| 📊 | **Stats Header** — animated total / success / failed counters |
| 🧪 | **Sample Project** — test the pipeline with one tap |
| 🎨 | **Beautiful UI** — green theme, smooth animations, splash screen |
| 🔒 | **100% Offline** — everything runs on your device |

## 📱 Installation

1. Go to the [**Actions**](../../actions) tab
2. Open the latest **Java2Dex CI** run
3. Download the **Java2Dex-debug-apk** artifact
4. Install the APK *(enable "Install from unknown sources" if asked)*

## 📂 Output Location

```
Internal Storage ▸ Android ▸ data ▸ com.java2dex.app ▸ files ▸ JAVA2DEX ▸ <project> ▸ classes.dex
```

> 💡 Use **Export** on the project detail page to send your dex to
> `Downloads/Java2Dex` for easy access.

## 🛠️ Build From Source

```bash
git clone https://github.com/<your-username>/Java2Dex.git
cd Java2Dex
gradle :app:assembleDebug
```

Or simply push to GitHub — **GitHub Actions** builds the APK automatically.

## 🗺️ Roadmap

- [ ] APK packaging from DEX (full builder)
- [ ] Smali viewer
- [ ] Multi-dex support
- [ ] Dark mode theme

## 🤝 Contributing

Pull requests are welcome! For major changes, please open an issue first
to discuss what you would like to change.

---

<div align="center">

**Made with ❤️ for the modding community**

⭐ Star this repo if you find it useful!

</div>
