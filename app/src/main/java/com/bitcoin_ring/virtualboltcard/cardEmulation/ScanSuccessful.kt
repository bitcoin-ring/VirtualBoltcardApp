package com.bitcoin_ring.virtualboltcard.cardEmulation

import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bitcoin_ring.virtualboltcard.R

class ScanSuccessful : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_successful)
        val imageView = findViewById<ImageView>(R.id.imageView2)
        imageView.setImageResource(R.drawable.successfulscan)
        val animation = imageView.drawable as AnimationDrawable
        animation.start()
        notifySound()
        notifyVibrate()
        Handler(Looper.getMainLooper()).postDelayed({
            // Remove the overlay view
            Log.d("scan_successfull", "finish")
            finish()
        }, 3000)  // 5000 milliseconds = 5 seconds

    }

    fun notifySound(){
        val notificationSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = RingtoneManager.getRingtone(this, notificationSoundUri)
        ringtone.play()
        Handler(Looper.getMainLooper()).postDelayed({
            // Remove the overlay view
            Log.d("ringScan", "stopping sound")
            ringtone.stop()
        }, 1000)  // 5000 milliseconds = 5 seconds
    }
    fun notifyVibrate(){
        val vibrator = this.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        if (Build.VERSION.SDK_INT >= 26) {
            // Vibrate with a given pattern.
            // Pass in an array of ints that are the durations for which to turn on or off the vibrator in milliseconds.
            // The first value indicates the number of milliseconds to wait before turning the vibrator on.
            // The next value indicates the number of milliseconds for which to keep the vibrator on before turning it off.
            // Subsequent values alternate between durations in milliseconds to turn the vibrator off or to turn the vibrator on.
            // The 'amplitude' parameter is the strength of the vibration. This must be a value between 1 and 255, or -1 to use the system default.
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 1000), -1))
        } else {
            //deprecated in API 26
            vibrator.vibrate(longArrayOf(0, 100, 1000), -1)
        }
    }
}