package com.tfassbender.ikbpool.data.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

data class OpeningWindow(val open: LocalTime, val close: LocalTime) {
    fun contains(time: LocalTime): Boolean = !time.isBefore(open) && time.isBefore(close)
}

data class OpeningHours(val schedule: Map<DayOfWeek, OpeningWindow?>) {

    fun windowFor(day: DayOfWeek): OpeningWindow? = schedule[day]

    fun isOpenAt(time: LocalDateTime): Boolean {
        val window = schedule[time.dayOfWeek] ?: return false
        return window.contains(time.toLocalTime())
    }
}
