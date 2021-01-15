package benchmarks.collections

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import parsley.collections.IntMap
import kotlin.random.Random

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.NANOSECONDS)
open class IntMap {

    @Param("10", "100", "1000")
    private var size: Int = 0

    private lateinit var charMap: IntMap<Int>
    private lateinit var hashMap: Map<Int, Int>
    private lateinit var values: IntArray

    @Setup
    fun setup() {
        charMap = IntMap.empty()
        hashMap = mutableMapOf()
        values = sequence { yield(Random.nextInt(Int.MIN_VALUE, Int.MAX_VALUE)) }.toList().toIntArray()
        values.forEach { c ->
            charMap[c] = c
            (hashMap as MutableMap<Int, Int>)[c] = c
        }
        values.shuffle()
    }

    @Benchmark
    fun lookupCharToIntMap() {
        values.forEach { c ->
            charMap[c]
        }
    }

    @Benchmark
    fun lookupHashMap() {
        values.forEach { c ->
            hashMap[c]
        }
    }

}
