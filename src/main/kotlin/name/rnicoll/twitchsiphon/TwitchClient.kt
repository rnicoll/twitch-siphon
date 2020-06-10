package name.rnicoll.twitchsiphon

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import name.rnicoll.twitchsiphon.model.Clip
import name.rnicoll.twitchsiphon.model.ClipsResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import java.io.Closeable
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

class TwitchClient(
    private val accessToken: String,
    private val clientId: String
) : Closeable {
    companion object {
        private const val redirectUrl = "http://localhost"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val HEADER_CLIENT_ID = "Client-ID"
        private const val URL_AUTHORIZE = "https://id.twitch.tv/oauth2/authorize"
        private const val URL_CLIPS = "https://api.twitch.tv/helix/clips"
        private val rfcDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

        fun authorize(clientId: String, scope: String): URL = StringBuilder(URL_AUTHORIZE)
            .append("?client_id=")
            .append(URLEncoder.encode(clientId, "US-ASCII"))
            .append("&redirect_uri=")
            .append(URLEncoder.encode(redirectUrl, "US-ASCII"))
            .append("&response_type=token&scope=")
            .append(URLEncoder.encode(scope, "US-ASCII"))
            .toString()
            .let(::URL)
    }

    private val httpClient: CloseableHttpClient = HttpClients.createDefault();
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    override fun close() {
        httpClient.close()
    }

    fun getUserId(login: String): TwitchUserId {
        val res = HttpGet("https://api.twitch.tv/helix/users?login=$login").apply {
            setHeader(HEADER_CLIENT_ID, clientId)
            setHeader(HEADER_AUTHORIZATION, "Bearer $accessToken")
        }.run {
            httpClient.execute(this)
        }.entity.content
        return TwitchUserId.fromJson(objectMapper.readTree(res))
    }

    fun listClips(broadcasterId: TwitchUserId, startAt: Date, endedAt: Date): List<Clip> {
        val first = 50
        val startAtText = rfcDateFormat.format(startAt)
        val endedAtText = rfcDateFormat.format(endedAt)
        val clips: MutableList<Clip> = mutableListOf()
        var cursor: String? = null
        var readCount: Int
        do {
            var url =
                "$URL_CLIPS?broadcaster_id=${broadcasterId.id}&started_at=${startAtText}&ended_at=${endedAtText}&first=$first"
            if (cursor != null) {
                url += "&after=$cursor"
            }
            val res = HttpGet(url).apply {
                setHeader(HEADER_CLIENT_ID, clientId)
                setHeader(HEADER_AUTHORIZATION, "Bearer $accessToken")
            }.run {
                httpClient.execute(this)
            }
            val clipsResult: ClipsResponse = objectMapper.readValue(res.entity.content, ClipsResponse::class.java)
            cursor = clipsResult.pagination?.cursor
            readCount = clipsResult.data.size
            clipsResult.data.forEach { clips.add(it) }
        } while (readCount > 0 && (cursor?.length ?: 0) > 0)
        return clips
    }
}

data class TwitchUserId(val id: String, val login: String) {
    companion object {
        fun fromJson(jtok: JsonNode): TwitchUserId {
            val data = jtok.at("/data/0")
            val userId = data.at("/id").textValue()
            val login = data.at("/login").textValue()
            return TwitchUserId(userId, login)
        }
    }
}
