package com.fh

import java.util.*


class Person(private val genes: Genes) {

    private val _random = Random(genes.seed)

    fun value(current: Double, max: Double, min: Double, rate: Double): Double {
        var newValue = current

        val isIncrease = _random.nextDouble() > genes.increaseOrDecrease
        if (isIncrease) {
            newValue += (rate * genes.rateCombo)
        } else {
            newValue -= (rate * genes.rateCombo)
        }

        val goHealthy = _random.nextDouble() <= genes.healthyRate
        if (newValue > max) {
            if (goHealthy) newValue = max
        }
        if (newValue < min) {
            if (goHealthy) newValue = min
        }

        return newValue
    }

}