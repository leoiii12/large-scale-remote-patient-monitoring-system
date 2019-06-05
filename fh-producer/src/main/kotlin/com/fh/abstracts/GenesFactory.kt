package com.fh.abstracts

import com.fh.Genes
import com.fh.config
import java.util.*
import java.util.concurrent.atomic.AtomicLong

class NormalGenesFactory : GenesFactory {

    private var _i = AtomicLong(0)
    private val _random = Random()

    override fun getGenes(): Genes {
        return Genes(0.5, 1 + _random.nextDouble(), config.HEALTHY_RATE, _i.getAndIncrement())
    }
}

class ClusteredGenesFactory : GenesFactory {

    override fun getGenes(): Genes {
        return Genes(0.5, 1.1, config.HEALTHY_RATE, Date().time / 100L);
    }

}

interface GenesFactory {
    fun getGenes(): Genes
}