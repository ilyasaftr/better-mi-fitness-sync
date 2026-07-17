package com.bettermifitness.sync.health

/**
 * Bridges MainActivity's Health Connect permission launcher into [HealthWriter],
 * which lives outside the Activity lifecycle.
 */
object HealthConnectPermissionBridge {
    /**
     * Launches the system Health Connect permission UI and returns the granted set.
     * Set from [com.bettermifitness.sync.MainActivity] in onCreate.
     */
    var requestPermissions: (suspend (Set<String>) -> Set<String>)? = null

    /** Opens Health Connect settings or Play Store install page. */
    var openHealthConnect: (() -> Unit)? = null
}
