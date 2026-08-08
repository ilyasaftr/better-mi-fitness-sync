package com.bettermifitness.sync.i18n

import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.desc.desc
import dev.icerock.moko.resources.format

actual object L10nPlatform {
    actual fun text(resource: StringResource): String = resource.desc().localized()

    actual fun format(resource: StringResource, args: Array<out Any>): String =
        resource.format(*args).localized()
}
