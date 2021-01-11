package benchmarks.collections

/*
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import parsley.internal.collections.CharToIntMap
import parsley.internal.unsafe
import kotlin.random.Random

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.NANOSECONDS)
open class PrimitiveMaps {

    @Param("10", "100", "1000")
    private var size: Int = 0

    private lateinit var charMap: CharToIntMap
    private lateinit var hashMap: Map<Char, Int>
    private lateinit var values: CharArray

    @Setup
    fun setup() {
        charMap = CharToIntMap.empty()
        hashMap = mutableMapOf()
        values = sequence { yield(Random.nextInt(Char.MIN_VALUE.toInt(), Char.MAX_VALUE.toInt()).toChar()) }.toList().toCharArray()
        values.forEach { c ->
            charMap[c] = c.toInt()
            (hashMap as MutableMap<Char, Int>)[c] = c.toInt()
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
 */

