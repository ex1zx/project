package com.project.handusb

import android.os.Handler
import android.os.HandlerThread
import android.os.Process

/**
 * مُرسل الإشارة اللحظي (بدون أي تأخير):
 *  - أي تغيّر في عدد الأصابع يُرسل فوراً على خيط ذي أولوية عالية.
 *  - تستمر إشارة الاستمرارية (keep-alive) بفاصل قصير جداً للحفاظ على الاتصال.
 *  - عند صفر أصابع أو اختفاء اليد يتوقف الإرسال فوراً.
 */
class SignalSender(
    private val usb: UsbSerialManager,
    private val keepAliveMs: Long = 25L,
    private val onValueChanged: (Int) -> Unit = {}
) {
    private val thread = HandlerThread("signal-sender", Process.THREAD_PRIORITY_URGENT_AUDIO)
        .apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile private var value: Int = 0
    @Volatile private var running = false

    private val loop = object : Runnable {
        override fun run() {
            val v = value
            if (v in 1..5) {
                usb.send(v)
                handler.postDelayed(this, keepAliveMs)
            } else {
                running = false
            }
        }
    }

    /** يضبط الإشارة الحالية ويرسلها فوراً. */
    fun setValue(newValue: Int) {
        val v = newValue.coerceIn(0, 5)
        val changed = v != value
        value = v

        if (changed) {
            onValueChanged(v)
            handler.removeCallbacks(loop)
            if (v == 0) {
                running = false
                handler.postAtFrontOfQueue { usb.send(0) }
                return
            }
            running = true
            // إرسال فوري للقيمة الجديدة ثم متابعة الاستمرارية
            handler.postAtFrontOfQueue { usb.send(v) }
            handler.postDelayed(loop, keepAliveMs)
            return
        }

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
