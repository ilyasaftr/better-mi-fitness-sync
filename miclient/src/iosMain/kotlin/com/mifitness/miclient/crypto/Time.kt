package com.mifitness.miclient.crypto

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun currentUnixSeconds(): Long =
    NSDate().timeIntervalSince1970.toLong()
