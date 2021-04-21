package parsley.backend.codeGen.alt

import parsley.backend.CodeGenContext
import parsley.backend.instructions.Catch
import parsley.backend.instructions.InputCheck
import parsley.backend.instructions.JumpGood
import parsley.backend.instructions.JumpGoodAttempt
import parsley.backend.instructions.Label
import parsley.backend.instructions.RecoverAttempt
import parsley.backend.instructions.RecoverAttemptWith
import parsley.backend.instructions.RecoverWith
import parsley.frontend.Attempt
import parsley.frontend.ParserF
import parsley.frontend.Pure

@OptIn(ExperimentalStdlibApi::class)
suspend fun <I, E> DeepRecursiveScope<ParserF<I, E, Any?>, Unit>.altDefaultGen(
    xs: List<ParserF<I, E, Any?>>,
    ctx: CodeGenContext<I, E>
): Unit =
    when (xs.size) {
        1 -> callRecursive(xs.first())
        else -> {
            val fst = xs[0]
            val snd = xs[1]
            if (fst is Attempt) {
                if (snd is Pure) {
                    val badLabel = ctx.mkLabel()
                    ctx += InputCheck(badLabel)
                    callRecursive(fst)
                    // TODO Do I not need JumpGood here?
                    ctx += Label(badLabel)
                    if (ctx.discard) ctx += RecoverAttempt()
                    else ctx += RecoverAttemptWith(snd.a)
                } else {
                    val skip = ctx.mkLabel()
                    val badLabel = ctx.mkLabel()
                    ctx += InputCheck(badLabel)
                    callRecursive(fst)
                    ctx += Label(badLabel)
                    ctx += JumpGoodAttempt(skip)
                    altDefaultGen(xs.drop(1), ctx)
                    ctx += Label(skip)
                }
            } else {
                if (snd is Pure) {
                    val skip = ctx.mkLabel()
                    val badLabel = ctx.mkLabel()
                    ctx += InputCheck(badLabel)
                    callRecursive(fst)
                    ctx += JumpGood(skip)
                    ctx += Label(badLabel)
                    if (ctx.discard) ctx += Catch()
                    else ctx += RecoverWith(snd.a)
                    ctx += Label(skip)
                } else {
                    val skip = ctx.mkLabel()
                    val badLabel = ctx.mkLabel()
                    ctx += InputCheck(badLabel)
                    callRecursive(fst)
                    ctx += JumpGood(skip)
                    ctx += Label(badLabel)
                    ctx += Catch()
                    altDefaultGen(xs.drop(1), ctx)
                    ctx += Label(skip)
                }
            }
        }
    }
