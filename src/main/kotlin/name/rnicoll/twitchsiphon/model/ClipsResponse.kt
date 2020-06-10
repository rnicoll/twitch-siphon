package name.rnicoll.twitchsiphon.model

data class ClipsResponse(
    val data: List<Clip>,
    val pagination: Pagination? = null
)