package pl.pollub.s95408.lab4

data class File(
    var id:String,
    var name:String,
    var type:String,
    var url:String,
    var downloadedUri:String?=null,
    var bytesDownloaded: Int = 0
)