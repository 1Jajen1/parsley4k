package parsley.internal.backend.instructions

import parsley.internal.backend.Instruction
import parsley.internal.backend.Jumps
import parsley.internal.backend.Pops
import parsley.internal.backend.Pushes
import parsley.internal.backend.StackMachine

// Helpers to implement intrinsic methods
internal class Many<I, E>(override var to: Int) : Instruction<I, E>, Jumps, Pops, Pushes {
    override fun apply(machine: StackMachine<I, E>) {
        val el = machine.dataStack.pop()
        machine.dataStack.pop()
        val xs = machine.dataStack.pop() as IList<Any?>
        machine.dataStack.push(IList.Cons(el, xs))
        machine.dataStack.push(machine.inputOffset)
        machine.jump(to)
    }

    override fun toString(): String = "Many($to)"
    override fun pops(): Int = 3
    override fun pushes(): Int = 2
}

internal sealed class IList<out A> {
    object Nil : IList<Nothing>()
    class Cons<out A>(val a: A, val tail: IList<A>): IList<A>()

    fun toList(): List<A> {
        val mutList = mutableListOf<A>()
        var curr = this
        while (curr !== Nil) {
            val cons = curr as Cons
            mutList.add(cons.a)
            curr = cons.tail
        }
        return mutList
    }
}
