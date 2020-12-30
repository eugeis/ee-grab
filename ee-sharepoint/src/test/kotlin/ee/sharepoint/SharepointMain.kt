package ee.sharepoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import ee.grab.core.Browser
import java.nio.file.Paths

fun main() {
    System.setProperty("webdriver.gecko.driver", "/home/z000ru5y/dev/d/ee-grab/ee-grab/drivers/geckodriver");
    System.setProperty("webdriver.chrome.driver", "/home/z000ru5y/dev/d/ee-grab/ee-grab/drivers/chromedriver");
    val folderName = "Documents"
    val url = "https://wse02.siemens.com/content/P0002864/SSWA/Shared%20Documents/Forms/AllItems.aspx?View=%7B2E5798D3%2DB8D9%2D408F%2DB4CE%2D6ED0E20BA0A0%7D"

    val target = Paths.get("/home/z000ru5y/Documents/SSWA/download/")

    val json = ObjectMapper()
    val targetFile = target.resolve("${folderName}.json").toFile()

    val grab = SharepointGrab(Paths.get("/home/z000ru5y/Downloads"), false)
    grab.login(url, 40000)

    val folder: SpFolder = if (!targetFile.exists()) {
        val folder = grab.browse(folderName,
                url)
        json.writeValue(targetFile, folder)
        folder
    } else {
        json.readValue(targetFile)
    }
    grab.download(folder, target, false, setOf("wmv", "mp4", "mp3"))
}