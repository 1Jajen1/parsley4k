package parsley.settings

data class CompilerSettings<I, E>(
    val frontend: FrontendSettings<I, E>,
    val backend: BackendSettings<I, E>,
    val logging: LogSettings
)

data class LogSettings(
    val showWarnings: Boolean = true,
    val printInitialAST: Boolean = false,
    val printFixedAST: Boolean = false,
    val printOptimisedAST: Boolean = false,
    val printInitialInstr: Boolean = false,
    val printInlinedInstr: Boolean = false,
    val printRewrittenRulesInstr: Boolean = false,
    val printOptimisedInstr: Boolean = false,
    val printFinalInstr: Boolean = false
)

fun defaultLogSettings(): LogSettings = LogSettings()

fun <I, E> CompilerSettings<I, E>.printAllStages(): CompilerSettings<I, E> =
    printInitialAST()
        .printFixedAST()
        .printOptimisedAST()
        .printInitialInstr()
        .printInlinedInstr()
        .printRewrittenInstr()
        .printOptimisedInstr()
        .printFinalInstr()

fun <I, E> CompilerSettings<I, E>.hideWarnings(): CompilerSettings<I, E> =
    copy(logging = logging.copy(showWarnings = false))

fun <I, E> CompilerSettings<I, E>.printInitialAST(): CompilerSettings<I, E> =
    copy(logging = logging.copy(printInitialAST = true))

fun <I, E> CompilerSettings<I, E>.printFixedAST(): CompilerSettings<I, E> =
    copy(logging = logging.copy(printFixedAST = true))

fun <I, E> CompilerSettings<I, E>.printOptimisedAST(): CompilerSettings<I, E> =
    copy(logging = logging.copy(printOptimisedAST = true))

fun <I, E> CompilerSettings<I, E>.printInitialInstr(): CompilerSettings<I, E> =
    copy(logging = logging.copy(printInitialInstr = true))

fun <I, E> CompilerSettings<I, E>.printInlinedInstr(): CompilerSettings<I, E> =
    copy(logging = logging.copy(printInlinedInstr = true))

fun <I, E> CompilerSettings<I, E>.printRewrittenInstr(): CompilerSettings<I, E> =
    copy(logging = logging.copy(printRewrittenRulesInstr = true))

fun <I, E> CompilerSettings<I, E>.printOptimisedInstr(): CompilerSettings<I, E> =
    copy(logging = logging.copy(printOptimisedInstr = true))

fun <I, E> CompilerSettings<I, E>.printFinalInstr(): CompilerSettings<I, E> =
    copy(logging = logging.copy(printFinalInstr = true))
