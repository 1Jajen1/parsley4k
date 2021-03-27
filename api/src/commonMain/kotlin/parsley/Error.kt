package parsley

import parsley.frontend.Catch
import parsley.frontend.Fail
import parsley.frontend.FailTop
import parsley.frontend.Hide
import parsley.frontend.Label

// labels
fun <I, E, A> Parser<I, E, A>.label(str: String): Parser<I, E, A> = Parser(Label(str, parserF))

fun <I, E, A> Parser<I, E, A>.hide(): Parser<I, E, A> = Parser(Hide(parserF))

// error builders
private fun <I, E> Parser.Companion.fail(error: ParseError<I, E>): Parser<I, E, Nothing> =
    Parser(Fail(error))

fun <I> Parser.Companion.fail(unexpected: ErrorItem<I>? = null, expected: Set<ErrorItem<I>> = emptySet()): Parser<I, Nothing, Nothing> =
    fail(ParseError(-1, unexpected, expected, emptySet()))

fun <E> Parser.Companion.fail(errors: Set<FancyError<E>>): Parser<Nothing, E, Nothing> =
    fail(ParseError(-1, null, emptySet(), errors))

fun <I> Parser.Companion.unexpected(item: ErrorItem<I>): Parser<I, Nothing, Nothing> = fail(item)

fun <E> Parser.Companion.fail(err: E): Parser<Nothing, E, Nothing> = fail(setOf(FancyError.Custom(err)))

fun Parser.Companion.fail(reason: String): Parser<Nothing, Nothing, Nothing> = fail(setOf(FancyError.Message(reason)))

// Recovery
fun <I, E, A> Parser<I, E, A>.catch(): Parser<I, E, Either<ParseError<I, E>, A>> = Parser(Catch(parserF))

fun <I, E, A> Parser<I, E, A>.recover(): Parser<I, E, Unit> = catch().void()

fun <I, E, A> Parser<I, E, A>.handle(f: (ParseError<I, E>) -> A): Parser<I, E, A> =
    catch().map { it.fold(f) { it } }

fun <I, E, A> Parser<I, E, A>.handleEither(f: (ParseError<I, E>) -> Either<ParseError<I, E>, A>): Parser<I, E, A> =
    catch().map { it.fold(f) { Either.right(it) } }.select(Parser(FailTop))

fun <I, E, A> Parser<I, E, A>.region(f: (ParseError<I, E>) -> ParseError<I, E>): Parser<I, E, A> =
    handleEither { Either.left(f(it)) }
