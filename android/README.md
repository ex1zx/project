# Hand Signal (Android)

كاميرا أمامية كالمرآة + كشف كف يد واحد عبر MediaPipe + عدّ الأصابع + إرسال الرقم (1–5) عبر USB Serial إلى الأردوينو. يعمل محليًا بالكامل.

## البناء

```bash
cd android
gradle assembleDebug
```

أو سحابيًا: GitHub → Actions → "Build APK" → Run workflow → حمّل Artifact باسم `HandSignal-apk`.

## البنية

- `MainActivity.kt` — الصلاحيات، CameraX، MediaPipe، عدّ الأصابع، إرسال مستقر.
- `HandOverlayView.kt` — رسم هيكل اليد بخطوط منعّمة مع توهج.
- `UsbSerialManager.kt` — طلب صلاحية USB والاتصال بسرعة 9600.
- نموذج `hand_landmarker.task` يُنزَّل تلقائيًا إلى `assets/` أثناء البناء.
