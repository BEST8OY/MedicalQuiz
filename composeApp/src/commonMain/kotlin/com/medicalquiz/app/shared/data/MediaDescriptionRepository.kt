package com.medicalquiz.app.shared.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import medicalquiz.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

object MediaDescriptionRepository {
    private var cachedDescriptions: Map<String, MediaDescription> = emptyMap()

    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(): Map<String, MediaDescription> {
        if (cachedDescriptions.isNotEmpty()) return cachedDescriptions

        try {
            // Read from Compose Resources
            val bytes = Res.readBytes("files/media_descriptions.json")
            val jsonString = bytes.decodeToString()
            
            val array = Json.parseToJsonElement(jsonString).jsonArray
            val entries = mutableMapOf<String, MediaDescription>()
            
            for (element in array) {
                val obj = element.jsonObject
                val imageName = obj["image_name"]?.jsonPrimitive?.content ?: continue
                val description = obj["description"]?.jsonPrimitive?.content ?: continue
                
                if (imageName.isBlank() || description.isBlank()) continue
                
                val title = obj["title"]?.jsonPrimitive?.content ?: ""
                
                entries[imageName] = MediaDescription(
                    imageName = imageName,
                    title = title,
                    description = description
                )
            }
            cachedDescriptions = entries
        } catch (e: Exception) {
            println("Error loading media descriptions: ${e.message}")
            e.printStackTrace()
        }
        
        return cachedDescriptions
    }
}
