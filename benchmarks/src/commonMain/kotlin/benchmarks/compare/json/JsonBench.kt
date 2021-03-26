package benchmarks.compare.json

import benchmarks.parsers.jsonSample1K
import benchmarks.parsers.jsonSample1KArr
import benchmarks.parsers.jsonSample1KCharArr
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import kotlinx.serialization.json.Json
import parsley.compile

@State(Scope.Benchmark)
open class JsonBench {

    @Benchmark
    open fun jsonParsleyGeneric() {
        benchmarks.parsers.generic.compiledJsonParser.parse(jsonSample1KArr)
    }

    @Benchmark
    open fun jsonParsleyString() {
        benchmarks.parsers.string.compiledJsonParser.parse(jsonSample1KCharArr)
    }

    @Benchmark
    open fun jsonParsleyStringTrie() {
        benchmarks.parsers.string.compiledJsonParserTrie.parse(jsonSample1KCharArr)
    }

    @Benchmark
    open fun jsonParsleyGenericCold() {
        benchmarks.parsers.generic.jsonRootParser.compile<Char, Nothing, benchmarks.parsers.Json>().parse(jsonSample1KArr)
    }

    @Benchmark
    open fun jsonParsleyStringCold() {
        benchmarks.parsers.string.jsonRootParser.compile().parse(jsonSample1KCharArr)
    }

    @Benchmark
    open fun jsonParsleyStringTrieCold() {
        benchmarks.parsers.string.jsonRootParserTrie.compile().parse(jsonSample1KCharArr)
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
