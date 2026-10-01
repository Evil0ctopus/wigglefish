package com.wigglefish.android

import java.util.ArrayDeque

internal data class SignalSample(val observedAtMillis: Long, val rssiDbm: Int)

internal data class SignalTrackSnapshot(
    val sampleCount: Int,
    val elapsedMillis: Long,
    val latestRssiDbm: Int?,
    val medianChangeDb: Int?,
    val trend: SignalTrend,
)

internal enum class SignalTrend(val label: String) {
    INSUFFICIENT("INSUFFICIENT HISTORY"),
    STRONGER("SIGNAL GETTING STRONGER"),
    WEAKER("SIGNAL GETTING WEAKER"),
    STABLE("SIGNAL RELATIVELY STABLE"),
}

internal class AirspaceSignalTracker(
    private val windowMillis: Long = 90_000L,
    private val minimumDurationMillis: Long = 15_000L,
    private val minimumSamples: Int = 4,
    private val changeThresholdDb: Int = 4,
    private val maximumSamples: Int = 120,
) {
    private val samples = ArrayDeque<SignalSample>()

    fun record(rssiDbm: Int, observedAtMillis: Long) {
        samples.addLast(SignalSample(observedAtMillis, rssiDbm))
        while (samples.size > maximumSamples) samples.removeFirst()
        trim(observedAtMillis)
    }

    fun clear() = samples.clear()
    fun recentSamples(limit: Int = 5): List<SignalSample> = samples.toList().takeLast(limit)

    fun snapshot(nowMillis: Long): SignalTrackSnapshot {
        trim(nowMillis)
        val current = samples.toList()
        val elapsed = if (current.size < 2) 0L else current.last().observedAtMillis - current.first().observedAtMillis
        if (current.size < minimumSamples || elapsed < minimumDurationMillis) {
            return SignalTrackSnapshot(current.size, elapsed, current.lastOrNull()?.rssiDbm, null, SignalTrend.INSUFFICIENT)
        }

        val split = current.size / 2
        val baselineMedian = median(current.take(split).map { it.rssiDbm })
        val recentMedian = median(current.drop(split).map { it.rssiDbm })
        val change = recentMedian - baselineMedian
        val trend = when {
            change >= changeThresholdDb -> SignalTrend.STRONGER
            change <= -changeThresholdDb -> SignalTrend.WEAKER
            else -> SignalTrend.STABLE
        }
        return SignalTrackSnapshot(current.size, elapsed, current.last().rssiDbm, change, trend)
    }

    private fun trim(nowMillis: Long) {
        val cutoff = nowMillis - windowMillis
        while (samples.isNotEmpty() && samples.first().observedAtMillis < cutoff) samples.removeFirst()
    }

    private fun median(values: List<Int>): Int {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2
        } else {
            sorted[middle]
        }
    }
}
