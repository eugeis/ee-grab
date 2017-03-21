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

import ee.common.ext.exists
import ee.common.ext.mkdirs
import ee.common.ext.toConvertUmlauts
import ee.common.ext.toKey
import org.apache.http.client.CookieStore
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.cookie.BasicClientCookie
import org.apache.http.params.BasicHttpParams
import org.apache.http.params.HttpConnectionParams
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.net.URLDecoder
import java.nio.file.Path

/* ***************************************************************************/

/* ***************************************************************************/
internal class BrowserImpl(val driver: WebDriver) : Browser, WebDriver by driver {
    override val js = object : JavaScriptExecutor {
        override fun execute(vararg args: Any, async: Boolean, script: () -> String): Any? {
            if (driver is JavascriptExecutor) {
                return when (async) {
                    false -> driver.executeScript(script(), *args)
                    else -> driver.executeAsyncScript(script(), *args)
                }
            }

            throw UnsupportedOperationException()
        }
    }

    override fun downloadFile(url: String, target: Path, fileName: String, fileExt: String, prefix: String): String {
        var ret = "$prefix$fileName.$fileExt"
        var path = target.resolve(ret)
        if (!path.exists()) {
            try {
                val cookieStore = seleniumCookiesToCookieStore()
                val httpParams = BasicHttpParams()
                HttpConnectionParams.setConnectionTimeout(httpParams, 20000)
                HttpConnectionParams.setSoTimeout(httpParams, 20000)
                val httpClient = DefaultHttpClient(httpParams)
                httpClient.cookieStore = cookieStore

                val httpGet = HttpGet(url)
                val response = httpClient.execute(httpGet)

                val entity = response.entity
                if (entity != null) {
                    val bis = BufferedInputStream(entity.content)
                    val currentFileName = response.getHeaders("Content-Disposition").firstOrNull()?.value?.
                            substringAfterLast("filename=\"")?.substringBeforeLast("\"")
                    if (currentFileName != null) {
                        ret = "$prefix${URLDecoder.decode(currentFileName, "UTF-8").toKey()}"
                    } else {
                        val type = MimeType.findByContentType(entity.contentType.value)
                        if (type != null) {
                            ret = "$prefix$fileName.${type.name}"
                        }
                    }
                    path = target.resolve(ret)
                    if (!path.exists()) {
                        path.parent.mkdirs()
                        val outputFile = path.toFile()
                        val bos = BufferedOutputStream(FileOutputStream(outputFile))
                        var read = bis.read()
                        while (read != -1) {
                            bos.write(read)
                            read = bis.read()
                        }
                        bis.close()
                        bos.close()
                        println("Downloaded $outputFile " + outputFile.length() + " bytes. " + entity!!.contentType)
                    }
                } else {
                    println("Download failed of $ret!")
                }
            } catch (e: Exception) {
                println("Download failed of $ret because of $e!")
            }
        } else {
            println("Skip the file $ret because the file exists already.")
        }
        return ret
    }

    private fun seleniumCookiesToCookieStore(): CookieStore {

        val seleniumCookies = driver.manage().cookies
        val cookieStore = BasicCookieStore()

        for (seleniumCookie in seleniumCookies) {
            val basicClientCookie = BasicClientCookie(seleniumCookie.name, seleniumCookie.value)
            basicClientCookie.domain = seleniumCookie.domain
            basicClientCookie.expiryDate = seleniumCookie.expiry
            basicClientCookie.path = seleniumCookie.path
            cookieStore.addCookie(basicClientCookie)
        }

        return cookieStore
    }

    override fun <T> waitFor(timeOutInSeconds: Long, sleepInMillis: Long, isTrue: () -> ExpectedCondition<T>): T {
        return WebDriverWait(driver, timeOutInSeconds, sleepInMillis)
                .until(isTrue())
    }
}
/* ***************************************************************************/
