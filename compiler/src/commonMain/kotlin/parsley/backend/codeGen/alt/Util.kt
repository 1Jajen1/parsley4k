package parsley.backend.codeGen.alt

import parsley.Either
import parsley.ErrorItem
import parsley.Predicate
import parsley.backend.codeGen.mkPath
import parsley.backend.instructions.Matcher
import parsley.frontend.Alt
import parsley.frontend.Ap
import parsley.frontend.Binary
import parsley.frontend.Let
import parsley.frontend.ParserF
import parsley.frontend.Pure
import parsley.frontend.Satisfy
import parsley.frontend.Single
import parsley.frontend.Unary
import parsley.settings.SubParsers
import parsley.unsafe

@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E> ParserF<I, E, Any?>.findLeading(subs: SubParsers<I, E>): Either<Predicate<I>, Pair<I, Set<ErrorItem<I>>>> =
    DeepRecursiveFunction<ParserF<I, E, Any?>, Either<Predicate<I>, Pair<I, Set<ErrorItem<I>>>>> { p ->
        when (p) {
            is Pure -> Either.Left(Predicate { false })
            is Satisfy<*> -> Either.left(p.match.unsafe())
            is Single<*> -> Either.right(p.i.unsafe<I>() to p.expected.unsafe())
            is Ap<I, E, *, Any?> -> {
                if (p.first is Pure) p.second.findLeading(subs)
                else p.first.findLeading(subs)
            }
            is Alt -> {
                val l = callRecursive(p.first)
                val r = callRecursive(p.second)
                when {
                    l is Either.Left && r is Either.Left -> {
                        Either.Left(Predicate {
                            l.a(it) || r.a(it)
                        })
                    }
                    l is Either.Right && r is Either.Right -> {
                        Either.Left(Predicate {
                            it == l.b.first || it == r.b.first
                        })
                    }
                    l is Either.Left && r is Either.Right -> {
                        Either.Left(Predicate {
                            l.a(it) || it == r.b.first
                        })
                    }
                    l is Either.Right && r is Either.Left -> {
                        Either.Left(Predicate {
                            it == l.b.first || r.a(it)
                        })
                    }
                    else -> TODO("Impossible")
                }
            }
            is Unary<I, E, *, Any?> -> p.inner.findLeading(subs)
            is Binary<I, E, *, *, Any?> -> p.first.findLeading(subs)
            is Let -> subs[p.sub].findLeading(subs)
            else -> Either.left(Predicate { true })
        }
    }.invoke(this)

fun <I, E> getExpected(p: ParserF<I, E, Any?>, subs: SubParsers<I, E>): Set<ErrorItem<I>>? {
    val expected = mkPath(p, subs)?.map {
        when (it) {
            is Matcher.Eof -> setOf(ErrorItem.EndOfInput)
            is Matcher.El -> it.expected
            is Matcher.Sat -> it.expected
        }
    } ?: emptyList()
    if (expected.isNotEmpty()) {
        if (expected.all { it.size == 1 && it.first() is ErrorItem.Tokens<*> }) {
            val buf = mutableListOf<I>()
            expected.forEach {
                when (val i = it.first()) {
                    is ErrorItem.Tokens -> if (i.tail.isEmpty()) buf.add(i.head) else return expected.first()
                    else -> return expected.first()
                }
            }
            return setOf(ErrorItem.Tokens(buf.first(), buf.drop(1)))
        } else {
            return expected.first()
        }
    } else return null
}

