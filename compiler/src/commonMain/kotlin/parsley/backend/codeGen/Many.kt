package parsley.backend.codeGen

import parsley.AnyParser
import parsley.backend.CodeGenContext
import parsley.backend.CodeGenStep
import parsley.backend.instructions.InputCheck
import parsley.backend.instructions.Label
import parsley.backend.instructions.Many_
import parsley.backend.instructions.MatchManyN_
import parsley.backend.instructions.Matcher
import parsley.backend.instructions.SatisfyMany
import parsley.backend.instructions.SatisfyMany_
import parsley.backend.instructions.SingleMany
import parsley.backend.instructions.SingleMany_
import parsley.frontend.Alt
import parsley.frontend.ApR
import parsley.frontend.Eof
import parsley.frontend.Let
import parsley.frontend.Many
import parsley.frontend.ParserF
import parsley.frontend.Pure
import parsley.frontend.Satisfy
import parsley.frontend.Single
import parsley.settings.SubParsers
import parsley.unsafe

fun <I, E> manyGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is Many<I, E, *>) {
        manySingleGen(p, ctx) ?: manyPathsGen(p, ctx) ?: manyDefaultGen(p, ctx)
        true
    } else false
}

fun <I, E> manySingleGen(p: Many<I, E, *>, ctx: CodeGenContext<I, E>): Unit? =
    when (val inner = p.inner) {
        is Single<*> -> {
            if (ctx.discard) ctx += SingleMany_(inner.i.unsafe())
            else ctx += SingleMany(inner.i.unsafe())
        }
        is Satisfy<*> -> {
            if (ctx.discard) ctx += SatisfyMany_(inner.match.unsafe())
            else ctx += SatisfyMany(inner.match.unsafe())
        }
        else -> null
    }

fun <I, E> manyPathsGen(p: Many<I, E, *>, ctx: CodeGenContext<I, E>): Unit? {
    if (ctx.discard) {
        /* TODO This is not worth it over MatchManyN_(paths) even with tail call elimination
        if (p.inner is Alt) {
            val alt = p.inner as Alt
            val path = mkPath(alt.first, ctx.subs)
            if (path != null && path.size == 1) {
                val lbl = ctx.mkLabel()
                val let = Let(true, lbl)
                val parser = ApR(Many(alt.first), Alt(ApR(alt.second, let), Pure(Unit)))
                ctx.subs[lbl] = parser
                callRecursive(let)
                return true
            }
        }
         */

        val paths = mkPaths(p.inner, ctx.getSubParsers())
        if (paths != null && paths.size > 1) {
            ctx += MatchManyN_(paths)
            return Unit
        }
    }
    return null
}

internal fun <I, E> mkPath(p: ParserF<I, E, Any?>, subs: SubParsers<I, E>): Array<Matcher<I>>? {
    val buf = mutableListOf<Matcher<I>>()
    var curr = p
    l@ while (true) {
        when (curr) {
            is Satisfy<*> -> {
                buf.add(Matcher.Sat(curr.match.unsafe(), curr.expected.unsafe()))
                break@l
            }
            is Single<*> -> {
                buf.add(Matcher.El(curr.i.unsafe(), curr.expected.unsafe()))
                break@l
            }
            is Pure -> break@l
            is ApR<I, E, *, Any?> -> {
                var l = curr.first
                val lbuf = mutableListOf<Matcher<I>>()
                i@ while (true) {
                    when (l) {
                        is Satisfy<*> -> {
                            lbuf.add(Matcher.Sat(l.match.unsafe(), l.expected.unsafe()))
                            break@i
                        }
                        is Single<*> -> {
                            lbuf.add(Matcher.El(l.i.unsafe(), l.expected.unsafe()))
                            break@i
                        }
                        Eof -> lbuf.add(Matcher.Eof())
                        is ApR<I, E, *, Any?> -> {
                            when (val el = l.second) {
                                is Satisfy<*> -> {
                                    lbuf.add(Matcher.Sat(el.match.unsafe(), el.expected.unsafe()))
                                }
                                is Single<*> -> {
                                    lbuf.add(Matcher.El(el.i.unsafe(), el.expected.unsafe()))
                                }
                                else -> return null
                            }
                            l = l.first
                        }
                        is parsley.frontend.Label -> l = l.inner
                        else -> return null
                    }
                }
                buf.addAll(lbuf.reversed())
                curr = curr.second
            }
            is parsley.frontend.Label -> curr = curr.inner
            is Let -> if (!curr.recursive) curr = subs[curr.sub] else return null
            else -> return null
        }
    }
    return buf.toTypedArray()
}

private fun <I, E> mkPaths(p: ParserF<I, E, Any?>, subs: SubParsers<I, E>): Array<Array<Matcher<I>>>? {
    val buf = mutableListOf<Array<Matcher<I>>>()
    var curr = p
    while (curr is Alt) {
        mkPath(curr.first, subs)?.let(buf::add) ?: return null
        curr = curr.second
    }
    mkPath(curr, subs)?.let(buf::add) ?: return null
    return buf.toTypedArray()
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun <I, E> DeepRecursiveScope<AnyParser<I, E>, Unit>.manyDefaultGen(p: Many<I, E, *>, ctx: CodeGenContext<I, E>): Unit {
    val handler = ctx.mkLabel()
    val jumpL = ctx.mkLabel()
    ctx += InputCheck(handler)
    ctx += Label(jumpL)
    callRecursive(p.inner)
    ctx += Label(handler)
    if (ctx.discard) {
        ctx += Many_(jumpL)
    } else {
        ctx += parsley.backend.instructions.Many(jumpL)
    }
}
