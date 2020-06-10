package name.rnicoll.twitchsiphon.model

import java.net.URI

data class Clip(
    val slug: String,
    val title: String,
    val creatorName: String,
    val createdAt: String,
    val viewCount: String?,
    val thumbnailUrl: URI
)
