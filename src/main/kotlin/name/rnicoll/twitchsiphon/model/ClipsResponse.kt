package name.rnicoll.twitchsiphon.model

class ClipsResponse(
    val data: Array<Clip>,
    val pagination: Pagination? = null
)