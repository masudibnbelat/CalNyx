package com.calnyx.app.launcher

import android.content.Context
import android.content.SharedPreferences

object LauncherPrefs {
    private const val PREFS_NAME = "calnyx_launcher_prefs"
    private const val KEY_HIDDEN = "hidden_apps"
    private const val KEY_FAVORITES = "favorite_apps"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getStringSet(context: Context, key: String): Set<String> {
        return prefs(context).getStringSet(key, emptySet()) ?: emptySet()
    }

    private fun saveStringSet(context: Context, key: String, value: Set<String>) {
        prefs(context).edit().putStringSet(key, value).apply()
    }

    fun getHidden(context: Context): Set<String> {
        return getStringSet(context, KEY_HIDDEN)
    }

    fun isHidden(context: Context, packageName: String): Boolean {
        return getHidden(context).contains(packageName)
    }

    fun hideApp(context: Context, packageName: String) {
        val current = getHidden(context).toMutableSet()
        current.add(packageName)
        saveStringSet(context, KEY_HIDDEN, current)
    }

    fun unhideApp(context: Context, packageName: String) {
        val current = getHidden(context).toMutableSet()
        current.remove(packageName)
        saveStringSet(context, KEY_HIDDEN, current)
    }

    fun getFavorites(context: Context): Set<String> {
        return getStringSet(context, KEY_FAVORITES)
    }

    fun isFavorite(context: Context, packageName: String): Boolean {
        return getFavorites(context).contains(packageName)
    }

    fun toggleFavorite(context: Context, packageName: String) {
        val current = getFavorites(context).toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        saveStringSet(context, KEY_FAVORITES, current)
    }
}
