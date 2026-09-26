package com.tanjid.contrastguard.model

data class RiskIndicator(
    val feature: String = "",
    val title: String = "",
    val description: String = "",
    val severity: String = "low" // critical, high, medium, low
)