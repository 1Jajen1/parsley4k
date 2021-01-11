package parsley.string

import parsley.CompileSettings
import parsley.CompiledParser
import parsley.ParseResult
import parsley.Parser
import parsley.internal.backend.CodeGenContext
import parsley.internal.backend.CodeGenFunc
import parsley.internal.backend.Instruction
import parsley.internal.backend.Method
import parsley.internal.backend.Program
import parsley.internal.backend.instructions.Satisfy
import parsley.internal.backend.instructions.SatisfyMany
import parsley.internal.backend.instructions.SatisfyMap
import parsley.internal.backend.instructions.Satisfy_
import parsley.internal.backend.optimise.optimise
import parsley.internal.backend.string.CharListToString
import parsley.internal.backend.string.MatchCharIn
import parsley.internal.backend.string.MatchCharIn_
import parsley.internal.backend.string.MatchCharOf
import parsley.internal.backend.string.MatchCharOf_
import parsley.internal.backend.string.MatchManyChars
import parsley.internal.backend.string.MatchManyCharsIn
import parsley.internal.backend.string.MatchManyCharsIn_
import parsley.internal.backend.string.MatchManyCharsOf
import parsley.internal.backend.string.MatchManyCharsOf_
import parsley.internal.backend.string.MatchManyChars_
import parsley.internal.backend.string.MatchString
import parsley.internal.backend.string.MatchString_
import parsley.internal.backend.string.SatisfyChar
import parsley.internal.backend.string.SatisfyCharMap
import parsley.internal.backend.string.SatisfyChar_
import parsley.internal.backend.string.SatisfyManyChars
import parsley.internal.backend.string.SingleChar
import parsley.internal.backend.string.SingleChar_
import parsley.internal.backend.string.StringStackMachine
import parsley.internal.backend.string.StringToCharList
import parsley.internal.backend.toProgram
import parsley.internal.frontend.ParserF
import parsley.internal.frontend.findLetBound
import parsley.internal.frontend.insertLets
import parsley.internal.frontend.optimise
import parsley.internal.unsafe

class CompiledStringParser<E, A> internal constructor(override val machine: StringStackMachine<E>) :
    CompiledParser<Char, CharArray, E, A>() {
    override fun reset() {
        machine.input = charArrayOf()
        super.reset()
    }

    override fun getRemaining(): CharArray {
        return machine.input.sliceArray(machine.inputOffset until machine.input.size)
    }

    override fun setInput(arr: CharArray) {
        machine.input = arr
    }

    fun execute(str: String): ParseResult<Char, CharArray, E, A> = execute(str.toCharArray())
}

@OptIn(ExperimentalStdlibApi::class)
internal fun <E> ParserF<Char, E, Any?>.collectAlternatives(): Set<Char> {
    val s = mutableSetOf<Char>()
    var discard = false
    DeepRecursiveFunction<ParserF<Char, E, Any?>, Unit> { parser ->
        if (parser is ParserF.Alt) {
            callRecursive(parser.left)
            callRecursive(parser.right)
        } else if (parser is ParserF.Single<*>) {
            s.add(parser.i.unsafe())
        } else {
            discard = true
        }
    }(this)
    return if (discard) emptySet()
    else s
}

internal class StringCodeGen<E> : CodeGenFunc<Char, E> {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun <A> DeepRecursiveScope<ParserF<Char, E, Any?>, Unit>.apply(
        p: ParserF<Char, E, A>,
        subs: Map<Int, Parser<Char, E, A>>,
        ctx: CodeGenContext<Char, E>,
        settings: CompileSettings
    ): Boolean =
        when (p) {
            is ParserF.ConcatString -> {
                callRecursive(p.p)
                ctx += CharListToString()
                true
            }
            is ParserF.Many<Char, E, Any?> -> {
                val set = p.p.collectAlternatives()
                when {
                    set.isEmpty() -> false
                    set.size == 1 -> {
                        if (ctx.discard) {
                            ctx += MatchManyChars_(set.first())
                        } else {
                            ctx += MatchManyChars(set.first())
                            ctx += StringToCharList()
                        }
                        true
                    }
                    else -> {
                        val arr = set.toCharArray()
                        arr.sort()
                        if (arr.last() - arr.first() == arr.size) {
                            if (ctx.discard) {
                                ctx += MatchManyCharsIn_(arr.first()..arr.last())
                            } else {
                                ctx += MatchManyCharsIn(arr.first()..arr.last())
                                ctx += StringToCharList()
                            }
                        } else {
                            if (ctx.discard) {
                                ctx += MatchManyCharsOf_(arr)
                            } else {
                                ctx += MatchManyCharsOf(arr)
                                ctx += StringToCharList()
                            }
                        }
                        true
                    }
                }
            }
            is ParserF.Alt -> {
                val set = p.collectAlternatives()
                if (set.size > 1) {
                    val arr = set.toCharArray()
                    arr.sort()
                    if (arr.last() - arr.first() == arr.size) {
                        if (ctx.discard) {
                            ctx += MatchCharIn_(arr.first()..arr.last())
                        } else {
                            ctx += MatchCharIn(arr.first()..arr.last())
                        }
                    } else {
                        if (ctx.discard) {
                            ctx += MatchCharOf_(set.toCharArray())
                        } else {
                            ctx += MatchCharOf(set.toCharArray())
                        }
                    }
                    true
                } else false
            }
            is ParserF.Single<*> -> {
                if (ctx.discard) {
                    ctx += SingleChar_(p.i.unsafe())
                } else {
                    ctx += SingleChar(p.i.unsafe())
                }
                true
            }
            is ParserF.Satisfy<*> -> {
                if (ctx.discard) {
                    ctx += SatisfyChar_(p.match.unsafe(), p.expected.unsafe())
                } else {
                    ctx += SatisfyChar(p.match.unsafe(), p.expected.unsafe())
                }
                true
            }
            else -> false
        }
}

fun <E, A> Parser<Char, E, A>.compile(settings: CompileSettings = CompileSettings()): CompiledStringParser<E, A> {
    val (bound, recs) = findLetBound()
    val (mainP, subs, highestLabel) = insertLets(bound, recs)

    val (prog, label) = Triple(mainP.optimise(settings), subs.mapValues { (_, v) ->
        v.optimise(settings)
    }, highestLabel).toProgram(StringCodeGen(), settings = settings)

    return CompiledStringParser(
        StringStackMachine(
            prog.postProcess(settings)
                .optimise(label, settings)
                .toFinalProgram()
                .also { it.mapIndexed { i, v -> i to v }.also(::println) }
                .toTypedArray()
        )
    )
}

internal fun <E> Program<Char, E>.postProcess(settings: CompileSettings): Program<Char, E> {
    var (main, subs) = this

    main = Method(main.instr.toMutableList().performCharOptimizations())
    subs = subs.mapValues { (_, s) -> Method(s.instr.toMutableList().performCharOptimizations()) }

    return Program(main, subs)
}

internal fun <E> MutableList<Instruction<Char, E>>.performCharOptimizations(): MutableList<Instruction<Char, E>> {
    var curr = 0
    while (curr < size - 1) {
        val el = get(curr++)
        val next = get(curr)
        when {
            // TODO Replace every instruction that may have not already been replaced!
                // TODO double check if I even need to do manual codegen when I have this step
            el is Satisfy -> {
                removeAt(--curr)
                add(curr, SatisfyChar(el.f.unsafe(), el.expected))
            }
            el is Satisfy_ -> {
                removeAt(--curr)
                add(curr, SatisfyChar_(el.f.unsafe(), el.expected))
            }
            el is SatisfyMap -> {
                removeAt(--curr)
                add(curr, SatisfyCharMap(el.f.unsafe(), el.fa, el.expected))
            }
            el is SatisfyMany && next is CharListToString -> {
                removeAt(--curr)
                removeAt(curr)
                add(curr, SatisfyManyChars(el.f.unsafe()))
            }
            el is StringToCharList && next is CharListToString -> {
                removeAt(curr--)
                removeAt(curr--)
            }
            el is SingleChar<E> && next is SingleChar<E> -> {
                removeAt(curr--)
                removeAt(curr)
                add(curr, MatchString("${el.c}${next.c}"))
            }
            el is SingleChar_<E> && next is SingleChar_<E> -> {
                removeAt(curr--)
                removeAt(curr)
                add(curr, MatchString_("${el.c}${next.c}"))
            }
            el is MatchString<E> && next is SingleChar<E> -> {
                removeAt(curr--)
                removeAt(curr)
                add(curr, MatchString("${el.str}${next.c}"))
            }
            el is MatchString_<E> && next is SingleChar_<E> -> {
                removeAt(curr--)
                removeAt(curr)
                add(curr, MatchString_("${el.str}${next.c}"))
            }
        }
    }
    return this
}
