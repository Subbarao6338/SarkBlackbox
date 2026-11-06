package top.niunaijun.blackboxa.util

import android.content.Context
import androidx.core.content.edit

object HiddenAppsManager {
    private const val PREF_NAME = "sark_hidden_apps"

    fun getHiddenApps(context: Context, userId: Int): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet("hidden_apps_$userId", emptySet()) ?: emptySet()
    }

    fun isAppHidden(context: Context, userId: Int, packageName: String): Boolean {
        return getHiddenApps(context, userId).contains(packageName)
    }

    fun hideApp(context: Context, userId: Int, packageName: String) {
        val current = getHiddenApps(context, userId).toMutableSet()
        current.add(packageName)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putStringSet("hidden_apps_$userId", current) }
    }

    fun unhideApp(context: Context, userId: Int, packageName: String) {
        val current = getHiddenApps(context, userId).toMutableSet()
        current.remove(packageName)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putStringSet("hidden_apps_$userId", current) }
    }
}
