package arrow

import parsley.Kind

internal class ForConst private constructor()
internal typealias ConstOf<T> = Kind<ForConst, T>
internal fun <T, A> Kind<ConstOf<T>, A>.fix(): Const<T, A> = this as Const<T, A>

internal class Const<T, A>(val t: T) : Kind<Kind<ForConst, T>, A>

internal fun <T, A> Kind<Kind<ForConst, T>, A>.value(): T = fix().t
