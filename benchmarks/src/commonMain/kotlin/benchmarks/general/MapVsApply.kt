package benchmarks.general

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import parsley.CompiledGenericParser
import parsley.CompiledStringParser
import parsley.backend.instructions.Apply
import parsley.backend.instructions.Map
import parsley.backend.instructions.Push
import parsley.unsafe

/**
 * Quick test if Map is faster than Apply
 *
 * Unsurprisingly it is, it is one less instruction and it only performs two stack operations compared to four.
 *
 * Map = Peek, Exchange, Push & Apply = Push, Pop, Peek, Exchange
 *
 * The difference is pretty tiny, but it does matter if its called enough.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
open class MapVsApply {

    lateinit var mapG: CompiledGenericParser<Char, Nothing, Int>
    lateinit var applyG: CompiledGenericParser<Char, Nothing, Int>

    @Setup
    fun setup() {
        mapG = CompiledGenericParser(arrayOf(Push(1), *Array(1000) { Map { it.unsafe<Int>() + 1 } }))
        applyG = CompiledGenericParser(
            arrayOf(*Array(1000) { Push { i: Int -> i + 1 } },
                Push(1),
                *Array(1000) { Apply() })
        )
    }

    @Benchmark
    fun benchmarkMapG() {
        mapG.parse(emptyArray())
    }

    @Benchmark
    fun benchmarkApplyG() {
        applyG.parse(emptyArray())
    }
}