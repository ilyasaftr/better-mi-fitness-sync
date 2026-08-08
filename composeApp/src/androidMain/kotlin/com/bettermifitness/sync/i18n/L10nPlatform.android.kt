package com.bettermifitness.sync.i18n

import com.bettermifitness.sync.di.androidAppContext
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.format

actual object L10nPlatform {
    actual fun text(resource: StringResource): String =
        resource.getString(androidAppContext())

    actual fun format(resource: StringResource, args: Array<out Any>): String =
        resource.format(*args).toString(androidAppContext())
}
