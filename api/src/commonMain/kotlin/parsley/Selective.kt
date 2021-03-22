package parsley

import parsley.frontend.Select

fun <I, E, A, B> Parser<I, E, Either<A, B>>.select(onLeft: Parser<I, E, (A) -> B>): Parser<I, E, B> =
    Parser(Select(parserF, onLeft.parserF))

fun <I, E, A, B, C> Parser<I, E, Either<A, B>>.branch(
    onLeft: Parser<I, E, (A) -> C>,
    onRight: Parser<I, E, (B) -> C>
): Parser<I, E, C> =
    map { it.map { Either.left(it) } }.select(onLeft.map { f -> { a -> Either.right(f(a)) } }).select(onRight)

private fun <I, E> Parser<I, E, Boolean>.toEither(): Parser<I, E, Either<Unit, Unit>> =
    map { if (it) Either.left(Unit) else Either.right(Unit) }

fun <I, E, A> Parser<I, E, Boolean>.ifElse(onTrue: Parser<I, E, A>, onFalse: Parser<I, E, A>): Parser<I, E, A> =
    toEither().branch(
        onTrue.map { a -> { a } },
        onFalse.map { a -> { a } }
    )

fun <I, E> Parser<I, E, Boolean>.onTrue(p: Parser<I, E, Unit>): Parser<I, E, Unit> =
    toEither().select(p.followedBy(Parser.pure {}))

fun <I, E> Parser<I, E, Boolean>.onFalse(p: Parser<I, E, Unit>): Parser<I, E, Unit> =
    map { it.not() }.onTrue(p)

inline fun <I, E, A> Parser<I, E, A>.filter(crossinline f: (A) -> Boolean): Parser<I, E, A> =
    filterMap { a -> if (f(a)) a else null }

inline fun <I, E, A, B : Any> Parser<I, E, A>.filterMap(crossinline f: (A) -> B?): Parser<I, E, B> =
    map { a -> f(a)?.let { Either.right(it) } ?: Either.left(Unit) }.select(Parser.empty())
