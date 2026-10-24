package com.idroid.app.utils

import android.content.Context
import com.idroid.app.model.DashboardRegistry

object DashboardPreferences {
    private const val PREFS_NAME = "idroid_dashboard_prefs"
    private const val KEY_ENABLED_CONTROLS = "enabled_controls"
    private const val KEY_PREFS_VERSION = "prefs_version_v3"
    private const val KEY_PILL_ORDER = "pill_order"
    private const val KEY_CIRCLE_ORDER = "circle_order"
    private const val KEY_UTILITY_ORDER = "utility_order"

    fun getEnabledControlIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val version = prefs.getInt(KEY_PREFS_VERSION, 0)
        if (version < 3) {
            prefs.edit()
                .putInt(KEY_PREFS_VERSION, 3)
                .putStringSet(KEY_ENABLED_CONTROLS, DashboardRegistry.DEFAULT_ENABLED_IDS)
                .apply()
            return DashboardRegistry.DEFAULT_ENABLED_IDS
        }
        val saved = prefs.getStringSet(KEY_ENABLED_CONTROLS, null)
        return saved ?: DashboardRegistry.DEFAULT_ENABLED_IDS
    }

    fun isControlEnabled(context: Context, controlId: String): Boolean {
        return getEnabledControlIds(context).contains(controlId)
    }

    fun setControlEnabled(context: Context, controlId: String, enabled: Boolean) {
        val current = getEnabledControlIds(context).toMutableSet()
        if (enabled) {
            current.add(controlId)
        } else {
            current.remove(controlId)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_ENABLED_CONTROLS, current).apply()
    }

    fun getPillOrder(context: Context, enabledIds: Set<String> = getEnabledControlIds(context)): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedRaw = prefs.getString(KEY_PILL_ORDER, null)
        val baseList = if (!savedRaw.isNullOrBlank()) {
            savedRaw.split(",").filter { it.isNotBlank() }
        } else {
            DashboardRegistry.DEFAULT_PILL_ORDER
        }

        // Keep saved order for currently enabled pills, then append any enabled pills missing from saved order
        val activeOrdered = baseList.filter { enabledIds.contains(it) }.toMutableList()
        DashboardRegistry.DEFAULT_PILL_ORDER.forEach { id ->
            if (enabledIds.contains(id) && !activeOrdered.contains(id)) {
                activeOrdered.add(id)
            }
        }
        return activeOrdered
    }

    fun setPillOrder(context: Context, order: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PILL_ORDER, order.joinToString(",")).apply()
    }

    fun getCircleOrder(context: Context, enabledIds: Set<String> = getEnabledControlIds(context)): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedRaw = prefs.getString(KEY_CIRCLE_ORDER, null)
        val baseList = if (!savedRaw.isNullOrBlank()) {
            savedRaw.split(",").filter { it.isNotBlank() }
        } else {
            DashboardRegistry.DEFAULT_CIRCLE_ORDER
        }

        val activeOrdered = baseList.filter { enabledIds.contains(it) }.toMutableList()
        DashboardRegistry.DEFAULT_CIRCLE_ORDER.forEach { id ->
            if (enabledIds.contains(id) && !activeOrdered.contains(id)) {
                activeOrdered.add(id)
            }
        }
        return activeOrdered
    }

    fun setCircleOrder(context: Context, order: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CIRCLE_ORDER, order.joinToString(",")).apply()
    }

    fun getUtilityOrder(context: Context, enabledIds: Set<String> = getEnabledControlIds(context)): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedRaw = prefs.getString(KEY_UTILITY_ORDER, null)
        val baseList = if (!savedRaw.isNullOrBlank()) {
            savedRaw.split(",").filter { it.isNotBlank() }
        } else {
            val pill = getPillOrder(context, enabledIds)
            val circle = getCircleOrder(context, enabledIds)
            pill + circle
        }

        val activeOrdered = baseList.filter { enabledIds.contains(it) }.toMutableList()
        DashboardRegistry.DEFAULT_UTILITY_ORDER.forEach { id ->
            if (enabledIds.contains(id) && !activeOrdered.contains(id)) {
                activeOrdered.add(id)
            }
        }
        return activeOrdered
    }

    fun setUtilityOrder(context: Context, order: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_UTILITY_ORDER, order.joinToString(",")).apply()
    }

    fun moveItem(list: List<String>, fromIndex: Int, toIndex: Int): List<String> {
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return list
        val mutable = list.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable
    }

    private const val KEY_SECTION_ORDER = "section_order"
    val DEFAULT_SECTION_ORDER = listOf(
        "connectivity_media",
        "hardware_sliders",
        "quick_controls",
        "clipboard_card"
    )

    fun getSectionOrder(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedRaw = prefs.getString(KEY_SECTION_ORDER, null)
        val baseList = if (!savedRaw.isNullOrBlank()) {
            savedRaw.split(",").filter { it.isNotBlank() }
        } else {
            DEFAULT_SECTION_ORDER
        }
        val mutable = baseList.toMutableList()
        DEFAULT_SECTION_ORDER.forEach { sec ->
            if (!mutable.contains(sec)) mutable.add(sec)
        }
        return mutable
    }

    fun setSectionOrder(context: Context, order: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SECTION_ORDER, order.joinToString(",")).apply()
    }

    fun resetToDefaults(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(KEY_ENABLED_CONTROLS, DashboardRegistry.DEFAULT_ENABLED_IDS)
            .remove(KEY_PILL_ORDER)
            .remove(KEY_CIRCLE_ORDER)
            .remove(KEY_UTILITY_ORDER)
            .remove(KEY_SECTION_ORDER)
            .apply()
    }
}
