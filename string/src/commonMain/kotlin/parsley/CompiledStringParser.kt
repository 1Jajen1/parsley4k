package parsley

import parsley.backend.AbstractStackMachine
import parsley.backend.CharJumpTable
import parsley.backend.CodeGenContext
import parsley.backend.CodeGenStep
import parsley.backend.Instruction
import parsley.backend.MatchString_
import parsley.backend.ParseStatus
import parsley.backend.SatisfyChar
import parsley.backend.SatisfyCharMany
import parsley.backend.SatisfyCharMany_
import parsley.backend.SatisfyCharMap
import parsley.backend.SatisfyChar_
import parsley.backend.SatisfyChars_
import parsley.backend.SingleChar
import parsley.backend.SingleCharMany
import parsley.backend.SingleCharMany_
import parsley.backend.SingleCharMap
import parsley.backend.SingleChar_
import parsley.backend.StringToCharList
import parsley.backend.instructions.JumpTable
import parsley.backend.instructions.Satisfy
import parsley.backend.instructions.SatisfyMany
import parsley.backend.instructions.SatisfyMany_
import parsley.backend.instructions.SatisfyMap
import parsley.backend.instructions.SatisfyN_
import parsley.backend.instructions.Satisfy_
import parsley.backend.instructions.Single
import parsley.backend.instructions.SingleMany
import parsley.backend.instructions.SingleMany_
import parsley.backend.instructions.SingleMap
import parsley.backend.instructions.SingleN_
import parsley.backend.instructions.Single_
import parsley.collections.IntMap
import parsley.frontend.CharListToString
import parsley.frontend.InsertLetStep
import parsley.frontend.LetBoundStep
import parsley.frontend.OptimiseStep
import parsley.frontend.ParserF
import parsley.frontend.fallthrough
import parsley.frontend.small

// TODO Split steps into individual files, this is getting too large
@OptIn(ExperimentalStdlibApi::class)
fun <E, A> Parser<Char, E, A>.compile(): CompiledStringParser<E, A> {
    val settings = defaultSettings<Char, E>()
        //.copy(optimise = OptimiseSettings(analyseSatisfy = AnalyseSatisfy(generateSequence(Char.MIN_VALUE) { c -> if (c != Char.MAX_VALUE) c.inc() else null })))
        .addLetFindStep(LetBoundStep.fallthrough { p, seen ->
            if (p is CharListToString<E>) {
                callRecursive(seen to p.p)
                true
            } else false
        })
        .addLetInsertStep(
            InsertLetStep.fallthrough({ p ->
                when (p) {
                    is CharListToString -> CharListToString(callRecursive(p).unsafe())
                    else -> null
                }
            }, { p ->
                when (p) {
                    is CharListToString -> p.p.small()
                    else -> p.small()
                }
            })
        )
        .addOptimiseStep(
            object : OptimiseStep<Char, E> {
                override suspend fun DeepRecursiveScope<ParserF<Char, E, Any?>, ParserF<Char, E, Any?>>.step(
                    p: ParserF<Char, E, Any?>,
                    settings: CompilerSettings<Char, E>
                ): ParserF<Char, E, Any?> {
                    return when {
                        p is CharListToString -> CharListToString(callRecursive(p.p).unsafe())
                        p is parsley.frontend.Satisfy<*> && p.match is CharPredicate && settings.optimise.analyseSatisfy.f.any { true } -> {
                            val accepted = mutableSetOf<Char>()
                            val rejected = mutableSetOf<Char>()
                            for (c in settings.optimise.analyseSatisfy.f) {
                                if (p.match.unsafe<CharPredicate>().invokeP(c))
                                    accepted.add(c)
                                else rejected.add(c)
                            }
                            parsley.frontend.Satisfy(p.match.unsafe(), accepted, rejected)
                        }
                        else -> p
                    }
                }
            }
        )
        .addCodegenStep(
            object : CodeGenStep<Char, E> {
                override suspend fun DeepRecursiveScope<ParserF<Char, E, Any?>, Unit>.step(
                    p: ParserF<Char, E, Any?>,
                    ctx: CodeGenContext<Char, E>
                ): Boolean {
                    return when (p) {
                        is CharListToString -> {
                            callRecursive(p.p)
                            if (!ctx.discard) ctx += parsley.backend.CharListToString()
                            true
                        }
                        else -> false
                    }
                }
            }
        ).addOptimiseStep { s, l -> replaceInstructions(s, l) }
    return CompiledStringParser(
        parserF.compile(settings).toTypedArray()
            //.also { println(it.withIndex().map { (i, v) -> i to v }) }
    )
}

class CompiledStringParser<E, A>(val instr: Array<Instruction<Char, E>>) {
    fun parse(input: CharArray): A? {
        val machine = StringStackMachine(instr)
        machine.input = input
        machine.execute()
        return if (machine.status == ParseStatus.Ok) machine.pop().unsafe()
        else null
    }
}

internal class StringStackMachine<E> internal constructor(instr: Array<Instruction<Char, E>>) :
    AbstractStackMachine<Char, E>(instr) {

    internal var input: CharArray = charArrayOf()

    override fun hasMore(): Boolean = inputOffset < input.size
    override fun take(): Char = input[inputOffset]
    override fun needInput() = fail()
    override fun hasMore(n: Int): Boolean = inputOffset < input.size - (n - 1)

    fun takeP(): Char = input[inputOffset]
    fun takeP(start: Int, end: Int): CharArray = input.slice(start, end)
}

internal expect inline fun CharArray.slice(start: Int, end: Int): CharArray

internal fun <E> Method<Char, E>.replaceInstructions(sub: IntMap<Method<Char, E>>, l: Int): Int {
    replaceMethod()
    sub.forEach { _, v -> v.replaceMethod() }
    return l
}

fun <E> Method<Char, E>.replaceMethod() {
    var curr = 0
    while (curr < size) {
        val el = get(curr)
        when {
            el is JumpTable -> {
                removeAt(curr)
                val map = IntMap.empty<Int>()
                el.table.forEach { (k, v) -> map[k.toInt()] = v }
                add(curr, CharJumpTable(map))
            }
            // TODO Any or all here?
            el is SatisfyN_ && el.fArr.all { it is CharPredicate } -> {
                removeAt(curr)
                add(curr, SatisfyChars_(el.fArr.unsafe()))
            }
            el is Satisfy && el.f is CharPredicate -> {
                removeAt(curr)
                add(curr, SatisfyChar(el.f.unsafe()))
            }
            el is SatisfyMap && el.f is CharPredicate && el.g is CharFunc<Any?> -> {
                removeAt(curr)
                add(curr, SatisfyCharMap(el.f.unsafe(), el.g.unsafe()))
            }
            el is Satisfy_ && el.f is CharPredicate -> {
                removeAt(curr)
                add(curr, SatisfyChar_(el.f.unsafe()))
            }
            el is SatisfyMany && el.f is CharPredicate -> {
                removeAt(curr)
                add(curr, SatisfyCharMany(el.f.unsafe()))
                if (curr + 1 < size) {
                    if (get(curr + 1) !is parsley.backend.CharListToString) {
                        add(curr + 1, StringToCharList())
                    } else {
                        removeAt(curr + 1)
                    }
                }
            }
            el is SatisfyMany_ && el.f is CharPredicate -> {
                removeAt(curr)
                add(curr, SatisfyCharMany_(el.f.unsafe()))
            }
            el is Single -> {
                removeAt(curr)
                add(curr, SingleChar(el.i))
            }
            el is SingleMap -> {
                removeAt(curr)
                add(curr, SingleCharMap(el.el, el.res))
            }
            el is Single_ -> {
                removeAt(curr)
                add(curr, SingleChar_(el.i))
            }
            el is SingleN_ -> {
                removeAt(curr)
                // This is sadly necessary because otherwise the cast from [Object to [Character fails...
                val arr = el.fArr.toList().toTypedArray().toCharArray()
                add(curr, MatchString_(arr))
            }
            el is SingleMany -> {
                removeAt(curr)
                add(curr, SingleCharMany(el.i))
                if (curr + 1 < size) {
                    if (get(curr + 1) !is parsley.backend.CharListToString) {
                        add(curr + 1, StringToCharList())
                    } else {
                        removeAt(curr + 1)
                    }
                }
            }
            el is SingleMany_ -> {
                removeAt(curr)
                add(curr, SingleCharMany_(el.i))
            }
        }
        curr++
    }
}
