package com.project.handusb

import android.os.Handler
import android.os.HandlerThread

/**
 * مُرسل الإشارة المستمر.
 *
 * القاعدة:
 *  - طالما هناك عدد أصابع مرفوعة (1..5) يبقى الإرسال مستمراً بدون انقطاع.
 *  - عند تغيّر عدد الأصابع ينتقل فوراً إلى الإشارة الجديدة ويستمر بها.
 *  - عند اختفاء اليد بالكامل أو عندما يصبح العدد صفراً يتوقف الإرسال.
 */
class SignalSender(
    private val usb: UsbSerialManager,
    private val intervalMs: Long = 80L,
    private val onValueChanged: (Int) -> Unit = {}
) {
    private val thread = HandlerThread("signal-sender").apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile private var value: Int = 0
    @Volatile private var running = false

    private val loop = object : Runnable {
        override fun run() {
            val v = value
            if (v in 1..5) {
                usb.send(v)
                handler.postDelayed(this, intervalMs)
            } else {
                running = false
            }
        }
    }

    /** يضبط الإشارة الحالية؛ يبدأ/يستمر الإرسال تلقائياً. */
    fun setValue(newValue: Int) {
        val v = newValue.coerceIn(0, 5)
        val changed = v != value
        value = v

        if (changed) {
            onValueChanged(v)
            if (v == 0) {
                // اختفاء اليد أو صفر أصابع: إيقاف الإرسال (مع إشعار واحد بالتوقف)
                handler.removeCallbacks(loop)
                running = false
                handler.post { usb.send(0) }
                return
            }
            // تغيّر العدد: انتقال فوري للإشارة الجديدة بدون انقطاع
            handler.removeCallbacks(loop)
            running = true
            handler.post(loop)
            return
        }

        // نفس القيمة: تأكد أن حلقة الإرسال ما زالت تعمل
        if (v > 0 && !running) {
            running = true
            handler.post(loop)
        }
    }

    fun currentValue(): Int = value

    fun stop() {
        handler.removeCallbacks(loop)
        running = false
        thread.quitSafely()
    }
}
