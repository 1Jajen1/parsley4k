package parsley.frontend

import pretty.Doc
import pretty.align
import pretty.group
import pretty.nest
import pretty.softLine
import pretty.text

class ByteListToArr<E>(p: ParserF<Byte, E, List<Byte>>) : Unary<Byte, E, List<Byte>, ByteArray>(p), DistributesOrElse {
    override fun small(): Boolean = inner.small()
    override fun copy(inner: ParserF<Byte, E, List<Byte>>): ParserF<Byte, E, ByteArray> =
        ByteListToArr(inner)

    override fun pprint(): Doc<Nothing> = cons(inner, "AsByteArr")
}