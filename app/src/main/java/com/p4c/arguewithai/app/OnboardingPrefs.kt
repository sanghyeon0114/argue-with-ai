package com.p4c.arguewithai.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.edit
import androidx.core.net.toUri
import com.p4c.arguewithai.platform.accessibility.MyAccessibilityService

object OnboardingPrefs {
    private const val PREFS = "app_prefs"
    private const val KEY_PARTICIPANT_NAME = "participant_name"
    private const val KEY_PIP_CONFIRMED = "pip_setup_confirmed"
    private const val KEY_INTERVENTION_TYPE = "intervention_type"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getCachedName(context: Context): String? =
        prefs(context).getString(KEY_PARTICIPANT_NAME, null)?.takeIf { it.isNotBlank() }

    fun setCachedName(context: Context, name: String) {
        prefs(context).edit { putString(KEY_PARTICIPANT_NAME, name) }
    }

    fun isOverlayGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isAccessibilityGranted(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val target = ComponentName(context, MyAccessibilityService::class.java)
        return enabled.any {
            val s = it.resolveInfo.serviceInfo
            s.packageName == target.packageName && s.name == target.className
        }
    }

    fun isPipConfirmed(context: Context): Boolean = prefs(context).getBoolean(KEY_PIP_CONFIRMED, false)

    fun setPipConfirmed(context: Context, confirmed: Boolean) {
        prefs(context).edit { putBoolean(KEY_PIP_CONFIRMED, confirmed) }
    }

    fun getInterventionType(context: Context): Int? {
        val value = prefs(context).getInt(KEY_INTERVENTION_TYPE, -1)
        return value.takeIf { it in 0..2 }
    }

    fun setInterventionType(context: Context, type: Int) {
        prefs(context).edit { putInt(KEY_INTERVENTION_TYPE, type) }
    }

    fun isOnboardingComplete(context: Context): Boolean {
        return getCachedName(context) != null &&
            isOverlayGranted(context) &&
            isAccessibilityGranted(context) &&
            isPipConfirmed(context) &&
            getInterventionType(context) != null
    }

    fun requestOverlayPermission(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${activity.packageName}".toUri()
        )
        activity.startActivity(intent)
    }

    fun openAccessibilitySettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { activity.startActivity(intent) }
    }

    fun openPipSettingsForApp(context: Context, packageName: String) {
        try {
            val intent = Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS").apply {
                data = "package:$packageName".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:$packageName".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }
}
