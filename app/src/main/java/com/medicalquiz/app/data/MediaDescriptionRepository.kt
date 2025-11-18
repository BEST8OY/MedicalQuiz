package com.medicalquiz.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object MediaDescriptionRepository {
    private const val FILE_NAME = "media_descriptions.json"

    fun load(context: Context): Map<String, MediaDescription> {
        val json = runCatching {
            context.assets.open(FILE_NAME).bufferedReader().use { it.readText() }
        }.getOrElse { return emptyMap() }

        val array = JSONArray(json)
        val entries = mutableMapOf<String, MediaDescription>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val imageName = obj.optString("image_name")
            val description = obj.optString("description")
            if (imageName.isBlank() || description.isBlank()) continue
            val title = obj.optString("title")
            entries[imageName] = MediaDescription(
                imageName = imageName,
                title = title,
                description = description
            )
        }
        return entries
    }
}
