package com.mifitness.miclient.gps

/**
 * Best-effort parser for Mi sport **RECORD** FDS blobs (`fileType = 0`).
 *
 * Layout (from [SportRecordBaseParser.parseFourDimenData]):
 * - 7-byte server data id + 1 reserved + dataValid
 * - Segments: pauseInit (opt) + count(u32) + startTime(u32) + IT summary + loop of packed samples
 *
 * Field type ids ([SportRecordBaseParser.DataItemType]):
 * hr=5, steps=4, distance=9, pace=12, cadence=49, speed=51, heightValue=87, integerKM=6
 *
 * Full four-dimen bit packing varies by sport; this extracts the common outdoor/step fields
 * when the bit layout matches OutdoorStepRecordParser, and falls back to scanning 1-byte HR.
 */
object SportRecordBinary {

    data class TimedValue(val timeSec: Long, val value: Double)

    data class KmSplit(val kilometer: Int, val timeSec: Long, val paceSecPerKm: Double? = null)

    data class Series(
        val heartRate: List<TimedValue> = emptyList(),
        val paceSecPerKm: List<TimedValue> = emptyList(),
        val cadenceSpm: List<TimedValue> = emptyList(),
        val speedMps: List<TimedValue> = emptyList(),
        val elevationM: List<TimedValue> = emptyList(),
        val kmSplits: List<KmSplit> = emptyList(),
    )

    fun parseSeries(data: ByteArray, sessionStartHint: Long = 0L): Series {
        if (data.size < 16) return Series()
        val version = data[5].toInt() and 0xFF
        val validLen = guessValidLen(version, data)
        val bodyOff = 7 + 1 + validLen
        if (bodyOff >= data.size) return Series()
        val body = data.copyOfRange(bodyOff, data.size)

        // Prefer structured outdoor-step style segment parse
        val structured = parseOutdoorStyleSegments(body, sessionStartHint)
        if (structured.heartRate.size >= 10 || structured.paceSecPerKm.isNotEmpty()) {
            return structured
        }
        // Fallback: 1 Hz HR bytes after a start time
        return Series(heartRate = scanHrBytes(body, sessionStartHint))
    }

    /**
     * Outdoor step record validity often 2–7 bytes; GPS used 1–2.
     * Try common lengths that leave a sensible body.
     */
    private fun guessValidLen(version: Int, data: ByteArray): Int {
        // Prefer sport-step table: v1→2 … v9→7
        val preferred = when (version) {
            1 -> 2
            2, 3, 4 -> 3
            5 -> 5
            6, 7 -> 6
            8, 9 -> 7
            else -> 2
        }
        if (7 + 1 + preferred < data.size) return preferred
        for (len in listOf(2, 3, 5, 6, 7, 1, 4)) {
            if (7 + 1 + len < data.size) return len
        }
        return 2
    }

    private fun parseOutdoorStyleSegments(body: ByteArray, sessionStartHint: Long): Series {
        val hr = ArrayList<TimedValue>()
        val pace = ArrayList<TimedValue>()
        val cadence = ArrayList<TimedValue>()
        val speed = ArrayList<TimedValue>()
        val elev = ArrayList<TimedValue>()
        val splits = ArrayList<KmSplit>()
        var off = 0
        // Limit segments to avoid runaway on garbage
        var segments = 0
        while (off + 12 <= body.size && segments < 8) {
            // Optional pause init: 4-byte time (diff or absolute)
            // count + startTime
            // Try both with and without leading 4-byte init
            val tryOffsets = listOf(off, off + 4).filter { it + 8 <= body.size }
            var advanced = false
            for (base in tryOffsets) {
                val count = u32(body, base).toInt()
                val start = u32(body, base + 4)
                if (count !in 1..50_000) continue
                // Heuristic: start near session or absolute unix
                val startOk = start in 1_000_000_000L..2_200_000_000L ||
                    (sessionStartHint > 0 && kotlin.math.abs(start - sessionStartHint) < 7 * 86400)
                if (!startOk && start > 100_000) continue

                // After header: skip small IT summary (0–32 bytes) by probing sample stride
                val afterHeader = base + 8
                val sampleStart = findSampleStart(body, afterHeader, count)
                if (sampleStart < 0) continue

                val stride = (body.size - sampleStart) / count
                if (stride !in 1..32) continue

                val absStart = if (start > 1_000_000_000L) start else sessionStartHint + start
                extractWithStride(body, sampleStart, count, stride, absStart, hr, pace, cadence, speed, elev, splits)
                off = sampleStart + count * stride
                advanced = true
                segments++
                break
            }
            if (!advanced) break
        }
        return Series(hr, pace, cadence, speed, elev, splits)
    }

    private fun findSampleStart(body: ByteArray, from: Int, count: Int): Int {
        // Probe offsets 0..40 for dense plausible HR stream
        for (skip in 0..40) {
            val start = from + skip
            if (start >= body.size) return -1
            val rem = body.size - start
            if (count <= 0 || rem < count) continue
            if (rem % count != 0 && rem / count < 1) continue
            val stride = rem / count
            if (stride !in 1..24) continue
            var good = 0
            val n = minOf(count, 40)
            for (i in 0 until n) {
                val b = body[start + i * stride].toInt() and 0xFF
                // HR often first byte of sample or alone
                if (b in 50..210) good++
                if (stride >= 2) {
                    val b2 = body[start + i * stride + 1].toInt() and 0xFF
                    if (b2 in 50..210) good++
                }
            }
            if (good >= n / 2) return start
        }
        return -1
    }

    private fun extractWithStride(
        body: ByteArray,
        start: Int,
        count: Int,
        stride: Int,
        absStart: Long,
        hr: MutableList<TimedValue>,
        pace: MutableList<TimedValue>,
        cadence: MutableList<TimedValue>,
        speed: MutableList<TimedValue>,
        elev: MutableList<TimedValue>,
        splits: MutableList<KmSplit>,
    ) {
        var km = 0
        for (i in 0 until count) {
            val o = start + i * stride
            if (o + stride > body.size) break
            val t = absStart + i
            // Common layouts: [hr] or [flags][hr] or multi-field LE
            val b0 = body[o].toInt() and 0xFF
            if (b0 in 45..220) {
                hr += TimedValue(t, b0.toDouble())
            } else if (stride >= 2) {
                val b1 = body[o + 1].toInt() and 0xFF
                if (b1 in 45..220) hr += TimedValue(t, b1.toDouble())
            }
            if (stride >= 4) {
                // try u16 pace at +2
                val p = u16(body, o + (if (stride >= 6) 2 else 0))
                if (p in 120..3600) pace += TimedValue(t, p.toDouble())
            }
            if (stride >= 3) {
                val cad = body[o + stride - 1].toInt() and 0xFF
                if (cad in 60..250) cadence += TimedValue(t, cad.toDouble())
            }
            // integer km markers sometimes sparse
            if (stride >= 8) {
                val dist = u32(body, o)
                if (dist > 0 && dist % 1000 == 0L && dist / 1000 <= 100) {
                    val k = (dist / 1000).toInt()
                    if (k > km) {
                        km = k
                        splits += KmSplit(k, t, null)
                    }
                }
            }
        }
        // elevation from floats if stride large
        if (stride >= 8) {
            for (i in 0 until minOf(count, 5000)) {
                val o = start + i * stride
                if (o + 8 > body.size) break
                val f = floatLe(body, o + 4)
                if (f in -100f..9000f && kotlin.math.abs(f) > 0.5f) {
                    elev += TimedValue(absStart + i, f.toDouble())
                }
            }
        }
        // speed m/s as u16/100
        if (stride >= 4 && speed.isEmpty()) {
            for (i in 0 until minOf(count, 5000)) {
                val o = start + i * stride + 2
                if (o + 2 > body.size) break
                val s = u16(body, o) / 100.0
                if (s in 0.5..15.0) speed += TimedValue(absStart + i, s)
            }
        }
    }

    private fun scanHrBytes(body: ByteArray, sessionStartHint: Long): List<TimedValue> {
        if (body.size < 20) return emptyList()
        // Find longest run of bytes in HR range
        var bestStart = 0
        var bestLen = 0
        var curStart = 0
        var curLen = 0
        for (i in body.indices) {
            val b = body[i].toInt() and 0xFF
            if (b in 50..210) {
                if (curLen == 0) curStart = i
                curLen++
                if (curLen > bestLen) {
                    bestLen = curLen
                    bestStart = curStart
                }
            } else {
                curLen = 0
            }
        }
        if (bestLen < 30) return emptyList()
        val base = if (sessionStartHint > 1_000_000_000L) sessionStartHint else 0L
        return (0 until bestLen).map { i ->
            TimedValue(base + i, (body[bestStart + i].toInt() and 0xFF).toDouble())
        }
    }

    /** Recover-rate file: sequence of HR after workout. */
    fun parseRecoverHr(data: ByteArray, recoverStartSec: Long): List<TimedValue> {
        if (data.size < 12) return emptyList()
        val version = data[5].toInt() and 0xFF
        val validLen = when (version) {
            in 1..5 -> 1
            else -> 2
        }
        val bodyOff = 7 + 1 + validLen
        if (bodyOff >= data.size) return scanHrBytes(data.copyOfRange(minOf(bodyOff, data.size - 1), data.size), recoverStartSec)
        val body = data.copyOfRange(bodyOff, data.size)
        return scanHrBytes(body, recoverStartSec)
    }

    private fun u32(b: ByteArray, o: Int): Long {
        if (o + 4 > b.size) return 0
        return (b[o].toLong() and 0xFF) or
            ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or
            ((b[o + 3].toLong() and 0xFF) shl 24)
    }

    private fun u16(b: ByteArray, o: Int): Int {
        if (o + 2 > b.size) return 0
        return (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    }

    private fun floatLe(b: ByteArray, o: Int): Float {
        val bits = (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)
        return Float.fromBits(bits)
    }
}
