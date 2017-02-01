/******************************************************************************
 * Copyright 2016 Edinson E. Padrón Urdaneta
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *****************************************************************************/

/* ***************************************************************************/
package ee.grab.core

/* ***************************************************************************/

/* ***************************************************************************/

import ee.common.ext.eeAppHome
import ee.common.ext.executableFileExtension
import ee.grab.exceptions.PageAtValidationError
import org.openqa.selenium.WebDriver
import org.openqa.selenium.firefox.FirefoxDriver

/* ***************************************************************************/

/* ***************************************************************************/
interface Browser : JavaScriptSupport, WaitingSupport, DownloadSupport, WebDriver {
    companion object {
        fun registerDriver() {
            System.setProperty("webdriver.gecko.driver", "$eeAppHome/drivers/geckodriver$executableFileExtension")
            System.setProperty("webdriver.chrome.driver", "$eeAppHome/drivers/chromedriver$executableFileExtension")
        }

        fun drive(driver: WebDriver = FirefoxDriver(), block: Browser.() -> Unit) {
            BrowserImpl(driver).apply {
                block()
                quit()
            }
        }

        fun new(driver: WebDriver = FirefoxDriver()): Browser {
            return BrowserImpl(driver)
        }
    }

    fun <T : Page> at(factory: () -> T): T {
        return at(factory, false)
    }

    fun to(url: String): String {
        get(url)

        return currentUrl
    }

    fun <T : Page> to(factory: () -> T): T {
        return at(factory, true)
    }

    private fun <T : Page> at(factory: () -> T, shouldChangeUrl: Boolean): T {
        val page = factory()

        page.browser = this

        if (shouldChangeUrl) {
            page.url?.let { to(it) }
        }

        if (!page.verifyAt()) {
            throw PageAtValidationError()
        }

        return page
    }
}
/* ***************************************************************************/
