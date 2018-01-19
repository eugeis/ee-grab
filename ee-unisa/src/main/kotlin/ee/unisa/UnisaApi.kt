package ee.unisa

import ee.common.ext.*
import ee.grab.core.Browser
import ee.grab.core.Page
import ee.grab.extensions.`$`
import ee.grab.extensions.find
import org.openqa.selenium.chrome.ChromeDriver
import java.net.URLDecoder
import java.nio.file.Path


open class OaiPage(browser: Browser, var urlBase: String, val handle: String = "", url: String = "$urlBase$handle") :
        Page(browser, url) {
    val indexFileName = "index.html"

    init {
        if (!urlBase.startsWith("http")) {
            urlBase = "http://$urlBase"
        }
    }
}


class BrowsePage(browser: Browser, urlBase: String, handle: String,
    url: String = "$urlBase$handle/browse?order=DESC&rpp=100&sort_by=2&etal=-1&offset=$0&type=dateissued") :
        OaiPage(browser, urlBase, handle, url) {

    private val nextPageLink by lazy {
        `$`("a.next-page-link").firstOrNull()?.getAttribute("href")
    }

    private val artifactList by lazy {
        `$`("ul.ds-artifact-list", 0)
    }

    private val artifactLinks by lazy {
        artifactList.`$`("a").map { it.getAttribute("href") }
    }

    fun content(): String {
        return content(artifactList)
    }

    fun toArtifactPage(handle: String): ArtifactPage {
        val ret = ArtifactPage(browser, urlBase, handle)
        browser.to({ ret })
        return browser.at({ ret })
    }

    fun downloadTo(target: Path, statusUpdater: (String) -> Unit = {}) {

        if (!target.exists()) {
            target.mkdirs()
        }

        val allContent = StringBuffer()

        var currentPage: BrowsePage? = this
        while (currentPage != null) {
            val next = currentPage.nextPageLink

            var artifactContent = currentPage.content()
            currentPage.artifactLinks.forEach { link ->
                val linkWithoutBase = link.substringAfter(urlBase)
                val artifactPage = toArtifactPage(linkWithoutBase)
                val fileName = artifactPage.downloadTo(target, statusUpdater)
                artifactContent = artifactContent.replace("\"$link\"", "\"$fileName\"")
                artifactContent = artifactContent.replace("\"$linkWithoutBase\"", "\"$fileName\"")
            }
            allContent.appendln(artifactContent)

            if (next != null) {
                currentPage = browser.to { BrowsePage(browser, urlBase, handle, next) }
            } else {
                currentPage = null
            }
        }
        saveAsHtml(allContent.toString(), "", target.resolve(indexFileName))
    }
}

class ArtifactPage(browser: Browser, urlBase: String, handle: String,
    val key: String = handle.toKey().replaceFirst("_handle_", "")) : OaiPage(browser, urlBase, handle) {

    val content by lazy {
        `$`("div#ds-body", 0)
    }

    val fileLinks by lazy {
        content.`$`("div.file-link").map { it.find("a", 0).getAttribute("href") }
    }

    private val title by lazy {
        content.`$`("h1", 0).text
    }

    fun content(): String {
        return content(content)
    }

    fun downloadTo(target: Path, statusUpdater: (String) -> Unit = {}): String {
        var ret = "$key.html"
        fileLinks.forEach {
            val fullFileName = URLDecoder.decode(it, "UTF-8").substringAfterLast("/").substringBefore("?")
            val ext = fullFileName.fileExt()
            val fileName = "${fullFileName.fileName().toKey()}"
            statusUpdater("Download artifact: $fileName")

            var artifactContent = content()

            val localLink = browser.downloadFile(it, target, fileName, ext, "${key}_")
            val link = it.replace("&", "&amp;")
            val linkWithoutUrlBase = link.substringAfter(urlBase)
            artifactContent = artifactContent.replace("\"$link\"", "\"$localLink\"")
            artifactContent = artifactContent.replace("\"$linkWithoutUrlBase\"", "\"$localLink\"")

            saveAsHtml(artifactContent, title, target.resolve(ret))
        }
        return ret
    }
}

class Oai() {
    var statusUpdater: (String) -> Unit = {}

    init {
        Browser.registerDriver()
    }

    fun downloadTo(urlBase: String, handle: String, target: Path) {
        val browser = Browser.new(ChromeDriver())

        val browsePage = BrowsePage(browser, urlBase, handle)
        browsePage.toUrlIfNotCurrent(false)
        browsePage.verifyAt()
        browsePage.downloadTo(target)
        browser.quit()
    }
}

private fun saveAsHtml(content: String, title: String, target: Path) {
    target.toFile().printWriter().use { out ->
        out.println("""<hmtl><head><meta http-equiv="content-type" content="text/php; charset=UTF-8">
    <title>$title</title>
<style>
  p {
      text-indent: 20px;
  }

  div.title {
      font-weight: bold;
  }
</style>
</head><body>""")
        out.println(content)
        out.println("</body></hmtl>")
    }
}