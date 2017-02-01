package ee.rulit

import ee.grab.core.Browser
import ee.grab.core.Page
import ee.grab.extensions.`$`
import ee.grab.libs.delegatesTo
import org.openqa.selenium.By
import java.nio.file.Path


open class RuLitPage(browser: Browser, url: String = "") : Page(browser, url) {
    companion object {
        var HOST = "rulit"
    }
}

class BookPage(browser: Browser, val book: String, val page: Int = 1,
               url: String = "http://www.${RuLitPage.HOST}.me/books/$book-$page.php") : RuLitPage(browser, url) {
    override val at = delegatesTo<Browser, Boolean> {
        findElement(By.id("current_page")).text.toInt() == page
    }

    private val nextPageLink by lazy {
        `$`("a[title='Следующая страница']").firstOrNull()
    }

    private val contentDiv by lazy {
        `$`("div.page_content", 0)
    }

    fun nextPage(): BookPage? {
        if (nextPageLink != null) {
            nextPageLink?.click()
            return browser.at({ BookPage(browser, book, page + 1) })
        } else {
            return null
        }
    }

    fun content(): List<String> {
        browser.at({ this })

        return contentDiv.findElements(By.ByXPath("*"))
                .filter { "p".equals(it.tagName) || ("div".equals(it.tagName) && "title".equals(it.getAttribute("class"))) }
                .map { it.getAttribute("outerHTML") }
    }
}

fun downloadTo(bookName: String, target: Path) {
    val content: MutableList<String> = arrayListOf()

    Browser.drive {
        var curentPage: BookPage? = BookPage(this, bookName, 1)
        to({ curentPage!! })
        while (curentPage != null) {
            content.addAll(curentPage.content())
            curentPage = curentPage.nextPage()
        }
    }

    target.resolve("$bookName.php").toFile().printWriter().use { out ->
        out.println("""<hmtl><head><meta http-equiv="content-type" content="text/php; charset=UTF-8">
<style>
  p {
      text-indent: 20px;
  }

  div.title {
      font-weight: bold;
  }
</style>
</head><body>""")
        content.forEach {
            out.println(it)
        }
        out.println("</body></hmtl>")
    }
}
