package ee.moodle.fx

import ee.moodle.Course
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Platform
import javafx.beans.property.Property
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.stage.DirectoryChooser
import javafx.util.Duration
import tornadofx.*

class Dashboard : View() {
    override val root = BorderPane()
    val moodleController: MoodleController by inject()
    val moodle = moodleController.moodle
    var url: TextField by singleAssign()
    var username: TextField by singleAssign()
    var password: PasswordField by singleAssign()
    var remember: CheckBox by singleAssign()
    var downloadTo: TextField by singleAssign()
    var courses = FXCollections.observableArrayList<Course>()
    var coursesSelectionModel: TableView.TableViewSelectionModel<Course>? = null
    var table: TableView<Course>? = null
    var status: SimpleStringProperty = SimpleStringProperty()
    var statusUpdater: (String) -> Unit = {
        Platform.runLater { status.value = it }
    }

    init {
        title = "Moodle Dashboard"

        with(root) {
            setPrefSize(800.0, 400.0)
            top {
                vbox {
                    addClass(Styles.spaces)
                    borderpane {
                        center {
                            hbox {
                                label("URL") {
                                    hboxConstraints { margin = Insets(5.0) }
                                }
                                url = textfield("http://www.lza.de/intern") {
                                    hboxConstraints {
                                        hGrow = Priority.ALWAYS
                                        margin = Insets(5.0)
                                    }
                                }
                            }
                        }
                        right {
                            hbox {
                                button("Login") {
                                    hboxConstraints { margin = Insets(5.0) }
                                    isDefaultButton = true

                                    setOnAction {
                                        moodleController.tryLogin(url.text, username.text, password.text,
                                            remember.isSelected)
                                    }
                                }
                                button("Exit") {
                                    hboxConstraints { margin = Insets(5.0) }
                                    setOnAction {
                                        moodleController.exit()
                                    }
                                }
                            }
                        }
                    }
                    hbox {
                        vboxConstraints { marginTop = 5.0 }
                        hbox {
                            label("User") {
                                hboxConstraints { margin = Insets(5.0) }
                            }
                            username = textfield() {
                                hboxConstraints { margin = Insets(5.0) }
                                prefWidth(10.0)
                            }
                        }
                        hbox {
                            label("Password") {
                                hboxConstraints { margin = Insets(5.0) }
                            }
                            password = passwordfield() {
                                hboxConstraints { margin = Insets(5.0) }
                                prefWidth(10.0)
                            }
                        }
                        hbox {
                            label("Remember") {
                                hboxConstraints { margin = Insets(5.0) }
                            }
                            remember = checkbox() {
                                hboxConstraints { margin = Insets(5.0) }
                            }
                        }
                        button("Clear") {
                            hboxConstraints { margin = Insets(5.0) }
                            setOnAction {
                                clear()
                            }
                        }
                    }
                }
            }
            center {
                table = tableview(courses) {
                    coursesSelectionModel = selectionModel
                    selectionModel.selectionMode = SelectionMode.MULTIPLE
                    column("Course", Course::titleProperty).remainingWidth()
                    columnResizePolicy = SmartResize.POLICY
                }
            }
            bottom {
                borderpane {
                    center {
                        hbox {
                            label("Download to: ") {
                                hboxConstraints { margin = Insets(5.0) }
                                alignment = Pos.CENTER_LEFT
                                contentDisplay = ContentDisplay.LEFT
                            }
                            downloadTo = textfield() {
                                hboxConstraints {
                                    margin = Insets(5.0)
                                    hGrow = Priority.ALWAYS
                                }
                            }
                            button("...") {
                                hboxConstraints { margin = Insets(5.0) }
                                setOnAction {
                                    val directoryChooser = DirectoryChooser()
                                    val selectedDirectory = directoryChooser.showDialog(primaryStage)

                                    if (selectedDirectory != null) {
                                        downloadTo.text = selectedDirectory.absolutePath
                                    }
                                }
                            }
                        }
                    }
                    right {
                        hbox {
                            button("DownloadAll") {
                                hboxConstraints { margin = Insets(5.0) }
                                setOnAction {
                                    moodleController.downloadTo(downloadTo.text, remember.isSelected, statusUpdater)
                                }
                            }
                            button("Download") {
                                hboxConstraints { margin = Insets(5.0) }
                                setOnAction {
                                    moodleController.downloadTo(downloadTo.text, coursesSelectionModel?.selectedItems!!,
                                        remember.isSelected, statusUpdater)
                                }
                            }
                        }
                    }
                    bottom {
                        vbox {
                            separator()
                            label(status) {
                                hboxConstraints {
                                    padding = Insets(2.0)
                                    margin = Insets(5.0)
                                    hGrow = Priority.ALWAYS
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    fun refreshCourses() {
        if (moodle.dashboardPage?.courses != null) {
            courses.clear()
            courses.addAll(moodle.dashboardPage!!.courses)
            table?.resizeColumnsToFitContent()
        }
    }

    fun clear() {
        url.clear()
        username.clear()
        password.clear()
        remember.isSelected = false
        moodleController.clearSettings()
    }

    fun shakeStage() {
        var x = 0
        var y = 0
        val cycleCount = 10
        val move = 10
        val keyframeDuration = Duration.seconds(0.04)

        val stage = FX.primaryStage

        val timelineX = Timeline(KeyFrame(keyframeDuration, EventHandler {
            if (x == 0) {
                stage.x = stage.x + move
                x = 1
            } else {
                stage.x = stage.x - move
                x = 0
            }
        }))

        timelineX.cycleCount = cycleCount
        timelineX.isAutoReverse = false

        val timelineY = Timeline(KeyFrame(keyframeDuration, EventHandler {
            if (y == 0) {
                stage.y = stage.y + move
                y = 1;
            } else {
                stage.y = stage.y - move
                y = 0;
            }
        }))

        timelineY.cycleCount = cycleCount;
        timelineY.isAutoReverse = false;

        timelineX.play()
        timelineY.play();
    }
}

val Course.titleProperty: Property<String>
    get() = SimpleStringProperty(title)