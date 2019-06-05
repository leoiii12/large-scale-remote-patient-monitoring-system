package com.fh

data class Genes(
        val increaseOrDecrease: Double, // Increase or decrease, e.g. 0.7 => when higher than 0.7, increase
        val rateCombo: Double, // In addition to the default rate, default rate * 1.1
        val healthyRate: Double, // Probability to go back to healthyRate, e.g. 1.0 => always healthy
        val seed: Long // Random's seed
) {

    init {
        if (increaseOrDecrease < 0 || increaseOrDecrease > 1.0) throw Exception("Out of range, increaseOrDecrease")
        if (healthyRate < 0 || healthyRate > 1.0) throw Exception("Out of range, healthyRate")
    }

}