package ee.fiveh

import ee.common.ext.logger
import ee.email.Forwarding
import ee.grab.core.Browser

class AddOrReplaceForwardingsCommand(val host: String, val forwardings: List<Forwarding>, val user: String, val password: String) {
    val log = logger()
    val nop: Boolean = false

    fun execute() {
        FivehPage.HOST = host

        if (!nop) {
            log.info("Add or replace forwardings: {}", forwardings)
            Browser.drive {
                val controlCenter: ControlCenterPage
                val loginPage = to(::LoginPage)
                controlCenter = loginPage.login(user, password)
                val emailCenter = controlCenter.toEmailCenter()
                var emailManger = emailCenter.toEmailManager()
                emailManger = emailManger.addOrReplaceForwardings(forwardings)
                emailManger.logout()
            }
        } else {
            forwardings.forEach { log.info("$it") }
        }
    }
}
