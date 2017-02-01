package ee.grab.core

import org.openqa.selenium.By
import org.openqa.selenium.SearchContext
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedCondition

abstract class Page(var browser: Browser = DriverlessBrowser(), open val url: String = "") : JavaScriptSupport, SearchContext, WaitingSupport {
    open val at: Browser.() -> Boolean = { url.isNotEmpty() || this.currentUrl.startsWith(url) }

    override val js: JavaScriptExecutor
        get() = browser.js

    override fun findElement(by: By): WebElement {
        return browser.findElement(by)
    }

    override fun findElements(by: By): List<WebElement> {
        return browser.findElements(by)
    }

    override fun <T> waitFor(timeOutInSeconds: Long, sleepInMillis: Long, isTrue: () -> ExpectedCondition<T>): T {
        return browser.waitFor(timeOutInSeconds, sleepInMillis, isTrue)
    }

    fun source(): String {
        return browser.pageSource
    }

    open fun content(element: WebElement?): String {
        return if (element != null) element.getAttribute("outerHTML") else ""
    }

    fun verifyAt(): Boolean {
        return at(browser)
    }

    fun toUrlIfNotCurrent(verify: Boolean = true) {
        if (browser.currentUrl != url) {
            if (verify) {
                browser.to({ this })
            } else {
                browser.to(url)
            }
        }
    }
}
