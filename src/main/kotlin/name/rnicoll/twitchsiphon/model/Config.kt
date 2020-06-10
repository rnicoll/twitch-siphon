package name.rnicoll.twitchsiphon.model

import com.fasterxml.jackson.annotation.JsonProperty

data class Config(
    @JsonProperty("client_id")
    val clientId: String,
    @JsonProperty("access_token")
    val accessToken: String,
    val broadcaster: String,
    @JsonProperty("start_year")
    val startYear: Int
)