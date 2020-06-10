package name.rnicoll.twitchsiphon.model

import com.fasterxml.jackson.annotation.JsonProperty

class ClipJson(
    var id: String,
    var url: String,
    @JsonProperty("embed_url")
    var embedUrl: String,
    @JsonProperty("broadcaster_id")
    var broadcasterId: Int,
    @JsonProperty("broadcaster_name")
    var broadcasterName: String,
    @JsonProperty("creator_id")
    var creatorId: Int,
    @JsonProperty("creator_name")
    var creatorName: String,
    @JsonProperty("video_id")
    var videoId: Int,
    @JsonProperty("game_id")
    var gameId: Int,
    var language: String,
    var title: String,
    @JsonProperty("view_count")
    var viewCount: Int,
    var createdAt: String,
    var thumbnailUrl: String
)