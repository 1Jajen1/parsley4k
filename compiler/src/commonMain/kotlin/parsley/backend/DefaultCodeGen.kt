package parsley.backend

import parsley.backend.codeGen.altGen
import parsley.backend.codeGen.applyGen
import parsley.backend.codeGen.applyLGen
import parsley.backend.codeGen.applyRGen
import parsley.backend.codeGen.attemptGen
import parsley.backend.codeGen.catchGen
import parsley.backend.codeGen.chunkOfGen
import parsley.backend.codeGen.emptyGen
import parsley.backend.codeGen.eofGen
import parsley.backend.codeGen.failGen
import parsley.backend.codeGen.labelGen
import parsley.backend.codeGen.letGen
import parsley.backend.codeGen.lookAheadGen
import parsley.backend.codeGen.manyGen
import parsley.backend.codeGen.matchOfGen
import parsley.backend.codeGen.negLookAheadGen
import parsley.backend.codeGen.pureGen
import parsley.backend.codeGen.satisfyGen
import parsley.backend.codeGen.selectGen
import parsley.backend.codeGen.singleGen
import parsley.backend.codeGen.toNativeGen

fun <I, E> defaultCodeGen(): CodeGenStep<I, E> {
    val steps: Array<CodeGenStep<I, E>> = arrayOf(
        altGen(),
        applyGen(),
        applyLGen(),
        applyRGen(),
        attemptGen(),
        catchGen(),
        chunkOfGen(),
        labelGen(),
        letGen(),
        lookAheadGen(),
        manyGen(),
        matchOfGen(),
        negLookAheadGen(),
        selectGen(),
        toNativeGen(),
        pureGen(),
        emptyGen(),
        satisfyGen(),
        singleGen(),
        failGen(),
        eofGen()
    )

    return CodeGenStep { p, ctx, settings ->
        var handled = false
        for ((s) in steps) {
            if (s(p, ctx, settings)) {
                handled = true
                break
            }
        }
        handled
    }
}
