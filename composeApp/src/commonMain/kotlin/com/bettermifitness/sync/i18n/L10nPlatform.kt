package com.bettermifitness.sync.i18n

import dev.icerock.moko.resources.StringResource

/** Platform-backed system-locale resolution for shared Kotlin state. */
expect object L10nPlatform {
    fun text(resource: StringResource): String
    fun format(resource: StringResource, args: Array<out Any>): String
}
