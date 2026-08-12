# PROJECT — تطبيق أندرويد (يعمل محلياً بالكامل)

- يفتح الكاميرا الأمامية كالمرآة، يتتبع كف يد واحد ويرسم عليه خطوط الهيكل حتى أطراف الأصابع.
- رقم صغير في زاوية الشاشة يمثل عدد الأصابع المرفوعة (0–5).
- يرسل الرقم عبر USB Serial (9600 baud, سطر مثل `3\n`) إلى الأردوينو عند تغيّره.
- لا يحتاج إنترنت: نموذج MediaPipe مدمج داخل التطبيق في `app/src/main/assets/hand_landmarker.task`.

## البناء سحابياً (بدون لابتوب)
1. افتح تبويب **Actions** في المستودع.
2. شغّل workflow باسم **Build PROJECT APK** (زر Run workflow) أو انتظر البناء التلقائي بعد أي تعديل.
3. بعد انتهاء البناء، نزّل ملف **PROJECT-apk** من قسم Artifacts وثبّت `app-debug.apk` على الهاتف.

## كود الأردوينو المقابل
```cpp
void setup(){ Serial.begin(9600); pinMode(LED_BUILTIN, OUTPUT); }
void loop(){
  if (Serial.available()) {
    int n = Serial.parseInt();
    if (n >= 0 && n <= 5) { /* استخدم n هنا */ }
  }
}
```
