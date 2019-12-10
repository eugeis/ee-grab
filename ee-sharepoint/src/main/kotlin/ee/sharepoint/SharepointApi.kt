package ee.sharepoint

import ee.grab.core.Browser
import ee.grab.core.Page
import ee.grab.libs.delegatesTo
import org.openqa.selenium.By
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path


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
class SpFolder(name: String="", url: String="",
               var folders: List<SpFolder> = emptyList(), var files: List<SpFile> = emptyList()) : SpItem(name, url)

class SpFile(name: String="", url: String="", val type: String="") : SpItem(name, url)

fun browse(rootFolder: SpFolder, target: Path) {
    val log = LoggerFactory.getLogger("SharepointPage")
    val documents: MutableList<String> = arrayListOf()

    Browser.drive {
        val queue = Queue<SpFolder>()

        to(rootFolder.url)
        //login manually

        var currentFolder: SpFolder? = rootFolder
        while (currentFolder != null) {
            val page = RootFolderPage(this, currentFolder.name, currentFolder.url)
            to { page }

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
            currentFolder.files = files
            currentFolder.folders = folders

            queue.items.addAll(folders)

            currentFolder = queue.dequeue()
        }
    }
    log.info("{}", documents.size)
}

fun SpFolder.create(target: File) {
    val folder = File(target, name)
    if (!folder.exists()) {
        folder.mkdir()
    }

    folders.forEach {
        it.create(folder)
    }

    files.forEach {
        it.create(folder)
    }
}

fun SpFile.create(target: File) {
    val file = File(target, name)
    if (!file.exists()) {
        file.createNewFile()
    }
}

class Queue<T>(var items: MutableList<T> = mutableListOf()) {
    fun dequeue(): T? = if (items.isEmpty()) {
        null
    } else {
        items.removeAt(0)
    }

    fun peek(): T? = items[0]
}