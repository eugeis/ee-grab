package ee.brill

import ee.common.ext.*
import ee.grab.core.Browser
import ee.grab.core.Page
import ee.grab.extensions.`$`
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.support.ui.ExpectedConditions
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.regex.Pattern

data class Item(val name: String, val url: String, val urlSuffix: String,
                val key: String = urlSuffix.substringAfterLast("/"), val filePath: String = "$urlSuffix.html") {
}

var linkValidator: Pattern = "".toPattern()
val handeledLinks = hashSetOf<String>()
val mediaToDownload = ConcurrentLinkedDeque<Item>()
val articleToDownload = ConcurrentLinkedDeque<Item>()
val browseToDownload = ConcurrentLinkedDeque<Item>()
private fun isToHandle(it: Item, validateLink: Boolean = true) =
        !handeledLinks.contains(it.url) && (!validateLink || linkValidator.matcher(it.url).matches())

private fun extractLink(it: WebElement) = it.getAttribute("href").substringBeforeLast("?").substringBeforeLast("#")

open class BrillPage(browser: Browser, val urlBase: String, url: String = urlBase) : Page(browser, url) {
    val indexFileName = "index.html"

    fun logout() {
        browser.to("$urlBase#logout-form")
    }
}

class LoginPage(browser: Browser, urlBase: String) : BrillPage(browser, urlBase) {
    private val loginForm by lazy { `$`("form#login-form", 0) }
    private val loginButton by lazy { loginForm.findElement(By.name("submit")) }
    private val loginUser by lazy { loginForm.`$`("input#sgkUser", 0) }
    private val loginPwd by lazy { loginForm.`$`("input#sgkPass", 0) }

    fun login(user: String, pwd: String): SubjectsOverview {
        loginUser.sendKeys(user)
        loginPwd.sendKeys(pwd)
        waitFor { ExpectedConditions.elementToBeClickable(loginButton) }
        loginButton.click()
        return SubjectsOverview(browser, urlBase)
    }
}

open class BrowsePage(browser: Browser, urlBase: String, val item: Item) : BrillPage(browser, "${item.url}$urlSuffix") {
    companion object {
        val urlSuffix: String = "?lang=de&s.rows=100000"
    }

    private val main by lazy {
        `$`("div#main", 0)
    }

    private val content by lazy {
        main.`$`("div.result", 0)
    }

    fun content(): String {
        return "${content(content)}\n"
    }

    val articles by lazy {
        content.`$`("a[href*='entries/']").map {
            val link = extractLink(it)
            Item(it.text, link, link.substringAfter("$urlBase/"))
        }
    }

    val browses by lazy {
        content.`$`("a[href*='browse/']").map {
            val link = extractLink(it)
            Item(it.text, link, link.substringAfter("$urlBase/"))
        }
    }

    val media by lazy {
        val ret = arrayListOf<Item>()
        ret.addAll(content.`$`("a[href*='media/']").map {
            val link = extractLink(it)
            val urlSuffix = link.substringAfter("$urlBase/")
            Item(it.text, link, urlSuffix, urlSuffix.substringAfterLast("/"), urlSuffix)
        })
        ret
    }

    fun downloadTo(target: Path, statusUpdater: (String) -> Unit = {}) {
        var content = content()
        val itemPath = target.resolve(item.filePath)
        statusUpdater("Download: ${item.filePath}")
        articles.forEach {
            if (isToHandle(it)) {
                handeledLinks.add(it.url)
                articleToDownload.add(it)
            }
            content = content.replace("${it.url}", "${itemPath.parent.relativize(target.resolve(it.filePath))}")
        }

        browses.forEach {
            if (isToHandle(it)) {
                handeledLinks.add(it.url)
                browseToDownload.add(it)
            }
            content = content.replace("\"${it.url}\"", "\"${itemPath.parent.relativize(target.resolve(it.filePath))}\"")
        }

        media.forEach {
            if (isToHandle(it, false)) {
                handeledLinks.add(it.url)
                mediaToDownload.add(it)
            }
            content = content.replace("${it.url}", "${itemPath.parent.relativize(target.resolve(it.filePath))}")
        }
        saveAsHtml(content, item.name, target, itemPath)
    }
}

class SubjectsOverview(browser: Browser, urlBase: String) : BrillPage(browser, urlBase) {
    private val main by lazy {
        `$`("div#main", 0)
    }

    private val content by lazy {
        main.`$`("div#subject-list", 0)
    }

    val browses by lazy {
        content.`$`("a[href*='browse/']").map {
            val link = extractLink(it)
            Item(it.text, link, link.substringAfter("$urlBase/"))
        }
    }

    fun content(): String {
        return "${content(content)}\n"
    }

    fun downloadTo(target: Path, subjectsToDownload: List<String>, statusUpdater: (String) -> Unit = {}) {
        subjectsToDownload.forEach {
            val url = "$urlBase/browse/$it"
            handeledLinks.add("$urlBase/browse/$it")
            browseToDownload.add(Item(url, url, "browse/$it"))
        }

        linkValidator = ".*(${subjectsToDownload.joinToString("|")}).*".toPattern()

        downloadBrowses(target, statusUpdater)
        downloadArticles(target, statusUpdater)
        downloadMedia(target, statusUpdater)
    }

    private fun downloadBrowses(target: Path, statusUpdater: (String) -> Unit = {}) {
        while (browseToDownload.isNotEmpty()) {
            val item = browseToDownload.poll()
            try {
                val page = browser.to { BrowsePage(browser, urlBase, item) }
                page.downloadTo(target, statusUpdater)
            } catch (e: Exception) {
                statusUpdater("Download failed of $item, because of $e")
            }
            downloadArticles(target, statusUpdater)
        }
    }

    private fun downloadArticles(target: Path, statusUpdater: (String) -> Unit = {}) {
        while (articleToDownload.isNotEmpty()) {
            val item = articleToDownload.poll()
            if (!target.resolve(item.filePath).exists()) {
                try {
                    val page = browser.to { ArticlePage(browser, urlBase, item) }
                    page.downloadTo(target, statusUpdater)
                } catch (e: Exception) {
                    statusUpdater("Download failed of $item, because of $e")
                }
            }
            downloadMedia(target, statusUpdater)
        }
    }

    private fun downloadMedia(target: Path, statusUpdater: (String) -> Unit = {}) {
        while (mediaToDownload.isNotEmpty()) {
            val item = mediaToDownload.poll()
            val itemPath = target.resolve(item.filePath)
            if (!itemPath.exists()) {
                try {
                    statusUpdater("Download: ${item.filePath}")
                    browser.downloadFile(item.url, target, item.filePath.fileName(), item.filePath.fileExt())
                } catch (e: Exception) {
                    val msg = "Download of $itemPath not possible because of $e"
                    statusUpdater(msg)
                }
            }
        }
    }
}

class ArticlePage(browser: Browser, urlBase: String, val item: Item) : BrillPage(browser, item.url) {

    private val main by lazy {
        `$`("div#main", 0)
    }

    private val title by lazy {
        main.`$`("h1.book-title", 0)
    }

    private val content by lazy {
        main.`$`("article.content", 0)
    }

    private val relatedCurrent by lazy {
        main.`$`("div#linking-hub-related-current").firstOrNull()
    }

    private val citation by lazy {
        main.`$`("div.citation").firstOrNull()
    }

    val articles by lazy {
        val ret = arrayListOf<Item>()
        ret.addAll(content.`$`("a[href*='entries/']").map {
            val link = extractLink(it)
            Item(it.text, link, link.substringAfter("$urlBase/"))
        })
        if (relatedCurrent != null) {
            ret.addAll(relatedCurrent!!.`$`("a[href*='entries/']").map {
                val link = extractLink(it)
                Item(it.text, link, link.substringAfter("$urlBase/"))
            })
        }
        ret
    }

    val media by lazy {
        val ret = arrayListOf<Item>()
        ret.addAll(content.`$`("a[href*='media/']").map {
            val link = extractLink(it)
            val urlSuffix = link.substringAfter(urlBase)
            Item(it.text, link, urlSuffix, urlSuffix.substringAfterLast("/"), urlSuffix)
        })
        ret
    }

    fun content(): String {
        return "${content(title)}\n${content(content)}\n${content(relatedCurrent)}\n${content(citation)}\n"
    }

    fun downloadTo(target: Path, statusUpdater: (String) -> Unit = {}) {
        val itemPath = target.resolve(item.filePath)
        if (!itemPath.exists()) {
            statusUpdater("Download: ${item.filePath}")

            var content = content()

            articles.forEach {
                if (isToHandle(it)) {
                    handeledLinks.add(it.url)
                    articleToDownload.add(it)
                }
                content = content.replace("${it.url}", "${itemPath.parent.relativize(target.resolve(it.filePath))}")
            }
            media.forEach {
                if (isToHandle(it, false)) {
                    handeledLinks.add(it.url)
                    mediaToDownload.add(it)
                }
                content = content.replace("${it.url}", "${itemPath.parent.relativize(target.resolve(it.filePath))}")
            }
            saveAsHtml(content, item.name, target, itemPath)
        }
    }
}

class Brill() {
    private var browser: Browser? = null
    var subjectsOverview: SubjectsOverview? = null

    init {
        Browser.registerDriver()
    }

    fun login(urlBase: String, username: String, password: String) {
        val currentBrowser = startBrowser()
        var loginPage = currentBrowser.to { LoginPage(currentBrowser, urlBase) }
        subjectsOverview = loginPage.login(username, password)
    }

    fun downloadTo(target: Path, subjectsToDownload: List<String>, statusUpdater: (String) -> Unit = { println(it) }) {
        subjectsOverview?.downloadTo(target, subjectsToDownload, statusUpdater)
    }

    private fun startBrowser(): Browser {
        if (browser == null) {
            browser = Browser.new(ChromeDriver())
        }
        return browser!!
    }

    fun logout() {
        letTraceExc(false) { subjectsOverview?.logout() }
        letTraceExc(false) { browser?.quit() }
    }
}


private fun saveAsHtml(content: String, title: String, base: Path, target: Path) {
    val styleFile = "${target.parent.relativize(base.resolve("style.css"))}"
    target.parent.mkdirs()
    target.toFile().printWriter().use {
        out ->
        out.println("""<hmtl><head><meta http-equiv="content-type" content="text/php; charset=UTF-8">
    <title>$title</title>
        <link href="$styleFile" rel="stylesheet">
        <style>
        </style>
</head><body>""")
        out.println(content)
        out.println("</body></hmtl>")
    }
}
