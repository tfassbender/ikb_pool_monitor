package com.tfassbender.ikbpool.data.model

@JvmInline
value class Occupancy(val percent: Int) {
    init {
        require(percent in 0..100) { "occupancy percent out of range: $percent" }
    }
}
