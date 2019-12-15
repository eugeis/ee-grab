package ee.sharepoint

import com.google.common.collect.Maps
import com.google.common.collect.Sets
import ee.common.cr.waitFor
import ee.common.ext.safe
import ee.grab.core.Browser
import ee.grab.core.Page
import ee.grab.libs.delegatesTo
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.openqa.selenium.*
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.remote.CapabilityType
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

class SharepointGrab(val rootTarget: Path) {
    private val log = LoggerFactory.getLogger("SharepointGrab")

    private val browser: Browser
    private val rootTargetFile = rootTarget.toFile()

    init {

        val profile = FirefoxOptions()

        profile.addPreference("browser.download.folderList", 2)
        profile.addPreference("browser.download.manager.showWhenStarting", false)
        profile.addPreference("browser.download.dir", rootTarget.toRealPath().toString())
        profile.addPreference("browser.helperApps.neverAsk.openFile",
                "text/csv,application/x-msexcel,application/excel,application/x-excel,application/vnd.ms-excel," +
                        "image/png,image/jpeg,text/html,text/plain,application/msword,application/xml")
        profile.addPreference("browser.helperApps.neverAsk.saveToDisk",
                "text/csv,application/x-msexcel,application/excel,application/x-excel,application/vnd.ms-excel," +
                        "image/png,image/jpeg,text/html,text/plain,application/msword,application/xml")
        profile.addPreference("browser.helperApps.alwaysAsk.force", false)
        profile.addPreference("browser.download.manager.alertOnEXEOpen", false)
        profile.addPreference("browser.download.manager.focusWhenStarting", false)
        profile.addPreference("browser.download.manager.useWindow", false)
        profile.addPreference("browser.download.manager.showAlertOnComplete", false)
        profile.addPreference("browser.download.manager.closeWhenDone", false)

        val driver = FirefoxDriver(profile)
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

    fun download(rootFolder: SpFolder) {
        rootFolder.download(rootTarget.toFile())
    }

    fun SpFolder.download(target: File) {
        val folder = File(target, name)
        if (!folder.exists()) {
            folder.mkdir()
        }

        folders.forEach {
            it.download(folder)
        }

        files.forEach {
            it.download(folder)
        }
    }

    fun SpFile.download(target: File) {
        val fileName = "$name.${url.substringAfterLast(".")}"

        val targetFile = File(target, fileName).toPath()
        val downloadedFile = File(rootTargetFile, fileName)
        if (downloadedFile.exists()) {
            downloadedFile.delete()
        }

        GlobalScope.launch {
            safe(log, "download $targetFile") {
                log.info("download {}", targetFile)
                browser.get(url)
            }
        }


        val downloaded = runBlocking {
            waitFor(30) {
                downloadedFile.exists()
            }
        }

        if (downloaded) {
            safe(log, "move $downloadedFile to $targetFile") {
                Files.move(downloadedFile.toPath(), targetFile, StandardCopyOption.REPLACE_EXISTING)
            }
        } else {
            log.warn("can't download {}", targetFile)
        }
    }
}