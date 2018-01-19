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
package ee.grab.extensions

/* ***************************************************************************/

/* ***************************************************************************/
import org.openqa.selenium.By
import org.openqa.selenium.SearchContext
import org.openqa.selenium.WebElement

/* ***************************************************************************/

/* ***************************************************************************/
fun SearchContext.`$`(selector: String, index: Int): WebElement {
    return find(selector, index)
}

fun SearchContext.`$`(selector: String, range: IntRange): List<WebElement> {
    return find(selector, range)
}

fun SearchContext.`$`(selector: String, vararg indexes: Int): List<WebElement> {
    return find(selector, *indexes)
}

fun SearchContext.find(selector: String, index: Int): WebElement {
    val elements = findElements(By.cssSelector(selector))
    if (elements.isNotEmpty()) {
        return elements[index]
    } else {
        throw IllegalArgumentException("There is no element for the selector $selector")
    }
}

fun SearchContext.find(selector: String, range: IntRange): List<WebElement> {
    return findElements(By.cssSelector(selector)).slice(range)
}

fun SearchContext.find(selector: String, vararg indexes: Int): List<WebElement> {
    val elements = findElements(By.cssSelector(selector))

    if (indexes.size == 0) {
        return elements
    }

    return elements.slice(indexes.asList())
}
/* ***************************************************************************/
