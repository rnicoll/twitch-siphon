package name.rnicoll.twitchsiphon.model

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.Test

class ClipsResponseTest {
    companion object {
        val EXAMPLE_JSON = "{\"data\": [${ClipTest.EXAMPLE_CLIP_JSON}]}"
    }

    @Test
    fun `should parse from JSON`() {
        val objectMapper = jacksonObjectMapper()
        val actual = objectMapper.readValue(EXAMPLE_JSON, ClipsResponse::class.java)
    }
}