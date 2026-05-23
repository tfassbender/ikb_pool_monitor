package com.tfassbender.ikbpool.domain

enum class Recommendation(val message: String) {
    SUPER("Super Zeit zum Schwimmen"),
    GUT("Gut geeignet zum Schwimmen"),
    OKAY("Noch akzeptabel trotz Reservierung"),
    SCHLECHT("Aktuell nicht empfehlenswert"),
    UNBEKANNT("Status nicht verfügbar"),
}
