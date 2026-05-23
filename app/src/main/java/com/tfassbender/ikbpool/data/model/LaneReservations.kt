package com.tfassbender.ikbpool.data.model

data class LaneReservations(
    val lane1Reserved: Boolean,
    val lane2Reserved: Boolean,
    val lane3Reserved: Boolean,
) {
    val lowerThreeAllReserved: Boolean
        get() = lane1Reserved && lane2Reserved && lane3Reserved
}
