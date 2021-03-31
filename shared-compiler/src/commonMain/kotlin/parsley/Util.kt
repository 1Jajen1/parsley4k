package parsley

import parsley.backend.Instruction

expect inline fun <A> Any?.unsafe(): A

fun <I, E> Array<Instruction<I, E>>.copy(): Array<Instruction<I, E>> =
    copyOf().also { new ->
        new.forEachIndexed { i, instruction -> new[i] = instruction.copy() }
    }
