package com.kaii.lavender.widgets.music_widget

import android.content.Context
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.VibratorManager

fun launchMediaPlayer(
    packageName: String,
    context: Context
) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)

    if (packageName.contains("spotify")) {
        launchIntent?.setAction("com.spotify.mobile.android.ui.action.player.SHOW")
    }

    context.applicationContext.startActivity(launchIntent)

    // hah vibrator
    val vibrator = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager

    vibrator.vibrate(
        CombinedVibration.createParallel(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    )
}