package com.fh.alerts

import com.google.gson.Gson
import java.util.*

class NormalRange {

    inner class Range(internal var max: Double, internal var min: Double)

    private val map = HashMap<String, Range>()

    init {
        val json = Scanner(javaClass.classLoader.getResourceAsStream("NormalRange.json")!!, "UTF-8").useDelimiter("\\A").next()
        val gson = Gson()

        println(json)

        var map: Map<String, Map<String, Double>> = HashMap()

        map = gson.fromJson(json, map.javaClass)

        map.forEach { key, value ->
            val max = value["max"]
            val min = value["min"]

            if (max == null || min == null){
                throw NoSuchFieldError()
            }

            this.map[key] = Range(max, min)
        }
    }

    fun isInRange(unit: String, value: Double): Boolean {
        val range = map[unit] ?: return true

        return range.min <= value && value <= range.max
    }

}