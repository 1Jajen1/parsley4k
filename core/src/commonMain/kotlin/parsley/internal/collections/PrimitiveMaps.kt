package parsley.internal.collections

internal class CharToIntMap private constructor(private val branches: IntToIntMapBranches.Branch) {
    operator fun get(c: Char): Int? = internalGet(branches, 0, c.toInt())
    operator fun set(c: Char, i: Int): Unit = internalPut(branches, c.toInt(), i)

    inline fun forEach(f: (Char, Int) -> Unit): Unit {
        val toRun = mutableListOf(this.branches)
        while (toRun.isNotEmpty()) {
            toRun.removeFirst().let { b ->
                b.table.forEach { l ->
                    when (l) {
                        null -> Unit
                        is IntToIntMapBranches.Leaf -> f(l.key.toChar(), l.value)
                        is IntToIntMapBranches.Branch -> {
                            toRun.add(l)
                        }
                    }
                }
            }
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("CharToIntMap({")
        forEach { c, i -> sb.append("($c, $i),") }
        sb.append("})")
        return sb.toString()
    }

    companion object {
        fun empty(): CharToIntMap = CharToIntMap(IntToIntMapBranches.Branch(arrayOfNulls(ARR_SIZE)))
    }
}

internal class IntToIntMap private constructor(private val branches: IntToIntMapBranches.Branch) {
    operator fun get(k: Int): Int? = internalGet(branches, 0, k)
    operator fun set(k: Int, i: Int): Unit = internalPut(branches, k, i)

    companion object {
        fun empty(): IntToIntMap = IntToIntMap(IntToIntMapBranches.Branch(arrayOfNulls(ARR_SIZE)))
    }
}

private sealed class IntToIntMapBranches {
    class Branch(val table: Array<IntToIntMapBranches?>) : IntToIntMapBranches()
    class Leaf(val key: Int, val value: Int) : IntToIntMapBranches()
}

private tailrec fun internalGet(m: IntToIntMapBranches?, depth: Int, c: Int): Int? = when (m) {
    null -> null
    is IntToIntMapBranches.Leaf -> if (m.key == c) m.value else null
    is IntToIntMapBranches.Branch -> internalGet(
        m.table[c.indexAtDepth(depth)],
        depth.nextDepth(),
        c
    )
}

private fun internalPut(m: IntToIntMapBranches.Branch, c: Int, i: Int) {
    var curr = m
    var depth = 0
    while (true) {
        val ind = c.indexAtDepth(depth)
        when (val branch = curr.table[ind]) {
            null -> {
                curr.table[ind] = IntToIntMapBranches.Leaf(c, i)
                return
            }
            is IntToIntMapBranches.Leaf -> {
                if (branch.key != c) {
                    val new = pair(depth.nextDepth(), c, IntToIntMapBranches.Leaf(c, i), branch.key, branch)
                    curr.table[ind] = new
                } else {
                    curr.table[ind] = IntToIntMapBranches.Leaf(c, i)
                }
                return
            }
            is IntToIntMapBranches.Branch -> {
                curr = branch
                depth = depth.nextDepth()
            }
        }
    }
}

private fun pair(
    depth: Int,
    hash1: Int,
    branch1: IntToIntMapBranches,
    hash2: Int,
    branch2: IntToIntMapBranches
): IntToIntMapBranches.Branch {
    val branchInd1 = hash1.indexAtDepth(depth)
    val branchInd2 = hash2.indexAtDepth(depth)
    val branches = arrayOfNulls<IntToIntMapBranches>(ARR_SIZE)
    if (branchInd1 == branchInd2) {
        val deeper = pair(depth.nextDepth(), hash1, branch1, hash2, branch2)
        branches[branchInd1] = deeper
    } else {
        branches[branchInd1] = branch1
        branches[branchInd2] = branch2
    }
    return IntToIntMapBranches.Branch(branches)
}

private const val ARR_SIZE = 8
private const val DEPTH_STEP = 3
private const val MASK = 1.shl(DEPTH_STEP) - 1

private fun Int.index(): Int = MASK.and(this)
private fun Int.atDepth(d: Int): Int = shr(d)
private fun Int.indexAtDepth(d: Int): Int = atDepth(d).index()
private fun Int.nextDepth(): Int = this + DEPTH_STEP

