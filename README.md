# Party-System X Beta

An experimental Android launcher for running **Vintage Story 1.21.6** natively on ARM64 phones, built on a fork of [dnbootstrap](https://github.com/VSMobile/dnbootstrap).

> ⚠️ **Beta.** Expect bugs. This is a fan project and is not affiliated with or endorsed by Anego Studios / the Vintage Story team.
> **No game files are included.** You must own Vintage Story and supply your own copy.

---

## What's new in this build

- **Tries OpenGL ES 3.2 first.** If 3.2 fails to start, the launcher falls back to **ES 3.1**, which boots to the main menu with only the known minor bugs.
- Previous builds targeted ES 3.1 only.
- <!-- Add any other changes here: branding, fixes, etc. -->

---

## Tested hardware

This build was designed and tested on:

| | |
|---|---|
| **Phone** | Moto G Power 5G (2024) |
| **Android** | 15 |
| **CPU** | MediaTek Dimensity 7020 (ARM64) |
| **GPU** | IMG BXM-8-256 |
| **Renderer** | OpenGL ES (`.gles` build). Vulkan is not used due to known driver issues on this GPU. |

Other ARM64 phones may work, but they are untested. A community compatibility list is planned.

---

## Requirements

- An ARM64 Android phone
- A **Vintage Story account** (purchased from the official site or itch.io)
- The **Vintage Story 1.21.6** Linux `.tar.gz` archive

---

## Installation

1. **Buy Vintage Story** from the official website or itch.io if you haven't already.
2. **Download the game archive.** Get the **Linux `.tar.gz`** for version **1.21.6** from wherever you purchased it, and save it to your phone.
3. **Download the APK** from the [Releases](../../releases) page. Install it, and allow installs from unknown sources if prompted.
4. **Launch the app** and select the Vintage Story `.tar.gz` archive when asked.
5. If the game **reaches the main menu**, log in with your Vintage Story account.

That's it. Control and touch-layout configuration will be documented in a future update.

---

## Technical architecture

Party-System X Beta is built as an Android/Gradle APK around the native `dnbootstrap` launcher stack.

Graphics path: Android/Gradle APK -> `:ltw` native compatibility layer -> GLFW/native graphics -> bundled ANGLE libraries -> native `dnbootstrap` launcher -> .NET nethost/hostfxr -> `Vintagestory.dll`.

### Rendering path

The launcher attempts OpenGL ES 3.2 first and falls back to OpenGL ES 3.1 when required by the device or driver.

### LTW

`ltw` (Large Thin Wrapper) provides the OpenGL compatibility layer used by the Android build, including graphics/context compatibility, extension handling, and shader-related adaptation.

### ANGLE

The Android application currently bundles `app/libs/angle.aar`, providing native ANGLE libraries for supported Android ABIs, including ARM64. The exact upstream ANGLE version and provenance are still being documented and should be recorded before a release build is treated as final.

### Project lineage and attribution

This project builds upon multiple upstream components:

1. **Party-System X Beta** — Bonkai Xengetsu / David J. Brown
2. **dnbootstrap** — VSMobile and contributors
3. **GLFW Android fork/submodule** — VSMobile/glfw, tracked under `app/src/main/cpp/glfw34`
4. **LTW** — MojoLauncher and contributors; upstream native source includes attribution to artDev, SerpentSpirale, and CADIndie
5. **ANGLE and other third-party components** — respective upstream licenses and attribution requirements apply
6. **Vintage Story** — Anego Studios; proprietary software. This project does not redistribute the game and is not affiliated with or endorsed by Anego Studios.

This section will be expanded as additional dependencies and exact upstream provenance are verified.

## Known issues

- <!-- List current minor bugs here -->

---

## Reporting bugs

Please open an [Issue](../../issues) and include your phone model, Android version, and whether the game started on ES 3.2 or fell back to 3.1.

---

## Credits

**Development**
- **Bonkai Xengetsu (David J. Brown)**: port work, builds, and testing
  <!-- optional: · Blackfeather Studios -->

**Built on**
- [dnbootstrap](https://github.com/VSMobile/dnbootstrap) by VSMobile and its contributors: the launcher this project is forked from

**Game**
- Vintage Story © Anego Studios. The name is used only to describe compatibility. This project is not affiliated with or endorsed by Anego Studios.

**Mods**
- <!-- Shader fix mod name --> by <!-- author --> (<!-- link -->): sky/terrain rendering fixes

**Tools**
- [Termux](https://termux.dev): on-device development environment
- GitHub Actions: APK builds

**AI assistance**
- Claude (Anthropic), ChatGPT (OpenAI), and NotebookLM (Google) helped with debugging, research, and documentation.

**Testers**
- <!-- Community testers go here as the compatibility list grows -->
