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
import org.openqa.selenium.support.ui.ExpectedCondition
/* ***************************************************************************/

/* ***************************************************************************/
interface WaitingSupport {
  fun <T> waitFor(timeOutInSeconds: Long, sleepInMillis: Long, isTrue: () -> ExpectedCondition<T>): T

  fun <T> waitFor(timeOutInSeconds: Long, isTrue: () -> ExpectedCondition<T>): T {
    return waitFor(timeOutInSeconds, 1000L, isTrue)
  }

  fun <T> waitFor(isTrue: () -> ExpectedCondition<T>): T {
    return waitFor(10L, isTrue)
  }
}
/* ***************************************************************************/
