package name.rnicoll.twitchsiphon.exception

import java.net.URI

class DownloadException(val url: URI) : SiphonException()