package com.aurax.guard.ui

/** What the Home tab can ask the main screen to do. Keeps the old logic where it already lives. */
interface HomeActions {
    fun setGuard(on: Boolean)
    fun testTrigger()
    fun togglePark()
    fun fixSetup()
    fun openPhotos()
    fun openQueue()
    fun openControls()
    fun stopSiren()
    fun quickLock()
    fun panic()
}
