package parsley

import parsley.backend.CodeGenContext
import parsley.backend.CodeGenStep
import parsley.backend.instructions.CharEof
import parsley.backend.instructions.CharJumpTable
import parsley.backend.instructions.Eof
import parsley.backend.instructions.JumpTable
import parsley.backend.instructions.MatchManyCharN_
import parsley.backend.instructions.MatchManyChar_
import parsley.backend.instructions.MatchManyN_
import parsley.backend.instructions.MatchMany_
import parsley.backend.instructions.MatchString_
import parsley.backend.instructions.MkPair
import parsley.backend.instructions.PushChunkOf
import parsley.backend.instructions.PushStringOf
import parsley.backend.instructions.Satisfy
import parsley.backend.instructions.SatisfyChar
import parsley.backend.instructions.SatisfyCharMany
import parsley.backend.instructions.SatisfyCharMany_
import parsley.backend.instructions.SatisfyCharMap
import parsley.backend.instructions.SatisfyChar_
import parsley.backend.instructions.SatisfyChars_
import parsley.backend.instructions.SatisfyMany
import parsley.backend.instructions.SatisfyMany_
import parsley.backend.instructions.SatisfyMap
import parsley.backend.instructions.SatisfyN_
import parsley.backend.instructions.Satisfy_
import parsley.backend.instructions.Single
import parsley.backend.instructions.SingleChar
import parsley.backend.instructions.SingleCharMany
import parsley.backend.instructions.SingleCharMany_
import parsley.backend.instructions.SingleChar_
import parsley.backend.instructions.SingleMany
import parsley.backend.instructions.SingleMany_
import parsley.backend.instructions.SingleN_
import parsley.backend.instructions.Single_
import parsley.backend.instructions.StringToCharList
import parsley.backend.instructions.convert
import parsley.collections.IntMap
import parsley.frontend.CharListToString
import parsley.frontend.OptimiseStep
import parsley.frontend.ParserF
import kotlin.jvm.JvmName

@JvmName("compileString")
@OptIn(ExperimentalStdlibApi::class)
fun <E, A> Parser<Char, E, A>.compile(): CompiledStringParser<E, A> {
    val settings = defaultSettings<Char, E>()
        //.copy(optimise = OptimiseSettings(analyseSatisfy = AnalyseSatisfy(generateSequence(Char.MIN_VALUE) { c -> if (c != Char.MAX_VALUE) c.inc() else null })))
        .addOptimiseStep(
            object : OptimiseStep<Char, E> {
                override suspend fun DeepRecursiveScope<ParserF<Char, E, Any?>, ParserF<Char, E, Any?>>.step(
                    p: ParserF<Char, E, Any?>,
                    subs: IntMap<ParserF<Char, E, Any?>>,
                    settings: CompilerSettings<Char, E>
                ): ParserF<Char, E, Any?> {
                    return when {
                        p is parsley.frontend.Satisfy<*> && p.match is CharPredicate && settings.optimise.analyseSatisfy.f.any { true } -> {
                            val accepted = mutableSetOf<Char>()
                            val rejected = mutableSetOf<Char>()
                            for (c in settings.optimise.analyseSatisfy.f) {
                                if (p.match.unsafe<CharPredicate>().invokeP(c))
                                    accepted.add(c)
                                else rejected.add(c)
                            }
                            parsley.frontend.Satisfy(p.match.unsafe(), p.expected.unsafe(), accepted, rejected)
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
                            callRecursive(p.inner)
                            if (!ctx.discard) ctx += parsley.backend.instructions.CharListToString()
                            true
                        }
                        else -> false
                    }
                }
            }
        ).addOptimiseStep { s, l -> replaceInstructions(s, l) }
    return CompiledStringParser(
        parserF.compile(settings).toTypedArray()
    )
}


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
                val map = parsley.collections.IntMap.empty<Int>()
                el.table.forEach { (k, v) -> map[k.toInt()] = v }
                val table = CharJumpTable<E>(map)
                table.error.expected = el.error.expected
                add(curr, table)
            }
            el is SatisfyN_ && el.fArr.all { it is CharPredicate } -> {
                removeAt(curr)
                val new = SatisfyChars_<E>(el.fArr.unsafe(), el.eArr)
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is Satisfy && el.f is CharPredicate -> {
                removeAt(curr)
                val new = SatisfyChar<E>(el.f.unsafe())
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is SatisfyMap && el.f is CharPredicate && el.g is CharFunc<Any?> -> {
                removeAt(curr)
                val new = SatisfyCharMap<E>(el.f.unsafe(), el.g.unsafe())
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is Satisfy_ && el.f is CharPredicate -> {
                removeAt(curr)
                val new = SatisfyChar_<E>(el.f.unsafe())
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is SatisfyMany && el.f is CharPredicate -> {
                removeAt(curr)
                add(curr, SatisfyCharMany(el.f.unsafe()))
                if (curr + 1 < size) {
                    if (get(curr + 1) is parsley.backend.instructions.CharListToString) {
                        removeAt(curr + 1)
                    } else {
                        add(curr + 1, StringToCharList())
                    }
                }
            }
            el is SatisfyMany_ && el.f is CharPredicate -> {
                removeAt(curr)
                add(curr, SatisfyCharMany_(el.f.unsafe()))
            }
            el is Single -> {
                removeAt(curr)
                val new = SingleChar<E>(el.i)
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is Single_ -> {
                removeAt(curr)
                val new = SingleChar_<E>(el.i)
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is SingleN_ -> {
                removeAt(curr)
                val arr = el.fArr.toList().toCharArray()
                val new = MatchString_<E>(arr, el.eArr)
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is SingleMany -> {
                removeAt(curr)
                add(curr, SingleCharMany(el.i))
                if (curr + 1 < size) {
                    if (get(curr + 1) is parsley.backend.instructions.CharListToString) {
                        removeAt(curr + 1)
                    } else {
                        add(curr + 1, StringToCharList())
                    }
                }
            }
            el is SingleMany_ -> {
                removeAt(curr)
                add(curr, SingleCharMany_(el.i))
            }
            el is PushChunkOf -> {
                removeAt(curr)
                add(curr, PushStringOf())

                val next = get(curr + 1)
                when (next) {
                    is MkPair -> {
                        if (curr < size - 1 && get(curr + 2) is parsley.backend.instructions.CharListToString) {
                            removeAt(curr + 2)
                        } else {
                            add(curr + 1, StringToCharList())
                        }
                    }
                    is parsley.backend.instructions.CharListToString -> {
                        removeAt(curr + 1)
                    }
                    else -> add(curr + 1, StringToCharList())
                }
            }
            el is MatchMany_ -> {
                removeAt(curr)
                add(curr, MatchManyChar_(el.path.convert()))
            }
            el is MatchManyN_ -> {
                removeAt(curr)
                add(curr, MatchManyCharN_(el.paths.convert()))
            }
            el is Eof -> {
                removeAt(curr)
                add(curr, CharEof())
            }
        }
        curr++
    }
}