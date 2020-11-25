package ee.sharepoint

import ee.common.cr.waitFor
import ee.common.ext.exists
import ee.common.ext.safe
import ee.grab.core.Browser
import ee.grab.core.Page
import ee.grab.libs.delegatesTo
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

class RootFolderPage(browser: Browser, val name: String, url: String) : Page(browser, url) {
    private val log = LoggerFactory.getLogger(javaClass)

    override val at = delegatesTo<Browser, Boolean> {
        val value = findElement(By.id("DeltaPlaceHolderPageTitleInTitleArea"))
        val lastTitle = value.text.split("\n").last()
        lastTitle == name
    }

    private val documentTable by lazy {
        findElement(By.id("onetidDoclibViewTbl0"))
    }

    fun items(): List<SpItem> {
        browser.at { this }
        val items: MutableList<SpItem> = mutableListOf()
        documentTable.findElements(By.tagName("a")).forEach {
            val classNames = it.getAttribute("class")
            val childHref = it.getAttribute("href")
            val ariaLabel = it.getAttribute("aria-label")
            if (classNames.contains("ms-listlink")) {
                val type = ariaLabel.substringAfterLast(", ")
                items.add(if (type == "Folder") {
                    SpFolder(it.text, childHref)
                } else {
                    SpFile(it.text, childHref, type)
                })
            } else {
                log.debug("ignore {}", childHref)
            }
        }
        return items
    }
}

open class SpItem(var name: String, var url: String)
class SpFolder(name: String = "", url: String = "",
               var folders: List<SpFolder> = emptyList(), var files: List<SpFile> = emptyList()) : SpItem(name, url)

class SpFile(name: String = "", url: String = "", val type: String = "") : SpItem(name, url)


class Queue<T>(var items: MutableList<T> = mutableListOf()) {
    fun dequeue(): T? = if (items.isEmpty()) {
        null
    } else {
        items.removeAt(0)
    }

    fun peek(): T? = items[0]
}

class SharepointGrab(val downloadTarget: Path, firefox: Boolean = true) {
    private val log = LoggerFactory.getLogger("SharepointGrab")

    private val browser: Browser
    private val rootTargetFile = downloadTarget.toFile()

    init {
        val driver = if (firefox) {
            val firefoxOptions = FirefoxOptions()

            firefoxOptions.addPreference("browser.download.folderList", 2)
            firefoxOptions.addPreference("browser.download.manager.showWhenStarting", false)
            firefoxOptions.addPreference("browser.download.dir", downloadTarget.toRealPath().toString())
            firefoxOptions.addPreference("browser.helperApps.neverAsk.openFile",
                    "text/csv,application/x-msexcel,application/excel,application/x-excel,application/vnd.ms-excel," +
                            "image/png,image/jpeg,text/html,text/plain,application/msword,application/xml")
            firefoxOptions.addPreference("browser.helperApps.neverAsk.saveToDisk",
                    "text/csv,application/x-msexcel,application/excel,application/x-excel,application/vnd.ms-excel," +
                            "image/png,image/jpeg,text/html,text/plain,application/msword,application/xml")
            firefoxOptions.addPreference("browser.helperApps.alwaysAsk.force", false)
            firefoxOptions.addPreference("browser.download.manager.alertOnEXEOpen", false)
            firefoxOptions.addPreference("browser.download.manager.focusWhenStarting", false)
            firefoxOptions.addPreference("browser.download.manager.useWindow", false)
            firefoxOptions.addPreference("browser.download.manager.showAlertOnComplete", false)
            firefoxOptions.addPreference("browser.download.manager.closeWhenDone", false)

            FirefoxDriver(firefoxOptions)
        } else {
            val options = ChromeOptions()

            // ChromeDriver is just AWFUL because every version or two it breaks unless you pass cryptic arguments
            //AGRESSIVE: options.setPageLoadStrategy(PageLoadStrategy.NONE); // https://www.skptricks.com/2018/08/timed-out-receiving-message-from-renderer-selenium.html
            options.addArguments("start-maximized"); // https://stackoverflow.com/a/26283818/1689770
            options.addArguments("enable-automation"); // https://stackoverflow.com/a/43840128/1689770
            //options.addArguments("--headless"); // only if you are ACTUALLY running headless
            options.addArguments("--no-sandbox"); //https://stackoverflow.com/a/50725918/1689770
            options.addArguments("--disable-infobars"); //https://stackoverflow.com/a/43840128/1689770
            options.addArguments("--disable-dev-shm-usage"); //https://stackoverflow.com/a/50725918/1689770
            options.addArguments("--disable-browser-side-navigation"); //https://stackoverflow.com/a/49123152/1689770
            options.addArguments("--disable-gpu"); //https://stackoverflow.com/questions/51959986/how-to-solve-selenium-chromedriver-timed-out-receiving-message-from-renderer-exc


            options.setCapability("browser.download.folderList", 2)
            options.setCapability("browser.download.manager.showWhenStarting", false)
            options.setCapability("browser.download.dir", downloadTarget.toRealPath().toString())
            options.setCapability("browser.helperApps.neverAsk.openFile",
                    "text/csv,application/x-msexcel,application/excel,application/x-excel,application/vnd.ms-excel," +
                            "image/png,image/jpeg,text/html,text/plain,application/msword,application/xml")
            options.setCapability("browser.helperApps.neverAsk.saveToDisk",
                    "text/csv,application/x-msexcel,application/excel,application/x-excel,application/vnd.ms-excel," +
                            "image/png,image/jpeg,text/html,text/plain,application/msword,application/xml")
            options.setCapability("browser.helperApps.alwaysAsk.force", false)
            options.setCapability("browser.download.manager.alertOnEXEOpen", false)
            options.setCapability("browser.download.manager.focusWhenStarting", false)
            options.setCapability("browser.download.manager.useWindow", false)
            options.setCapability("browser.download.manager.showAlertOnComplete", false)
            options.setCapability("browser.download.manager.closeWhenDone", false)

            ChromeDriver(options)
        }

        browser = Browser.new(driver)

        driver.manage().timeouts().pageLoadTimeout(5, TimeUnit.SECONDS)
    }

    fun login(url: String, sleepForManualLogin: Long) {
        browser.to(url)
        //login manually
        if (sleepForManualLogin > 0) {
            Thread.sleep(sleepForManualLogin)
        }
    }

    fun browse(name: String, url: String): SpFolder {
        val rootFolder = SpFolder(name, url)

        val queue = Queue<SpFolder>()

        var currentFolder: SpFolder? = rootFolder
        while (currentFolder != null) {
            val page = RootFolderPage(browser, currentFolder.name, currentFolder.url)
            browser.to { page }

            val items = page.items()

            val folders = mutableListOf<SpFolder>()
            val files = mutableListOf<SpFile>()
            items.forEach {
                if (it is SpFolder) {
                    folders.add(it)
                } else if (it is SpFile) {
                    files.add(it)
                }
            }

            if (files.isNotEmpty()) {
                currentFolder.files = files
            }

            if (folders.isNotEmpty()) {
                currentFolder.folders = folders
                queue.items.addAll(folders)
            }

            currentFolder = queue.dequeue()
        }
        return rootFolder
    }

    fun download(rootFolder: SpFolder, targetFolder: Path, deleteExists: Boolean = true,
                 ignoreExtensions: Set<String> = emptySet()) {
        rootFolder.download(targetFolder.toFile(), deleteExists, ignoreExtensions)
        log.info("download finished of '{}'", rootFolder.name)
    }

    fun SpFolder.download(target: File, deleteExists: Boolean = true, ignoreExtensions: Set<String>) {
        val folder = File(target, name)
        if (!folder.exists()) {
            folder.mkdir()
        }

        folders.forEach {
            it.download(folder, deleteExists, ignoreExtensions)
        }

        files.forEach {
            val ext = it.url.substringAfterLast(".").toLowerCase()
            if (!ignoreExtensions.contains(ext)) {
                it.download(folder, deleteExists)
            } else {
                log.info("don't download, filter out by extension {}.{}", it.name, ext)
            }
        }
    }

    fun SpFile.download(target: File, deleteExists: Boolean) {
        val fileName = "$name.${url.substringAfterLast(".")}"

        val targetFile = File(target, fileName)
        val targetFilePath = targetFile.toPath()
        val downloadedFile = File(rootTargetFile, fileName)

        if (downloadedFile.exists() || targetFilePath.exists()) {
            if (!deleteExists) {
                log.warn("the file exists already, skip it: {}", targetFilePath)
                return
            }
            downloadedFile.delete()
            targetFile.delete()
        }

        GlobalScope.launch {
            safe(log, "download $targetFilePath") {
                log.info("download {}", targetFilePath)
                browser.get(url)
            }
        }


        val downloaded = runBlocking {
            waitFor(30) {
                downloadedFile.exists()
            }
        }

        if (downloaded) {
            safe(log, "move $downloadedFile to $targetFilePath") {
                Files.move(downloadedFile.toPath(), targetFilePath, StandardCopyOption.REPLACE_EXISTING)
            }
        } else {
            log.warn("can't download {}", targetFilePath)
        }
    }
}