package com.aurax.guard.smart

enum class SetupAction { TELEGRAM, PROTECTION, TEST }
data class SetupStep(val n: Int, val title: String, val hint: String, val done: Boolean, val action: SetupAction)

/** The short "first-time setup" list on Home. Pure rules. */
object SetupRules {
    fun steps(telegramOk: Boolean, guardOn: Boolean, permsOk: Boolean, hasAnyAlert: Boolean) = listOf(
        SetupStep(1, "Telegram jodein", "Bot token aur Chat ID daalein taki alert aapke phone par aaye.", telegramOk, SetupAction.TELEGRAM),
        SetupStep(2, "Protection chalu karein", "Guard ON aur Device Admin / Camera / Location allow karein.", guardOn && permsOk, SetupAction.PROTECTION),
        SetupStep(3, "Ek test karein", "Test Trigger dabayein aur Telegram par alert dekhein.", hasAnyAlert, SetupAction.TEST),
    )

    fun allDone(s: List<SetupStep>) = s.all { it.done }
    fun next(s: List<SetupStep>) = s.firstOrNull { !it.done }
}
