package com.mifitness.miclient.crypto

internal actual fun currentUnixSeconds(): Long = System.currentTimeMillis() / 1000
