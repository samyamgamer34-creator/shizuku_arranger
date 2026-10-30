# Call Recordings (arranger)

A small Android app that lists, searches and plays the recordings made by
[ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder) — no more digging
through a file manager. It doesn't use Shizuku itself; it just reads the folder.

## Features

- Reads `/storage/emulated/0/call_recordings` (sub-folders included; folder can be changed via ⚙).
- Understands the recorder's default name format `{date}_{direction}_{phone_number}`,
  e.g. `20240131_154502.123+0530_in_+15551234567.ogg`.
- Shows the **saved contact name** for known numbers (asks for Contacts permission once;
  if you decline, it just shows numbers).
- Recordings grouped by day (Today / Yesterday / date) with direction, time, duration and size.
- **Search** by contact name, phone number (punctuation ignored, so `555-123` finds `+1555123…`), date
  (`2024-01-31`, `31/01/2024`, `jan`, `monday`), time (`14:30`) or direction (`incoming`, `out`).
  Multiple words must all match, e.g. `jan 555`.
- Filter chips: All / Incoming / Outgoing; sort by newest, oldest, longest, or number.
- "All calls with this number" shortcut from each row's menu.
- Built-in player with seek bar, ±10 s and speed (0.5x / 1x / 1.5x / 2x); share or delete a recording.

## Install

Download the latest APK straight on your phone (no GitHub login needed):

**https://github.com/samyamgamer34-creator/shizuku_arranger/releases/latest/download/CallRecordings.apk**

Open it and install (allow "install unknown apps" when asked). Every push rebuilds it.

On first launch, tap **Grant access** and enable **All files access** — Android requires it
to read another app's audio files. You can also allow **Contacts** to see names. The app has no internet permission.

## Build locally

Needs JDK 17 and the Android SDK (or open the folder in Android Studio):

```bash
./gradlew assembleRelease
```
