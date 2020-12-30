package benchmarks

import benchmarks.json.jsonSample1K
import helios.core.Json
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

/*
@State(Scope.Benchmark)
open class JsonBenchHelios {

    @Benchmark
    open fun jsonHelios() {
        Json.parseUnsafe(jsonSample1K)
    }
}
 */