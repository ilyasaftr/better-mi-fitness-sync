package com.mifitness.miclient.gps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SportRecordIndoorTest {

    private val start = 1_794_838_080L

    private fun u32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    /** A strength-training record as Smart Band 10 writes it: header + (hr, kcal tick, 0, 0) per second. */
    private fun record(bpm: List<Int>, count: Long = bpm.size.toLong()): ByteArray {
        val header = u32(start) + byteArrayOf(4, 3, 8, 0, 0xFF.toByte(), 0xBB.toByte()) + u32(count) + u32(start) +
            byteArrayOf(0x7F)
        val samples = bpm.flatMapIndexed { i, b -> listOf(b.toByte(), (if (i % 12 == 11) 1 else 0).toByte(), 0, 0) }
        return header + samples.toByteArray()
    }

    @Test
    fun oneReadingPerSecondFromTheStart() {
        val series = SportRecordBinary.parseSeries(record(listOf(0, 0, 89, 111, 156, 130)))
        assertEquals(
            listOf(
                SportRecordBinary.TimedValue(start + 2, 89.0),
                SportRecordBinary.TimedValue(start + 3, 111.0),
                SportRecordBinary.TimedValue(start + 4, 156.0),
                SportRecordBinary.TimedValue(start + 5, 130.0),
            ),
            series.heartRate,
        )
    }

    @Test
    fun aCountThatDoesNotMatchTheSizeIsNotThisLayout() {
        assertNull(SportRecordBinary.parseIndoorSecondSamples(record(listOf(100, 101, 102), count = 4)))
    }

    @Test
    fun withoutTheMarkerItIsNotThisLayout() {
        val data = record(listOf(100, 101, 102)).also { it[9] = 0 }
        assertNull(SportRecordBinary.parseIndoorSecondSamples(data))
    }

    @Test
    fun aLongWorkoutKeepsEverySecond() {
        val bpm = (0 until 5057).map { 90 + it % 60 }
        assertEquals(5057, SportRecordBinary.parseSeries(record(bpm)).heartRate.size)
    }
}
