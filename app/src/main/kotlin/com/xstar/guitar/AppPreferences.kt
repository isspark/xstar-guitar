package com.xstar.guitar

import android.content.Context

/** Local-first state used by the learning flow. */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("xstar_app", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) { prefs.edit().putBoolean("onboarding_done", value).apply() }

    var tunerOpened: Boolean
        get() = prefs.getBoolean("tuner_opened", false)
        set(value) { prefs.edit().putBoolean("tuner_opened", value).apply() }

    val importedFiles: List<String>
        get() = prefs.getStringSet("imported_files", emptySet()).orEmpty().toList().sorted()

    fun addImportedFile(name: String, uri: String? = null) {
        val files = importedFiles.toMutableSet().apply { add(name) }
        val uris = prefs.getStringSet("imported_uris", emptySet()).orEmpty().toMutableSet()
        uri?.let { uris.add("$name|$it") }
        prefs.edit().putStringSet("imported_files", files).putStringSet("imported_uris", uris).apply()
    }

    fun uriFor(name: String): String? = prefs.getStringSet("imported_uris", emptySet()).orEmpty().firstOrNull { it.startsWith("$name|") }?.substringAfter('|')

    fun removeImportedFile(name: String) {
        val files = importedFiles.toMutableSet().apply { remove(name) }
        val uris = prefs.getStringSet("imported_uris", emptySet()).orEmpty().filterNot { it.startsWith("$name|") }.toSet()
        prefs.edit().putStringSet("imported_files", files).putStringSet("imported_uris", uris).apply()
    }

    val importedCollections: List<String>
        get() = prefs.getStringSet("imported_collections", emptySet()).orEmpty().toList().sorted()

    fun addImportedCollection(name: String, uris: List<String>) {
        val names = importedCollections.toMutableSet().apply { add(name) }
        val encoded = prefs.getStringSet("imported_collection_uris", emptySet()).orEmpty().toMutableSet()
        encoded.removeAll { it.startsWith("$name|") }
        encoded.add("$name|${uris.joinToString("||")}")
        prefs.edit().putStringSet("imported_collections", names).putStringSet("imported_collection_uris", encoded).apply()
    }

    fun urisForCollection(name: String): List<String> = prefs.getStringSet("imported_collection_uris", emptySet()).orEmpty()
        .firstOrNull { it.startsWith("$name|") }?.substringAfter('|')?.split("||").orEmpty()

    fun removeImportedCollection(name: String) {
        val names = importedCollections.toMutableSet().apply { remove(name) }
        val values = prefs.getStringSet("imported_collection_uris", emptySet()).orEmpty().filterNot { it.startsWith("$name|") }.toSet()
        prefs.edit().putStringSet("imported_collections", names).putStringSet("imported_collection_uris", values).apply()
    }

    fun renameImportedCollection(oldName: String, newName: String) {
        if (oldName == newName || newName.isBlank()) return
        val names = importedCollections.toMutableSet().apply {
            remove(oldName)
            add(newName)
        }
        val values = prefs.getStringSet("imported_collection_uris", emptySet()).orEmpty().toMutableSet()
        val entry = values.firstOrNull { it.startsWith("$oldName|") }
        values.removeAll { it.startsWith("$oldName|") || it.startsWith("$newName|") }
        entry?.substringAfter('|')?.let { values.add("$newName|$it") }
        prefs.edit().putStringSet("imported_collections", names).putStringSet("imported_collection_uris", values).apply()
    }
}
