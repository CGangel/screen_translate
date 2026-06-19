# 屏幕实时翻译

Android app for automatic screen translation.

The app keeps a user-approved MediaProjection session while translation is
running. Realtime mode detects screen text changes automatically. Click mode
shows a floating translate button and translates the current screen on demand.
Both modes use local ML Kit OCR and call an OpenAI-compatible
`/v1/chat/completions` API.

Required runtime permissions:

- Floating window permission
- Screen capture permission when starting translation
- Notification permission on Android 13+

## Build

```powershell
.\gradlew.bat assembleDebug
```
