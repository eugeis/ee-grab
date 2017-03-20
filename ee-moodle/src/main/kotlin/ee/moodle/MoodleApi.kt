package ee.moodle

import ee.common.ext.*
import ee.grab.core.Browser
import ee.grab.core.Page
import ee.grab.extensions.`$`
import ee.grab.libs.delegatesTo
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable
import org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated
import java.nio.file.Path

data class Result<out T>(val ok: Boolean = true, val message: String = "", val value: T? = null) {
    constructor(value: T) : this(true, "", value)
}

data class Course(val title: String, val link: String,
                  val id: String = link.substringAfterLast("id="), val key: String = title.toKey())

data class Resource(val title: String, val link: String, val type: ResourceType,
                    val id: String = link.substringAfterLast("id="), val key: String = "${title.toKey()}_$id")

enum class ResourceType {
    UNKNOWN("html"),
    LINK("html"),
    PDF("pdf"),
    DOC("doc"),
    JPEG("jpeg");

    val ext: String

    constructor(ext: String) {
        this.ext = ext
    }
}

open class MoodlePage(browser: Browser, var urlBase: String, url: String) : Page(browser, url) {
    val log = logger()
    val indexFileName = "index.html"

    init {
        if (!urlBase.startsWith("http")) {
            urlBase = "http://$urlBase"
        }
    }

    private val moodleBrand by lazy {
        `$`("a.brand").firstOrNull()
    }

    fun moodleBrand(): String {
        return moodleBrand?.text.orEmpty()
    }

    fun logout() {
        val logoutLink = `$`("a[href*='$urlBase/login/logout.php']", 0).getAttribute("href")
        browser.to(logoutLink)
    }


}

class LoginPage(browser: Browser, urlBase: String, url: String = "$urlBase/login/index.php") : MoodlePage(browser, urlBase, url) {
    private val loginButton by lazy {
        `$`("input#loginbtn", 0)
    }
    private val loginErrorMessage by lazy {
        `$`("a#loginerrormessage").firstOrNull()
    }
    private val errorMessage by lazy {
        `$`("span.error").firstOrNull()
    }

    private val loginForm by lazy {
        `$`("form#login", 0)
    }

    fun login(user: String, pwd: String): Result<DashboardPage> {
        var ret: Result<DashboardPage>
        try {
            loginForm.findElement(By.id("username")).sendKeys(user)
            loginForm.findElement(By.id("password")).sendKeys(pwd)
            waitFor { elementToBeClickable(loginButton) }
            loginButton.click()
            val dashboardPage = DashboardPage(browser, urlBase)
            if (dashboardPage.verifyAt()) {
                ret = Result(dashboardPage)
            } else if (loginErrorMessage != null && errorMessage != null) {
                ret = Result(false, errorMessage!!.text)
            } else {
                ret = Result(false, "Login not possible, please check web page for more information.")
            }
        } catch (e: Exception) {
            ret = Result(false, "Login not possible because of an error: $e.")
        }
        return ret
    }
}


class DashboardPage(browser: Browser, urlBase: String, url: String = "$urlBase/my/") : MoodlePage(browser, urlBase, url) {
    private val courseOverview by lazy {
        waitFor { presenceOfElementLocated(By.cssSelector("div[data-block='course_overview']")) }
        `$`("div[data-block='course_overview']", 0)
    }

    val courses by lazy {
        courseOverview.`$`("a[href*='$urlBase/course/']").map { Course(it.getAttribute("title"), it.getAttribute("href")) }
    }

    fun content(): String {
        return content(courseOverview)
    }

    fun toCoursePage(course: Course): CoursePage {
        toUrlIfNotCurrent()
        val ret = CoursePage(browser, urlBase, course)
        browser.to({ ret })
        return browser.at({ ret })
    }

    fun downloadTo(target: Path, statusUpdater: (String) -> Unit = {}) {
        toUrlIfNotCurrent()
        var coursesContent = content()

        courses.forEach { course ->
            val coursePage = toCoursePage(course)
            val fileName = coursePage.downloadTo(target, statusUpdater)
            coursesContent = coursesContent.replace("\"${course.link}\"", "\"$fileName\"")
            saveAsHtml(coursesContent, "", target.resolve(indexFileName))
        }
    }
}

class CoursePage(browser: Browser, urlBase: String, val course: Course) : MoodlePage(browser, urlBase, course.link) {
    private val content by lazy {
        `$`("div.course-content", 0)
    }

    val resources by lazy {
        content.`$`("a[href*='$urlBase/mod/']").map {
            Resource(findResourceTitle(it), it.getAttribute("href"), findResourceType(it))
        }
    }

    fun content(): String {
        return content(content)
    }

    fun toResourcePage(resource: Resource): ResourcePage {
        val ret = ResourcePage(browser, urlBase, resource)
        browser.to({ ret })
        return browser.at({ ret })
    }

    private fun findResourceTitle(el: WebElement): String {
        val titleEl = el.`$`("span.instancename", 0)
        var ret = titleEl.text
        titleEl.findElements(By.xpath("./*")).forEach {
            ret = ret.replace(it.text, "")
        }
        return ret.trim()
    }

    private fun findResourceType(it: WebElement): ResourceType {
        val ret: ResourceType
        val typeAsIconSrc = it.`$`("img").firstOrNull()?.getAttribute("src")?.substringAfterLast("/")
        if (typeAsIconSrc != null) {
            if (typeAsIconSrc.startsWith("jpeg")) {
                ret = ResourceType.JPEG
            } else if (typeAsIconSrc.startsWith("pdf")) {
                ret = ResourceType.PDF
            } else if (typeAsIconSrc.startsWith("icon")) {
                ret = ResourceType.LINK
            } else if (typeAsIconSrc.startsWith("document")) {
                ret = ResourceType.DOC
            } else {
                ret = ResourceType.UNKNOWN
            }
        } else {
            ret = ResourceType.UNKNOWN
        }
        return ret
    }

    fun downloadTo(target: Path, statusUpdater: (String) -> Unit = {}): String {
        toUrlIfNotCurrent()
        val ret = "${course.key}/$indexFileName"
        statusUpdater("Download course: ${course.title}")
        val coursePath = target.resolve(course.key)
        if (!coursePath.exists()) {
            coursePath.mkdirs()
        }

        var courseContent = content()

        resources.forEach { resource ->
            try {
                statusUpdater("Download resource: ${resource.title}")
                var localLink: String

                when (resource.type) {
                    ResourceType.PDF, ResourceType.DOC -> {
                        localLink = "${resource.key}.${resource.type.ext}"
                        try {
                            val targetFile = coursePath.resolve(localLink)
                            if (!targetFile.exists()) {
                                localLink = browser.downloadFile(resource.link, coursePath, resource.key, resource.type.ext)
                            }
                        } catch (e: Exception) {
                            val msg = "Download of $localLink not possible because of $e"
                            log.error(msg)
                            statusUpdater(msg)
                        }
                    }
                    else -> {
                        val resourcePage = toResourcePage(resource)
                        localLink = resourcePage.downloadTo(coursePath)
                    }
                }
                courseContent = courseContent.replace("\"${resource.link}\"", "\"$localLink\"")
                toUrlIfNotCurrent()
            } catch (e: Exception) {
                statusUpdater("Download resource failed: ${resource.title} becuase $e")
            }
        }
        saveAsHtml(courseContent, course.title, coursePath.resolve(indexFileName))
        return ret
    }
}

class ResourcePage(browser: Browser, urlBase: String, val resource: Resource) : MoodlePage(browser, urlBase, resource.link) {
    override val at = delegatesTo<Browser, Boolean> {
        true
    }

    private val content by lazy {
        `$`("div.resourcecontent", 0)
    }
    private val divMain by lazy {
        `$`("div[role='main']").firstOrNull()
    }

    fun downloadTo(target: Path): String {
        var localLink: String
        when (resource.type) {
            ResourceType.JPEG -> {
                val img = content.`$`("img.resourceimage", 0)
                val src = img.getAttribute("src")
                localLink = "${resource.key}.${src.substringAfterLast(".")}"
                try {
                    val targetFile = target.resolve(localLink)
                    if (!targetFile.exists()) {
                        localLink = browser.downloadFile(img.getAttribute("src"), target,
                                resource.key, src.substringAfterLast("."))
                    }
                } catch (e: Exception) {
                    println("Download of $localLink not possible because of $e")
                }
            }
            else -> {
                if (browser.currentUrl.startsWith(urlBase)) {
                    localLink = "${resource.key}.${resource.type.ext}"
                    if (divMain != null) {
                        saveAsHtml(content(divMain!!), browser.title, target.resolve(localLink))
                    } else {
                        saveAsHtml(source(), browser.title, target.resolve(localLink))
                    }
                } else {
                    localLink = browser.currentUrl
                }
            }
        }
        return localLink
    }
}

class Moodle() {
    private var browser: Browser? = null
    val courses = mutableListOf<Course>()
    var dashboardPage: DashboardPage? = null
    var statusUpdater: (String) -> Unit = {}

    init {
        Browser.registerDriver()
    }

    fun login(urlBase: String, username: String, password: String): Result<DashboardPage> {
        val ret: Result<DashboardPage>
        val currentBrowser = startBrowser()
        var loginPage: LoginPage = LoginPage(currentBrowser, urlBase)
        if (!loginPage.verifyAt()) {
            loginPage = currentBrowser.to { loginPage }
        }
        if (loginPage.waitFor({ ExpectedCondition { loginPage.verifyAt() } })) {
            ret = loginPage.login(username, password)
            if (ret.ok) {
                courses.clear()
                courses.addAll(ret.value!!.courses)
            }
        } else {
            ret = Result(false, "Login page cannot be loaded.")
        }
        dashboardPage = ret.value
        return ret
    }

    private fun startBrowser(): Browser {
        if (browser == null) {
            browser = Browser.new(ChromeDriver())
        }
        return browser!!
    }

    fun logout() {
        letTraceExc(false) { dashboardPage?.logout() }
        letTraceExc(false) { browser?.quit() }
    }
}

private fun saveAsHtml(content: String, title: String, target: Path) {
    target.toFile().printWriter().use { out ->
        out.println("""<hmtl><head><meta http-equiv="content-type" content="text/php; charset=UTF-8">
    <title>$title</title>
<style>

  .hidden {
    display: none;
    visibility: hidden;
  }

  .accesshide {
    position: absolute;
    left: -10000px;
    font-weight: normal;
    font-size: 1em;
  }

  p {
      text-indent: 20px;
  }

  div.title {
      font-weight: bold;
  }
</style>
</head><body>""")
        out.println(content)
        out.println("</body></hmtl>")
    }
}