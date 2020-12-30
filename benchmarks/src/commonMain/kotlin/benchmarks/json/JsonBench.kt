package benchmarks.json

import benchmarks.json.old.compiledJsonParserOldMany
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
open class JsonBench {

    /*
    @Benchmark
    open fun jsonParsleyCold() {
        jsonRootParser.compile().execute(jsonSample1K)
    }

    @Benchmark
    open fun jsonParsleyHot() {
        compiledJsonParser.execute(jsonSample1K)
    }

    @Benchmark
    open fun jsonBetterParse() {
        OptimizedJsonGrammar().parseToEnd(jsonSample1K)
    }

    @Benchmark
    open fun jsonKotlinxDeserializer() {
        Json.parseToJsonElement(jsonSample1K)
    }
     */
    
    @Benchmark
    open fun jsonImprovedList() {
        compiledJsonParser.execute(jsonSample1K)
    }
}
