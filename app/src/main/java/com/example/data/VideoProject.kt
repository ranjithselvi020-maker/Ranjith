package com.example.data

import androidx.room.*
import org.json.JSONArray
import org.json.JSONObject

data class VideoScene(
    val id: String,
    val visualPresetId: String,
    val durationSeconds: Int,
    val captionText: String,
    val speedMultiplier: Float = 1.0f,
    val suggestedPrompt: String = "",
    val stickerId: String = ""
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("visualPresetId", visualPresetId)
        obj.put("durationSeconds", durationSeconds)
        obj.put("captionText", captionText)
        obj.put("speedMultiplier", speedMultiplier.toDouble())
        obj.put("suggestedPrompt", suggestedPrompt)
        obj.put("stickerId", stickerId)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): VideoScene {
            return VideoScene(
                id = obj.optString("id", System.currentTimeMillis().toString()),
                visualPresetId = obj.optString("visualPresetId", "nature_lake"),
                durationSeconds = obj.optInt("durationSeconds", 4),
                captionText = obj.optString("captionText", ""),
                speedMultiplier = obj.optDouble("speedMultiplier", 1.0).toFloat(),
                suggestedPrompt = obj.optString("suggestedPrompt", ""),
                stickerId = obj.optString("stickerId", "")
            )
        }
    }
}

@Entity(tableName = "video_projects")
data class VideoProject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val moodName: String, // e.g. "DEFAULT", "CINEMATIC", "RETRO", "CYBERPUNK", "LOFI", "POP"
    val musicTrackName: String, // e.g. "NONE", "LOFI_BEATER", "ACOUSTIC", "SYNTHWAVE", "ORCHESTRAL"
    val scenesJson: String, // JSON Array representation of VideoScene list
    val createdAt: Long = System.currentTimeMillis(),
    val isExported: Boolean = false,
    val exportedFilePath: String = ""
) {
    @Ignore
    private var cachedScenes: List<VideoScene>? = null

    fun getScenes(): List<VideoScene> {
        if (cachedScenes != null) return cachedScenes!!
        val list = mutableListOf<VideoScene>()
        try {
            val arr = JSONArray(scenesJson)
            for (i in 0 until arr.length()) {
                list.add(VideoScene.fromJsonObject(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (list.isEmpty()) {
            // Default initial scene so the video is never empty
            list.add(VideoScene("scene_1", "nature_lake", 4, "Hello world!", 1.0f, "A beautiful tranquil lake surrounded by silent high mountains during sunrise"))
        }
        cachedScenes = list
        return list
    }

    companion object {
        fun createScenesJson(scenes: List<VideoScene>): String {
            val arr = JSONArray()
            scenes.forEach { arr.put(it.toJsonObject()) }
            return arr.toString()
        }
    }
}
