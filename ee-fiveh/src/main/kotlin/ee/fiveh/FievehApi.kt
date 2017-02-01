package ee.fiveh

import ee.email.*
import ee.grab.core.Browser
import ee.grab.core.Page
import ee.grab.extensions.`$`
import ee.grab.libs.delegatesTo
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable
import java.util.*

open class FivehPage : Page() {
    companion object {
        var HOST = ""
    }

    fun logout() {
        val logoutLink = `$`("p#loginout", 0).findElement(By.tagName("a"))
        waitFor { elementToBeClickable(logoutLink) }
        logoutLink.click()
    }
}

class LoginPage : FivehPage() {
    override val url = "https://my.$HOST.com/de/5cloud/customer/login"
    override val at = delegatesTo<Browser, Boolean> {
        `$`("h1", 0).text == "Willkommen im $HOST Control Center"
    }

    private val loginButton by lazy {
        `$`("input#login", 0)
    }

    private val loginForm by lazy {
        `$`("form#login", 0)
    }

    fun login(user: String, pwd: String): ControlCenterPage {
        loginForm.findElement(By.id("usr_d_id")).sendKeys(user)
        loginForm.findElement(By.id("usr_d_password")).sendKeys(pwd)
        waitFor { elementToBeClickable(loginButton) }
        loginButton.click()

        return browser.at(::ControlCenterPage)
    }
}

open class EmailCenterPage : FivehPage() {
    override val url = "https://my.$HOST.com/de/$HOST/email"
    override val at = delegatesTo<Browser, Boolean> {
        `$`("h2", 0).text == "Email"
    }

    private val emailCenterLink by lazy {
        `$`("a[href='/de/$HOST/email']", 0)
    }

    private val emailManagerLink by lazy {
        `$`("a[href='/de/$HOST/email/manager']", 0)
    }

    private val addAccountLink by lazy {
        `$`("a[href='/de/$HOST/email/add_account']", 0)
    }

    private val addForwardingLink by lazy {
        `$`("a[href='/de/$HOST/email/add_forwarding']", 0)
    }

    private val addFetcherLink by lazy {
        `$`("a[href='/de/$HOST/email/add_fetcher']", 0)
    }

    private val addImportMailboxLink by lazy {
        `$`("a[href='/de/$HOST/email/import_mailbox']", 0)
    }

    fun toEmailCenter(): EmailCenterPage {
        emailCenterLink.click()
        return browser.at(::EmailCenterPage)
    }

    fun toEmailManager(): EmailManagerPage {
        emailManagerLink.click()
        return browser.at(::EmailManagerPage)
    }

    fun toAddAccount(): AddEmailAccountPage {
        addAccountLink.click()
        return browser.at(::AddEmailAccountPage)
    }

    fun toAddForwarding(): AddForwardingPage {
        addForwardingLink.click()
        return browser.at(::AddForwardingPage)
    }

    fun toAddFetcher(): AddFetcherPage {
        addFetcherLink.click()
        return browser.at(::AddFetcherPage)
    }

    fun toImportMailbox(): ImportMailboxPage {
        addImportMailboxLink.click()
        return browser.at(::ImportMailboxPage)
    }
}

class EmailManagerPage : FivehPage() {
    override val url = "https://my.$HOST.com/de/$HOST/email/manager"
    override val at = delegatesTo<Browser, Boolean> {
        `$`("h2", 0).text == "Email Manager"
    }

    private val emailCenterLink by lazy {
        `$`("a[href='/de/$HOST/email']", 0)
    }

    fun toEmailCenter(): EmailCenterPage {
        emailCenterLink.click()
        return browser.at(::EmailCenterPage)
    }

    fun collectDomainNames(): List<String> {
        val ret = ArrayList<String>()

        `$`("h3").forEach {
            ret.add(it.text.substringBeforeLast(" - "))
        }
        return ret
    }

    fun addOrReplaceForwarding(forwarding: Forwarding): AddForwardingResultPage {
        val page = deleteForwardingIfExists(forwarding.from)
        return page.addForwarding(forwarding)
    }

    fun addOrReplaceForwardings(forwardings: List<Forwarding>): EmailManagerPage {
        var emailManager = this
        forwardings.forEach {
            println("Add or replace forwarding: ${it.from}")
            emailManager = emailManager.addOrReplaceForwarding(it).toEmailManager()
            println("Done for ${it.from}. Sleep 10 sec")
            Thread.sleep(10000)
        }
        return emailManager
    }

    fun deleteForwardingIfExists(from: EmailAddress): EmailManagerPage {
        val links = `$`("option[value='/de/$HOST/email/delete_forwarding/${from.domain()}/${from.name()}']")
        if (links.isNotEmpty()) {
            val link = links.first()
            waitFor { elementToBeClickable(link) }
            link.click()
            val page = browser.at(::DeleteForwardingPage)
            return page.confirm(from)
        } else {
            return this
        }
    }

    fun addForwarding(forwarding: Forwarding): AddForwardingResultPage {
        val link = `$`("a[href='/de/$HOST/email/add_forwarding/${forwarding.from.domain()}']", 0)
        waitFor { elementToBeClickable(link) }
        link.`$`("div", 0).click()
        val page = browser.at(::AddForwardingPage)
        return page.add(forwarding)
    }

    fun collectDomains(): List<EmailDomain> {
        val ret = ArrayList<EmailDomain>()

        `$`("h3").forEach {
            println(it.text)
            val accounts = ArrayList<EmailAddress>()
            val forwardings = ArrayList<Forwarding>()
            val tablesDiv = it.findElement(By.xpath("following-sibling::div[contains(@class, 'center_content_box')]"))

            val tables = tablesDiv.`$`("table")
            if (tables.size > 0) {
                val accountRows = tables.get(0).`$`("tr")
                accountRows.subList(1, accountRows.size).forEach {
                    accounts.add(EmailAddress(it.`$`("td", 0).text))
                }
            }

            if (tables.size >= 2) {
                val forwardigRows = tables.get(1).`$`("tr")
                forwardigRows.subList(1, forwardigRows.size).forEach {
                    val forwarding = Forwarding(EmailAddress(it.`$`("td", 0).text),
                            it.`$`("td", 1).text.split("<br>").map { EmailAddress(it) }.toMutableList())
                    forwardings.add(forwarding)
                }
            }
            ret.add(EmailDomain(it.text.substringBeforeLast(" - "), accounts, forwardings))
        }
        return ret
    }
}

class AddEmailAccountPage : FivehPage() {
    override val url = "https://my.$HOST.com/de/$HOST/email/manager"
    override val at = delegatesTo<Browser, Boolean> {
        `$`("h2", 0).text == "Email"
    }

    private val emailCenterLink by lazy {
        `$`("a[href='/de/$HOST/email']", 0)
    }

    fun toEmailCenter(): EmailCenterPage {
        emailCenterLink.click()
        return browser.at(::EmailCenterPage)
    }
}

class DeleteForwardingPage : FivehPage() {
    override val url = "https://my.$HOST.com/de/$HOST/email/delete_forwarding"
    override val at = delegatesTo<Browser, Boolean> {
        `$`("h2", 0).text == "Weiterleitung löschen"
    }

    fun confirm(from: EmailAddress): EmailManagerPage {
        val link = `$`("a[href='/de/$HOST/email/delete_forwarding_proceed/${from.domain()}/${from.name()}']", 0)
        waitFor { elementToBeClickable(link) }
        link.`$`("div", 0).click()

        return browser.at(::EmailManagerPage)
    }
}

class AddForwardingPage : FivehPage() {
    override val url = "https://my.$HOST.com/de/$HOST/email/add_forwarding"
    override val at = delegatesTo<Browser, Boolean> {
        `$`("h2", 0).text == "Weiterleitung hinzufügen"
    }

    private val forwardingForm by lazy {
        `$`("form#contact", 0)
    }

    private val addButton by lazy {
        `$`("input#add", 0)
    }

    fun add(forwarding: Forwarding): AddForwardingResultPage {

        forwardingForm.findElement(By.id("email_forw_source")).sendKeys(forwarding.from.name())
        //forwardingForm.findElement(By.id("email_domainname")).sendKeys(forwarding.from.design())
        forwardingForm.findElement(By.id("email_forw_destination")).sendKeys(forwarding.to.joinToString(", ") {
            it.address.replace("@gmail.com", "@googlemail.com")
        })

        waitFor { elementToBeClickable(addButton) }
        addButton.click()

        return browser.at(::AddForwardingResultPage)
    }
}

class AddForwardingResultPage : EmailCenterPage() {
    override val url = "https://my.$HOST.com/de/$HOST/email/add_forwarding"
    override val at = delegatesTo<Browser, Boolean> {
        `$`("div#content_content", 0).text.contains("Sie haben erfolgreich die Email-Weiterleitung eingerichtet.")
    }
}

class AddFetcherPage : FivehPage() {
    override val url = "https://my.$HOST.com/de/$HOST/email/manager"
    override val at = delegatesTo<Browser, Boolean> {
        `$`("h2", 0).text == "Fetcher hinzufügen"
    }

    private val emailCenterLink by lazy {
        `$`("a[href='/de/$HOST/email']", 0)
    }

    fun toEmailCenter(): EmailCenterPage {
        emailCenterLink.click()
        return browser.at(::EmailCenterPage)
    }
}

class ImportMailboxPage : FivehPage() {
    override val url = "https://my.$HOST.com/de/$HOST/email/manager"
    override val at = delegatesTo<Browser, Boolean> {
        `$`("h2", 0).text == "Postfachumzug"
    }

    private val emailCenterLink by lazy {
        `$`("a[href='/de/$HOST/email']", 0)
    }

    fun toEmailCenter(): EmailCenterPage {
        emailCenterLink.click()
        return browser.at(::EmailCenterPage)
    }
}

class ControlCenterPage : FivehPage() {
    override val at = delegatesTo<Browser, Boolean> {
        `$`("h1", 0).text == "Willkommen in Ihrem $HOST Control Center"
    }

    private val customerDataLink by lazy {
        `$`("a[href='/de/$HOST/customer/data']", 0)
    }

    private val customerBillsLink by lazy {
        `$`("a[href='/de/$HOST/customer/bills']", 0)
    }

    private val webserverCenterLink by lazy {
        `$`("a[href='/de/$HOST/webserver']", 0)
    }

    private val databaseCenterLink by lazy {
        `$`("a[href='/de/$HOST/database']", 0)
    }

    private val emailCenterLink by lazy {
        `$`("a[href='/de/$HOST/email']", 0)
    }


    fun toCustomerData() {
        waitFor { elementToBeClickable(customerDataLink) }
        customerDataLink.`$`("div", 0).click()
    }

    fun toCustomerBills() {
        waitFor { elementToBeClickable(customerBillsLink) }
        customerBillsLink.`$`("div", 0).click()
    }

    fun toWebserverCenter() {
        waitFor { elementToBeClickable(webserverCenterLink) }
        webserverCenterLink.`$`("div", 0).click()
    }

    fun toDatabaseCenter() {
        waitFor { elementToBeClickable(databaseCenterLink) }
        databaseCenterLink.`$`("div", 0).click()
    }

    fun toEmailCenter(): EmailCenterPage {
        emailCenterLink.`$`("div", 0).click()
        return browser.at(::EmailCenterPage)
    }
}