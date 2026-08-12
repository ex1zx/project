# PROJECT Android APK

This folder is the native Android build for PROJECT. It works offline at runtime:

- Camera frames stay on the phone.
- MediaPipe Hand Landmarker runs locally from `app/src/main/assets/hand_landmarker.task`.
- A single hand is tracked and drawn with smoothed landmark lines.
- Raised fingers are counted from 1 to 5.
- The count is sent as `1\n` through the first available USB bulk-out endpoint.

## Build in GitHub Actions

The repository workflow downloads the model during the build, compiles a release APK, and publishes it as a workflow artifact. The model is not downloaded by the installed app.

## Arduino USB note

The Android USB host connection looks for a bulk OUT endpoint, so common Arduino USB serial boards using CDC, FTDI, or CH340-style bulk endpoints are supported. The Arduino should read newline-terminated ASCII values.