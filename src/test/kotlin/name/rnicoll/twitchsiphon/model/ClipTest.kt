package name.rnicoll.twitchsiphon.model

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.Test

class ClipTest {
    companion object {
        val EXAMPLE_CLIP_JSON = """
            {
                "id": "DullRockyOryxBlarg",
                "url": "https://clips.twitch.tv/DullRockyOryxBlarg",
                "embed_url": "https://clips.twitch.tv/embed?clip=DullRockyOryxBlarg",
                "broadcaster_id": "12345678",
                "broadcaster_name": "rnicoll",
                "creator_id": "87654321",
                "creator_name": "creator",
                "video_id": "",
                "game_id": "509650",
                "language": "en",
                "title": "NYE Party",
                "view_count": 1,
                "created_at": "2020-01-01T05:22:00Z",
                "thumbnail_url": "https://twitch.example.org/preview-480x272.jpg"
            }
        """.trimIndent()
    }

    @Test
    fun `should parse from JSON`() {
        val objectMapper = jacksonObjectMapper()
        val actual = objectMapper.readValue(EXAMPLE_CLIP_JSON, Clip::class.java)
    }
}