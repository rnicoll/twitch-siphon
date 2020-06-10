package name.rnicoll.twitchsiphon

import com.fasterxml.jackson.databind.ObjectMapper
import name.rnicoll.twitchsiphon.exception.DownloadException
import name.rnicoll.twitchsiphon.model.Clip
import name.rnicoll.twitchsiphon.model.Config
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import java.io.*
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*

fun main() {
    val objectMapper = ObjectMapper()
    val configFile = Paths.get(TwitchSiphon.CONFIG_FILENAME).toFile()
    if (!configFile.exists()) {
        System.err.println("Configuration file $configFile does not exist.")
    } else {
        val config = objectMapper.readValue(configFile, Config::class.java)
        TwitchSiphon(config).use {
            it.run()
        }
    }
}

class TwitchSiphon(
    private val config: Config,
    private val objectMapper: ObjectMapper = ObjectMapper()
) : Closeable {
    companion object {
        const val CONFIG_FILENAME = "config.json"
        private const val STATUS_OK = 200
        private const val TIMEOUT = 5 // Seconds

        fun guessDownloadUrl(clip: Clip)
                = if (clip.thumbnailUrl.path.contains("AT-cm")) {
            clip.thumbnailUrl.path
                .split("-")
                .subList(0, 2)
                .joinToString("-")
                .plus(".mp4")
        } else {
            clip.thumbnailUrl.path
                .split("-preview-")
                .first()
                .plus(".mp4")
        }.let { filename ->
            clip.thumbnailUrl.resolve(filename)
        }
    }

    private val folderFormat = SimpleDateFormat("yyyy-MM-dd")
    private val errorLog: BufferedWriter = BufferedWriter(FileWriter(File("errors.txt"), true))
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
        errorLog.close()
    }

    fun run() {
        val broadcaster = twitchClient.getUserId(config.broadcasterLogin)
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
            println("Fetching $currentStartedAt")
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
            println("Got ${clips.size} clips")
            clips.forEach {
                downloadAll(it, folder)
            }
        }
    }

    fun downloadAll(clip: Clip, folder: Path) {
        val imageFile = folder.resolve("${clip.slug}.jpg").toFile()
        val jsonFile = folder.resolve("${clip.slug}.json").toFile()
        try {
            if (jsonFile.exists()) {
                println("Skipping ${clip.slug} because $jsonFile exists")
            } else {
                try {
                    downloadPreview(clip, imageFile)
                } catch (ex: DownloadException) {
                    // Don't care, not a critical file
                }
                try {
                    downloadClip(clip, folder, jsonFile)
                } catch (ex: DownloadException) {
                    println("Could not download ${ex.url} for ${clip.slug} based on ${clip.thumbnailUrl}")
                    errorLog.write("Could not download ${ex.url} for ${clip.slug} based on ${clip.thumbnailUrl}")
                    errorLog.newLine()
                    errorLog.flush()
                }
            }
        } catch (ex: IOException) {
            println("Error downloading clip ${clip.slug}")
            if (jsonFile.exists()) {
                jsonFile.delete()
            }
        }
    }

    fun downloadClip(clip: Clip, folder: Path, jsonFile: File) {
        val downloadUrl: URI = guessDownloadUrl(clip)
        println("Downloading $downloadUrl for ${clip.slug}")
        val videoFile = folder.resolve("${clip.slug}.mp4").toFile()
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
        println("Downloading ${clip.thumbnailUrl} for ${clip.slug}")
        val res = HttpGet(clip.thumbnailUrl.toString())
            .run {
                config = requestConfig
                httpClient.execute(this)
            }
        if (res.statusLine.statusCode == STATUS_OK) {
            BufferedOutputStream(FileOutputStream(imageFile)).use { outputStream ->
                res.entity.writeTo(outputStream)
            }
        } else {
            throw DownloadException(clip.thumbnailUrl)
        }
    }
}

