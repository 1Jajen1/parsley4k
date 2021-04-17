package parsley

import kotlin.jvm.JvmName

interface CompiledParser<IArr, I, E, A> {
    fun parse(input: IArr): ParseResult<IArr, I, E, A>

    fun parseStreaming(initial: IArr): ParseResult<IArr, I, E, A>

    fun parseTest(input: IArr, fromE: (E) -> String): Unit
}

// extensions on top of default api
fun <IArr, I, E, A> CompiledParser<IArr, I, E, A>.parseOrNull(inp: IArr): A? = when (val res = parse(inp)) {
    is ParseResult.Done -> res.result
    else -> null
}

@JvmName("parseTestWithError")
fun <IArr, I, E, A> CompiledParser<IArr, I, E, A>.parseTest(input: IArr): Unit =
    parseTest(input) { it.toString() }

@JvmName("parseTestWithoutError")
fun <IArr, I, A> CompiledParser<IArr, I, Nothing, A>.parseTest(input: IArr): Unit =
    parseTest(input) { throw IllegalArgumentException("CompiledParser.parseTest: Received a Nothing typed error, this should be possible only with unsafe typecasts, please avoid that.") }
