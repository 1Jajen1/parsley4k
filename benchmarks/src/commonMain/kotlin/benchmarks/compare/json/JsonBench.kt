package benchmarks.compare.json

import benchmarks.parsers.byte.byteParse
import benchmarks.parsers.byte.byteTrieParse
import benchmarks.parsers.char.charParse
import benchmarks.parsers.char.charTrieParse
import benchmarks.parsers.generic.genericParse
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import kotlinx.serialization.json.Json

@State(Scope.Benchmark)
open class JsonBench {

    @Benchmark
    open fun jsonParsleyGeneric() {
        genericParse(jsonSample1KArr)
    }

    @Benchmark
    open fun jsonParsleyString() {
        charParse(jsonSample1KCharArr)
    }

    @Benchmark
    open fun jsonParsleyStringTrie() {
        charTrieParse(jsonSample1KCharArr)
    }

    @Benchmark
    open fun jsonParsleyByte() {
        byteParse(jsonSample1KByteArr)
    }

    @Benchmark
    open fun jsonParsleyByteTrie() {
        byteTrieParse(jsonSample1KByteArr)
    }

    @Benchmark
    open fun jsonBetterParseNaive() {
        NaiveJsonGrammar().parseToEnd(jsonSample1K)
    }

    @Benchmark
    open fun jsonBetterParse() {
        OptimizedJsonGrammar().parseToEnd(jsonSample1K)
    }

    @Benchmark
    open fun jsonKotlinxDeserializer() {
        Json.parseToJsonElement(jsonSample1K)
    }
}
