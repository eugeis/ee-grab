package ee.moodle.fx

import javafx.event.EventHandler
import javafx.stage.Stage
import tornadofx.App
import tornadofx.importStylesheet

open class MoodleApp() : App() {
    override val primaryView = Dashboard::class
    val moodleController: MoodleController by inject()

    override fun start(stage: Stage) {
        stage.onCloseRequest = EventHandler { moodleController.exit() }
        importStylesheet(Styles::class)
        super.start(stage)
        moodleController.init()
    }
}