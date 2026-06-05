package com.specthinker.testutil

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class FixedClock(private val now: Instant) : Clock() {
    override fun getZone(): ZoneOffset = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?): Clock = this
    override fun instant(): Instant = now
}
