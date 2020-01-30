package ee.sharepoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import ee.grab.core.Browser
import java.nio.file.Paths

fun main() {
    System.setProperty("webdriver.gecko.driver",
            "/home/z000ru5y/dev/d/ee-grab/ee-grab/drivers/geckodriver");
    val folderName = "SSWA12"
    val url = "https://wse02.siemens.com/content/P0002864/SSWA/Shared Documents/Forms/AllItems.aspx?" +
            "RootFolder=%2Fcontent%2FP0002864%2FSSWA%2FShared Documents" +
            "%2FSSWA Participant Folders%2F$folderName"

    val target = Paths.get("/home/z000ru5y/SSWA/")

    val json = ObjectMapper()
    val targetFile = target.resolve("${folderName}.json").toFile()

    val grab = SharepointGrab(target)
    grab.login(url, 20000)

    val folder: SpFolder = if (!targetFile.exists()) {
        val folder = grab.browse(folderName,
                url)
        json.writeValue(targetFile, folder)
        folder
    } else {
        json.readValue(targetFile)
    }
    grab.download(folder)

}