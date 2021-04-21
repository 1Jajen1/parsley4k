package parsley.frontend.simplifier

import parsley.Either
import parsley.frontend.Alt
import parsley.frontend.Ap
import parsley.frontend.ApL
import parsley.frontend.ApR
import parsley.frontend.Attempt
import parsley.frontend.Catch
import parsley.frontend.ChunkOf
import parsley.frontend.Empty
import parsley.frontend.Fail
import parsley.frontend.Let
import parsley.frontend.LookAhead
import parsley.frontend.Many
import parsley.frontend.MatchOf
import parsley.frontend.NegLookAhead
import parsley.frontend.ParserF
import parsley.frontend.Pure
import parsley.frontend.Satisfy
import parsley.frontend.Select
import parsley.frontend.Single
import parsley.frontend.TransformStep
import parsley.unsafe

fun <I, E> constantFold(): TransformStep<I, E> = TransformStep { p, _, _ ->
    when (p) {
        is Ap<I, E, *, Any?> -> {
            val fst = p.first
            val snd = p.second
            when {
                // Pure f <*> Pure a == Pure (f(a))
                fst is Pure && snd is Pure -> Pure(fst.a.unsafe<(Any?) -> Any?>().invoke(snd.a))
                // Empty <*> _ == Empty
                fst is Empty -> Empty
                // _ <*> Empty == Empty
                snd is Empty -> Empty
                // Fail <*> _ == Fail
                fst is Fail -> fst
                // p <*> fails(q) == p *> q
                fails(snd) -> ApR(fst, snd)
                else -> p
            }
        }
        is ApL<I, E, Any?, *> -> {
            val fst = p.first
            val snd = p.second
            when {
                // p <* Pure _ == p
                snd is Pure -> fst
                // p <* (Pure _ <* q) == p <* q
                snd is ApL<I, E, Any?, *> && snd.first is Pure -> ApL(fst, snd.second)
                // p <* (p *> Pure _) == p <* q
                snd is ApR<I, E, *, Any?> && snd.second is Pure -> ApL(fst, snd.first)
                // Empty <* _ == Empty
                fst is Empty -> Empty
                // _ <* Empty == Empty
                snd is Empty -> Empty
                // Fail <* _ == Fail
                fst is Fail -> fst
                else -> p
            }
        }
        is ApR<I, E, *, Any?> -> {
            val fst = p.first
            val snd = p.second
            when {
                // Pure _ *> p == p
                fst is Pure -> snd
                // (q *> Pure _) *> q == p *> q
                fst is ApR<I, E, *, Any?> && fst.second is Pure -> ApR(fst.first, snd)
                // (Pure _ <* p) *> q == p *> q
                fst is ApL<I, E, Any?, *> && fst.first is Pure -> ApR(fst.second, snd)
                // Empty *> _ == Empty
                fst is Empty -> Empty
                // _ *> Empty == Empty
                snd is Empty -> Empty
                // Fail *> _ == Fail
                fst is Fail -> fst
                else -> p
            }
        }
        is Alt -> {
            val fst = p.first
            val snd = p.second
            when {
                // Pure x <|> _ = Pure x
                fst is Pure -> fst
                // Empty <|> p == p
                fst is Empty -> snd
                // p <|> Empty == p
                snd is Empty -> fst
                // !canFail(p) <|> _ == p
                !canFail(fst) -> fst
                else -> p
            }
        }
        is Select<I, E, *, Any?> -> {
            val fst = p.first
            val snd = p.second
            when {
                // Pure e <*? p ==
                //  e is Left  => Pure { f -> f(e.value) } <*> p
                //  e is Right => Pure(e.value)
                fst is Pure -> {
                    fst.a.fold({ l -> Ap(Pure { f: (Any?) -> Any? -> f(l) }, snd.unsafe()) }, { r -> Pure(r) })
                }
                // p <*? Pure f == Pure { e -> e.fold(f, id) } <*> p
                snd is Pure -> {
                    Ap(Pure { e: Either<Any?, Any?> -> e.fold(snd.a.unsafe(), { it }) }, fst)
                }
                // Empty <*? _ == Empty
                fst is Empty -> Empty
                // Fail <*? _ == Fail
                fst is Fail -> fst
                else -> p
            }
        }
        is LookAhead -> {
            when (val inner = p.inner) {
                // LookAhead (Pure x) == Pure x
                is Pure -> inner
                // LookAhead Empty == Empty
                is Empty -> Empty
                // LookAhead Fail
                is Fail -> inner
                else -> p
            }
        }
        is NegLookAhead -> {
            val inner = p.inner
            when {
                // NegLookAhead (Pure _) == Empty
                inner is Pure -> Empty
                // NegLookAhead Empty == Pure(Unit)
                inner is Empty -> Pure(Unit)
                // NegLookAhead Fail == Pure(Unit)
                inner is Fail -> Pure(Unit)
                // NegLookAhead (!canFail(p)) == Empty // TODO Retain errors somehow
                !canFail(inner) -> Empty
                // NegLookAhead (_ <|> !canFail(p)) == Empty // TODO Retain errors somehow
                inner is Alt && !canFail(inner.second) -> Empty
                else -> p
            }
        }
        is Attempt -> {
            val inner = p.inner
            when {
                // Attempt (Pure x) == Pure x
                inner is Pure -> inner
                // Attempt Empty == Empty
                inner is Empty -> Empty
                // Attempt Fail == Fail
                inner is Fail -> inner
                // Attempt (!fails(p)) == p
                !canFail(inner) -> inner
                else -> p
            }
        }
        is Many<I, E, *> -> {
            val inner = p.inner
            when {
                // Many Empty == Pure emptyList
                inner is Empty -> Pure(emptyList<Any?>())
                // Many Fail == Pure emptyList
                inner is Fail -> Pure(emptyList<Any?>())
                // Many (fails(inner)) == inner <|> Pure emptyList
                fails(inner) -> Alt(inner, Pure(emptyList<Any?>()))
                // Many (!canFail(p)) => diverges
                !canFail(inner) -> throw IllegalStateException("Many can never fail and thus never finishes")
                else -> p
            }
        }
        is ChunkOf -> {
            val inner = p.inner
            when {
                // ChunkOf (Pure _) == Pure emptyList
                inner is Pure -> Pure(emptyList<Any?>())
                // ChunkOf Empty == Empty
                inner is Empty -> Empty
                // ChunkOf Fail == Fail
                inner is Fail -> inner
                // ChunkOf (fails(inner)) == inner
                fails(inner) -> inner
                else -> p
            }
        }
        is MatchOf<I, E, *> -> {
            val inner = p.inner
            when {
                // MatchOf (Pure x) == Pure (emptyList() to x)
                inner is Pure -> Pure(emptyList<Any?>() to inner.a)
                // MatchOf Empty == Empty
                inner is Empty -> Empty
                // MatchOf Fail == Fail
                inner is Fail -> inner
                // MatchOf (fails(inner)) == inner
                fails(inner) -> inner
                else -> p
            }
        }
        is Catch<I, E, *> -> {
            val inner = p.inner
            when {
                // Catch (Pure x) == Pure (Right x)
                inner is Pure -> Pure(Either.Right(inner.a))
                // !canFail(p) == Pure { a -> Either.Right(a) } <*> p
                !canFail(inner) -> Ap(Pure { Either.Right(it) }, inner)
                else -> p
            }
        }
        else -> p
    }
}

// Check for assured failure
tailrec fun <I, E> fails(p: ParserF<I, E, Any?>): Boolean =
    when (p) {
        is Fail -> true
        is ApR<I, E, *, Any?> -> fails(p.second)
        is ApL<I, E, Any?, *> -> fails(p.second)
        is Ap<I, E, *, Any?> -> fails(p.second)
        is Alt -> fails(p.second)
        else -> false
    }

// Check for possible failure
@OptIn(ExperimentalStdlibApi::class)
fun <I, E> canFail(p: ParserF<I, E, Any?>): Boolean = DeepRecursiveFunction<ParserF<I, E, Any?>, Boolean> { p ->
    when (p) {
        is Fail -> true
        // TODO This is pessimistic. Include rejects analysis here
        is Satisfy<*> -> true
        is Single<*> -> true
        is ApR<I, E, *, Any?> -> callRecursive(p.first) || callRecursive(p.second)
        is ApL<I, E, Any?, *> -> callRecursive(p.first) || callRecursive(p.second)
        is Ap<I, E, *, Any?> -> callRecursive(p.first) || callRecursive(p.second)
        is Alt ->
            // TODO
            if (consumes(p.first) == Consumes(1)) callRecursive(p.second)
            else callRecursive(p.first) || callRecursive(p.second)
        is Many<I, E, *> -> consumes(p.inner) != Consumes(1)
        is Catch<I, E, *> -> false
        is Let -> true // TODO
        else -> true
    }
}.invoke(p)

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> consumes(p: ParserF<I, E, Any?>): Consumption = DeepRecursiveFunction<ParserF<I, E, Any?>, Consumption> { p ->
    when (p) {
        is Single<*> -> Consumes(1)
        is Satisfy<*> -> Consumes(1)
        is Ap<I, E, *, Any?> -> callRecursive(p.first) + callRecursive(p.second)
        is ApR<I, E, *, Any?> -> callRecursive(p.first) + callRecursive(p.second)
        is ApL<I, E, Any?, *> -> callRecursive(p.first) + callRecursive(p.second)
        is Alt -> callRecursive(p.first).max(callRecursive(p.second))
        is LookAhead -> Consumes(0)
        is NegLookAhead -> Consumes(0)
        is Attempt -> callRecursive(p.inner)
        is ChunkOf -> callRecursive(p.inner)
        is MatchOf<I, E, *> -> callRecursive(p.inner)
        is Catch<I, E, *> -> callRecursive(p.inner)
        is Many<I, E, *> -> Unknown
        else -> Unknown
    }
}.invoke(p)

sealed class Consumption
data class Consumes(val n: Int): Consumption()
object Unknown: Consumption()

operator fun Consumption.plus(other: Consumption): Consumption = when {
    this is Unknown -> Unknown
    other is Unknown -> Unknown
    this is Consumes && other is Consumes -> Consumes(n + other.n)
    else -> TODO("Not possible")
}

fun Consumption.max(other: Consumption): Consumption = when {
    this is Unknown -> Unknown
    other is Unknown -> Unknown
    this is Consumes && other is Consumes -> Consumes(n.coerceAtMost(other.n))
    else -> TODO("Not possible")
}
