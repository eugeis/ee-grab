package ee.moodle.fx

import ee.moodle.Course
import ee.moodle.Moodle
import javafx.application.Platform
import tornadofx.Controller
import tornadofx.FX
import java.nio.file.Paths

class MoodleController : Controller() {
    val dashboard: Dashboard by inject()
    val moodle: Moodle = Moodle()

    fun init() {
        showDashboard()
    }

    fun showDashboard() {
        if (FX.primaryStage.scene.root != dashboard.root) {
            FX.primaryStage.scene.root = dashboard.root
            FX.primaryStage.sizeToScene()
            FX.primaryStage.centerOnScreen()
            FX.primaryStage.title = dashboard.title
        }

        Platform.runLater {
            with(config) {
                if (containsKey(URL)) {
                    dashboard.url.text = string(URL)
                }
                if (containsKey(USERNAME)) {
                    dashboard.username.text = string(USERNAME)
                }
                if (containsKey(PASSWORD)) {
                    dashboard.password.text = string(PASSWORD)
                }
                if (containsKey(DOWNLOAD_TO)) {
                    dashboard.downloadTo.text = string(DOWNLOAD_TO)
                }
            }
            dashboard.username.requestFocus()
        }
    }

    fun changeTitle(message: String, shake: Boolean = false) {
        dashboard.title = message

        Platform.runLater {
            if (shake) dashboard.shakeStage()
        }
    }

    fun tryLogin(url: String, username: String, password: String, remember: Boolean) {
        runAsync {
            moodle.logout()
            moodle.login(url, username, password)
        } ui { result ->

            if (result.ok) {
                with(config) {
                    if (remember) {
                        set(URL to url)
                        set(USERNAME to username)
                        set(PASSWORD to password)
                    } else {
                        clearSettings()
                    }
                    save()
                }
                val title = moodle.dashboardPage?.moodleBrand()
                changeTitle(title ?: dashboard.title)
                dashboard.refreshCourses()
            } else {
                changeTitle(result.message, true)
            }
        }
    }

    fun downloadTo(target: String, courses: List<Course>, remember: Boolean, statusUpdater: (String) -> Unit = {}) {
        runAsync {
            storeOrRemoveSettings(target, remember)
            val targetPath = Paths.get(target)
            courses.forEach { course ->
                moodle.dashboardPage?.toCoursePage(course)?.downloadTo(targetPath, statusUpdater)
            }
            statusUpdater("Done")
        } ui {}
    }


    fun downloadTo(target: String, remember: Boolean, statusUpdater: (String) -> Unit = {}) {
        runAsync {
            storeOrRemoveSettings(target, remember)
            moodle.dashboardPage?.downloadTo(Paths.get(target), statusUpdater)
            statusUpdater("Done")
        } ui {}
    }

    private fun storeOrRemoveSettings(target: String, remember: Boolean) {
        with(config) {
            if (remember) {
                set(DOWNLOAD_TO to target)
            } else {
                remove(DOWNLOAD_TO)
            }
            save()
        }
    }

    fun clearSettings() {
        with(config) {
            remove(URL)
            remove(DOWNLOAD_TO)
            remove(USERNAME)
            remove(PASSWORD)
            save()
        }
    }

    fun exit() {
        moodle.logout()
        Platform.exit()
    }

    companion object {
        val URL = "URL"
        val DOWNLOAD_TO = "DOWNLOAD_TO"
        val USERNAME = "username"
        val PASSWORD = "password"
    }

}