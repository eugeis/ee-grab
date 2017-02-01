package ee.grab.core

import java.nio.file.Path

interface DownloadSupport {
    fun downloadFile(url: String, target: Path, fileName: String, fileExt: String, prefix: String = ""): String
}
