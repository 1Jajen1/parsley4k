package parsley

import parsley.internal.backend.ParseStatus
import parsley.internal.backend.StackMachine

// TODO add suspend based streaming version
abstract class CompiledParser<I, IArr, E, A> internal constructor() {
    internal abstract val machine: StackMachine<I, E>
    internal abstract fun setInput(arr: IArr): Unit
    internal abstract fun getRemaining(): IArr

    internal open fun reset(): Unit {
        machine.inputOffset = 0
        machine.dataStack.clear()
        machine.handlerStack.clear()
        machine.returnStack.clear()
        machine.programCounter = 0
        machine.status = ParseStatus.Ok
    }

    fun execute(arr: IArr): ParseResult<I, IArr, E, A> {
        reset()
        setInput(arr)

        while (true) {
            machine.execute()
            when (val stat = machine.status) {
                is ParseStatus.Ok -> return ParseResult.Success(machine.dataStack.pop() as A, getRemaining())
                is ParseStatus.NeedInput -> machine.failWith(
                    ParseError.Trivial(
                        unexpected = ErrorItem.EndOfInput,
                        expected = stat.expected,
                        offset = machine.inputOffset
                    )
                )
                is ParseStatus.Failed -> return ParseResult.Error(stat.error, getRemaining())
            }
        }
    }
}

sealed class ParseResult<out I, IArr, out E, out A> {
    data class Success<IArr, out A>(val result: A, val remaining: IArr) : ParseResult<Nothing, IArr, Nothing, A>()
    data class Error<out I, IArr, out E>(val error: ParseError<I, E>, val remaining: IArr) :
        ParseResult<I, IArr, E, Nothing>()

    inline fun <C> fold(onError: (ParseError<I, E>, IArr) -> C, onSuccess: (A, IArr) -> C): C = when (this) {
        is Success -> onSuccess(result, remaining)
        is Error -> onError(error, remaining)
    }
}
