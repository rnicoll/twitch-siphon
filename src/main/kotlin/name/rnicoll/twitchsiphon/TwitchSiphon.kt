package name.rnicoll.twitchsiphon

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import name.rnicoll.twitchsiphon.exception.DownloadException
import name.rnicoll.twitchsiphon.model.Clip
import name.rnicoll.twitchsiphon.model.Config
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.*
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

fun main() {
    val objectMapper = jacksonObjectMapper()
    val configFile = Paths.get(TwitchSiphon.CONFIG_FILENAME).toFile()
    if (!configFile.exists()) {
        System.err.println("Configuration file $configFile does not exist.")
        exitProcess(1)
    } else {
        val config = try {
            objectMapper.readValue(configFile, Config::class.java)
        } catch (ex: IOException) {
            System.err.println("Failed to read configuration: ${ex.message}")
            exitProcess(1)
        }
        TwitchSiphon(config).use {
            it.run()
        }
    }
}

/**
 * Twitch siphon application itself.
 *
 * @property config the configuration for the application, used to determine how to
 * authenticate against Twitch APIs, and which broadcaster to pull clips from.
 */
class TwitchSiphon(
    private val config: Config,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) : Closeable {
    companion object {
        const val CONFIG_FILENAME = "config.json"
        private const val STATUS_OK = 200
        private const val TIMEOUT = 5 // Seconds

        fun guessDownloadUrl(clip: Clip): URI {
            val thumbnailUrl = URL(clip.thumbnailUrl)
            return if (thumbnailUrl.path.contains("AT-cm")) {
                thumbnailUrl.path
                    .split("-")
                    .subList(0, 2)
                    .joinToString("-")
                    .plus(".mp4")
            } else {
                thumbnailUrl.path
                    .split("-preview-")
                    .first()
                    .plus(".mp4")
            }.let { filename ->
                URL(thumbnailUrl, filename).toURI()
            }
        }
    }

    private val logger: Logger = LogManager.getLogger("TwitchSiphon")
    private val folderFormat = SimpleDateFormat("yyyy-MM-dd")
    private val httpClient: CloseableHttpClient = HttpClients.createDefault()
    private val twitchClient = TwitchClient(config.accessToken, config.clientId)
    private val requestConfig = RequestConfig.custom()
        .setConnectTimeout(TIMEOUT * 1000)
        .setConnectionRequestTimeout(TIMEOUT * 1000)
        .setSocketTimeout(TIMEOUT * 1000)
        .build();

    override fun close() {
        twitchClient.close()
        httpClient.close()
    }

    fun run() {
        val broadcaster = twitchClient.getUserId(config.broadcaster)
        val calendar = Calendar.getInstance().apply {
            set(config.startYear, Calendar.JANUARY, 1)
        }
        val endAt = Calendar.getInstance().apply {
            time = Date(System.currentTimeMillis())
        }

        while (calendar < endAt) {
            val currentStartedAt = calendar.time
            calendar.add(Calendar.DATE, 7)
            val currentEndedAt = calendar.time
            logger.info("Fetching $currentStartedAt")
            val folder = Paths.get(folderFormat.format(currentStartedAt))
            folder.toFile().apply {
                if (!exists()) {
                    mkdir()
                }
                if (!isDirectory) {
                    throw IllegalStateException("Path $folder is not a directory")
                }
            }

            val clips = twitchClient.listClips(broadcaster, currentStartedAt, currentEndedAt)
            logger.info("Got ${clips.size} clips")
            clips.forEach {
                downloadAll(it, folder)
            }
        }
    }

    fun downloadAll(clip: Clip, folder: Path) {
        val imageFile = folder.resolve("${clip.id}.jpg").toFile()
        val jsonFile = folder.resolve("${clip.id}.json").toFile()
        try {
            if (jsonFile.exists()) {
                logger.info("Skipping ${clip.id} because $jsonFile exists")
            } else {
                try {
                    downloadPreview(clip, imageFile)
                } catch (ex: DownloadException) {
                    // Don't care, not a critical file
                }
                try {
                    downloadClip(clip, folder, jsonFile)
                } catch (ex: DownloadException) {
                    logger.info("Could not download ${ex.url} for ${clip.id} based on ${clip.thumbnailUrl}")
                    logger.error("Could not download ${ex.url} for ${clip.id} based on ${clip.thumbnailUrl}")
                }
            }
        } catch (ex: IOException) {
            logger.info("Error downloading clip ${clip.id}")
            if (jsonFile.exists()) {
                jsonFile.delete()
            }
        }
    }

    fun downloadClip(clip: Clip, folder: Path, jsonFile: File) {
        val downloadUrl: URI = guessDownloadUrl(clip)
        logger.info("Downloading $downloadUrl for ${clip.id}")
        val videoFile = folder.resolve("${clip.id}.mp4").toFile()
        val res = HttpGet(downloadUrl)
            .run {
                config = requestConfig
                httpClient.execute(this)
            }
        if (res.statusLine.statusCode == STATUS_OK) {
            val outputStream = BufferedOutputStream(FileOutputStream(videoFile))
            res.entity.writeTo(outputStream)
            outputStream.close()
            objectMapper.writeValue(jsonFile, clip)
        } else {
            throw DownloadException(downloadUrl)
        }
    }

    fun downloadPreview(clip: Clip, imageFile: File) {
        logger.info("Downloading ${clip.thumbnailUrl} for ${clip.id}")
        val thumbnailUrl = URL(clip.thumbnailUrl).toURI()
        val res = HttpGet(thumbnailUrl)
            .run {
                config = requestConfig
                httpClient.execute(this)
            }
        if (res.statusLine.statusCode == STATUS_OK) {
            BufferedOutputStream(FileOutputStream(imageFile)).use { outputStream ->
                res.entity.writeTo(outputStream)
            }
        } else {
            throw DownloadException(thumbnailUrl)
        }
    }
}

