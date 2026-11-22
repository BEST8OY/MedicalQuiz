package com.medqb.app.shared.data

import com.medqb.app.shared.di.AppScope
import com.medqb.app.shared.platform.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.medqb.app.shared.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.concurrent.Volatile

@Inject
@SingleIn(AppScope::class)
class MediaDescriptionRepository {
    @Volatile
    private var cachedDescriptions: Map<String, MediaDescription>? = null
    private val mutex = Mutex()

    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(): Map<String, MediaDescription> {
        cachedDescriptions?.let { return it }

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                cachedDescriptions?.let { return@withLock it }

                try {
                    val bytes = Res.readBytes("files/media_descriptions.json")
                    val jsonString = bytes.decodeToString()

                    val array = Json.parseToJsonElement(jsonString).jsonArray
                    val entries = mutableMapOf<String, MediaDescription>()

                    for (element in array) {
                        val obj = element.jsonObject
                        val imageName = obj["image_name"]?.jsonPrimitive?.contentOrNull ?: continue
                        val description = obj["description"]?.jsonPrimitive?.contentOrNull ?: continue

                        if (imageName.isBlank() || description.isBlank()) continue

                        val title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty()

                        entries[imageName] = MediaDescription(
                            imageName = imageName,
                            title = title,
                            description = description
                        )
                    }
                    cachedDescriptions = entries
                    entries
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e("MediaDescriptionRepository", "Failed to load media descriptions", e)
                    emptyMap()
                }
            }
        }
    }
}
