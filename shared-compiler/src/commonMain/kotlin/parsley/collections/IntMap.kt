package parsley.collections

class IntSet private constructor(private val branches: Branches.Branch<Unit>) {
    private var size = 0
    operator fun contains(i: Int): Boolean = contains(branches, i)

    operator fun plusAssign(i: Int): Unit = add(i)
    fun add(i: Int): Unit = insert(branches, i, Unit)
        .let { if (it) size++ }

    fun remove(i: Int): Boolean = remove(branches, i)
        .also { if (it) size-- }

    fun onEach(f: (Int) -> Unit) = onEach(branches) { k, _ -> f(k) }
    fun size(): Int = size
    fun isEmpty(): Boolean = size == 0
    fun isNotEmpty(): Boolean = size != 0
    fun first(): Int = first(branches)?.first ?: throw NoSuchElementException()

    override fun toString(): String {
        val map = mutableListOf<Int>()
        onEach { k -> map.add(k) }
        return "IntSet($map)"
    }

    companion object {
        fun empty(): IntSet = IntSet(Branches.Branch(arrayOfNulls(ARR_SIZE)))
    }
}

class IntMap<A> private constructor(private val branches: Branches.Branch<A>) {
    private var size: Int = 0
    operator fun set(i: Int, v: A): Unit = insert(branches, i, v)
        .let { if (it) size++ }

    operator fun get(i: Int): A = lookup(branches, i)
    operator fun contains(i: Int): Boolean = contains(branches, i)
    fun onEach(f: (Int, A) -> A) = onEach(branches, f)
    fun remove(i: Int): Boolean = remove(branches, i)
        .also { if (it) size-- }

    fun size(): Int = size
    fun isEmpty(): Boolean = size == 0
    fun isNotEmpty(): Boolean = size != 0
    fun filter(f: (Int, A) -> Boolean): Unit = filter(branches, f).let { size -= it }
    fun filterKeys(f: (Int) -> Boolean): Unit = filter { k, _ -> f(k) }
    fun filterValues(f: (A) -> Boolean): Unit = filter { _, v -> f(v) }
    fun forEach(f: (Int, A) -> Unit): Unit = onEach { k, v -> v.also { f(k, v) } }
    fun first(): Pair<Int, A> = first(branches) ?: throw NoSuchElementException()

    fun <B> mapValues(f: (A) -> B): IntMap<B> {
        val m = empty<B>()
        forEach { k, a -> m[k] = f(a) }
        return m
    }

    fun keySet(): IntSet {
        val s = IntSet.empty()
        forEach { k, _ -> s += k }
        return s
    }

    fun copy(): IntMap<A> {
        val m = empty<A>()
        forEach { k, a -> m[k] = a }
        return m
    }

    override fun toString(): String {
        val map = mutableListOf<Pair<Int, A>>()
        onEach { k, v -> v.also { map.add(k to v) } }
        return "IntMap($map)"
    }

    companion object {
        fun <A> empty(): IntMap<A> = IntMap(Branches.Branch(arrayOfNulls(ARR_SIZE)))
    }
}

private sealed class Branches<A> {
    class Leaf<A>(val key: Int, var value: A) : Branches<A>()
    class Branch<A>(val xs: Array<Branches<A>?>) : Branches<A>()
}

private fun <A> contains(b: Branches.Branch<A>, key: Int): Boolean {
    var depth = 0
    var curr = b
    while (true) {
        val ind = key.indexAtDepth(depth)
        when (val new = curr.xs[ind]) {
            null -> return false
            is Branches.Leaf -> return new.key == key
            is Branches.Branch -> {
                depth = depth.nextDepth()
                curr = new
            }
        }
    }
}

private fun <A> first(branches: Branches.Branch<A>): Pair<Int, A>? {
    branches.xs.forEach { b ->
        when (b) {
            null -> Unit
            is Branches.Leaf -> return@first b.key to b.value
            is Branches.Branch -> {
                first(b)?.let { return@first it }
            }
        }
    }
    return null
}

private fun <A> remove(b: Branches.Branch<A>, key: Int): Boolean {
    var depth = 0
    var curr = b
    while (true) {
        val ind = key.indexAtDepth(depth)
        when (val new = curr.xs[ind]) {
            null -> return false
            is Branches.Leaf -> {
                if (new.key == key) {
                    curr.xs[ind] = null
                    return true
                } else return false
            }
            is Branches.Branch -> {
                depth = depth.nextDepth()
                curr = new
            }
        }
    }
}

private fun <A> lookup(b: Branches.Branch<A>, key: Int): A {
    var depth = 0
    var curr = b
    while (true) {
        val ind = key.indexAtDepth(depth)
        when (val new = curr.xs[ind]) {
            null -> throw NoSuchElementException()
            is Branches.Leaf -> {
                if (new.key == key) return new.value
                else throw NoSuchElementException()
            }
            is Branches.Branch -> {
                depth = depth.nextDepth()
                curr = new
            }
        }
    }
}

private fun <A> insert(b: Branches.Branch<A>, key: Int, value: A): Boolean {
    var depth = 0
    var curr = b
    while (true) {
        val ind = key.indexAtDepth(depth)
        when (val step = curr.xs[ind]) {
            null -> {
                curr.xs[ind] = Branches.Leaf(key, value)
                return true
            }
            is Branches.Leaf -> {
                if (step.key == key) {
                    step.value = value
                    return false
                } else {
                    val new = pair(
                        depth.nextDepth(),
                        key,
                        Branches.Leaf(key, value),
                        step.key,
                        step
                    )
                    curr.xs[ind] = new
                    return true
                }
            }
            is Branches.Branch -> {
                depth = depth.nextDepth()
                curr = step
            }
        }
    }
}

private fun <A> pair(
    depth: Int,
    hash1: Int,
    branch1: Branches<A>,
    hash2: Int,
    branch2: Branches<A>
): Branches.Branch<A> {
    val branchInd1 = hash1.indexAtDepth(depth)
    val branchInd2 = hash2.indexAtDepth(depth)
    val branches = arrayOfNulls<Branches<A>>(ARR_SIZE)
    if (branchInd1 == branchInd2) {
        val deeper = pair(depth.nextDepth(), hash1, branch1, hash2, branch2)
        branches[branchInd1] = deeper
    } else {
        branches[branchInd1] = branch1
        branches[branchInd2] = branch2
    }
    return Branches.Branch(branches)
}

private fun <A> onEach(branches: Branches.Branch<A>, f: (Int, A) -> A): Unit {
    branches.xs.forEach { b ->
        when (b) {
            null -> Unit
            is Branches.Leaf -> b.value = f(b.key, b.value)
            is Branches.Branch -> onEach(b, f)
        }
    }
}

private fun <A> filter(branches: Branches.Branch<A>, f: (Int, A) -> Boolean): Int {
    var removed = 0
    for (i in branches.xs.indices) {
        when (val el = branches.xs[i]) {
            null -> Unit
            is Branches.Leaf -> {
                if (f(el.key, el.value).not()) {
                    branches.xs[i] = null
                    removed++
                }
            }
            is Branches.Branch -> removed += filter(el, f)
        }
    }
    return removed
}

private const val ARR_SIZE = 32
private const val DEPTH_STEP = 5
private const val MASK = 1.shl(DEPTH_STEP) - 1

private fun Int.index(): Int = MASK.and(this)
private fun Int.atDepth(d: Int): Int = shr(d)
private fun Int.indexAtDepth(d: Int): Int = atDepth(d).index()
private fun Int.nextDepth(): Int = this + DEPTH_STEP
