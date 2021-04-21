package parsley.backend.codeGen

import parsley.backend.CodeGenStep
import parsley.frontend.ToNative

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> toNativeGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is ToNative) {
        callRecursive(p.inner)
        if (!ctx.discard) ctx += parsley.backend.instructions.ToNative()
        true
    } else false
}
