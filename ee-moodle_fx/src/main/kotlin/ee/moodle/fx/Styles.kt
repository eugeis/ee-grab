package ee.moodle.fx

import tornadofx.Stylesheet
import tornadofx.box
import tornadofx.cssclass
import tornadofx.px

class Styles : Stylesheet() {
    companion object {
        val loginScreen by cssclass()
        val dashboard by cssclass()
        val spaces by cssclass()
    }

    init {
        select(loginScreen) {
            padding = box(15.px)
            vgap = 7.px
            hgap = 10.px
        }

        select(dashboard) {
            padding = box(10.px)
            vgap = 7.px
            hgap = 10.px
        }

        select(spaces) {
            barGap = (10.px)
            padding = box(10.px)
            vgap = 7.px
            hgap = 10.px
        }

    }
}