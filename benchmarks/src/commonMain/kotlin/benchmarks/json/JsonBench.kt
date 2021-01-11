package benchmarks.json

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.parser.parse
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import kotlinx.serialization.json.Json
import parsley.string.compile

@State(Scope.Benchmark)
open class JsonBench {

    @Benchmark
    open fun jsonParsleyCold() {
        jsonRootParser.compile().execute(jsonSample1K)
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

    @Benchmark
    open fun jsonParsley() {
        compiledJsonParser.execute(jsonSample1K)
    }
}
