package name.rnicoll.twitchsiphon.model

data class Config(val clientId: String,
                  val accessToken: String,
                  val broadcasterLogin: String,
                  val startYear: Int)