package dev.koral.compiler.frontend

import dev.koral.compiler.checks.ProgramChecks
import dev.koral.compiler.model.AccountParameter
import dev.koral.compiler.model.CounterInstruction
import dev.koral.compiler.model.CounterProgram
import dev.koral.compiler.model.Expression
import dev.koral.compiler.model.Operation
import dev.koral.compiler.model.ProgramCodec
import dev.koral.compiler.model.PublicKey
import dev.koral.compiler.model.StateLayout
import dev.koral.compiler.model.Statement
import dev.koral.compiler.model.ValueType
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrElseBranch
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrLoop
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.primaryConstructor
import java.io.File

@OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)
internal class InstructionLowering(
    private val context: SourceLowering,
    private val fn: IrSimpleFunction,
) {
    private val api = context.api
    private val accountClass = context.accountClass
    private val layout = context.layout

    private fun reject(reason: String): Nothing = context.reject(reason)

    private fun <T> at(
        offset: Int,
        action: () -> T,
    ): T = context.at(offset, action)

    private fun isU64(type: IrType) = context.isU64(type)

    private fun trusted(
        annotation: IrConstructorCall,
        name: String,
    ) = context.trusted(annotation, name)

    private val parameters = fn.parameters.filter { it.kind == IrParameterKind.Regular }

    private fun isState(type: IrType) = accountClass != null && type.classFqName == accountClass.fqNameWhenAvailable && !type.isNullable()

    private fun accountType(type: IrType): ValueType? =
        if (type.isNullable()) {
            null
        } else {
            when (type.classFqName?.asString()) {
                "dev.koral.api.AccountInfo" -> ValueType.ACCOUNT_READONLY
                "dev.koral.api.WritableAccount" -> ValueType.ACCOUNT_WRITABLE
                else -> null
            }
        }

    private fun constraint(
        parameter: IrValueParameter,
        name: String,
    ): String? =
        at(parameter.startOffset) {
            val annotation = parameter.annotations.singleOrNull { trusted(it, "dev.koral.api.$name") } ?: return@at null
            if (parameter == parameters.firstOrNull() ||
                accountType(parameter.type) == null
            ) {
                reject("@$name requires an external AccountInfo or WritableAccount parameter")
            }
            val value =
                (annotation.arguments.filterNotNull().singleOrNull() as? IrConst)?.value as? String
                    ?: reject("@$name requires a constant Base58 public key")
            try {
                PublicKey.decode(value)
            } catch (failure: IllegalArgumentException) {
                reject("@$name: ${failure.message}")
            }
            value
        }

    private fun metadata(
        parameter: IrValueParameter,
        name: String,
    ): Any? =
        at(parameter.startOffset) {
            val annotation = parameter.annotations.singleOrNull { trusted(it, "dev.koral.api.$name") } ?: return@at null
            if (parameter == parameters.firstOrNull() ||
                accountType(parameter.type) == null
            ) {
                reject("@$name requires an external AccountInfo or WritableAccount parameter")
            }
            val value = (annotation.arguments.filterNotNull().singleOrNull() as? IrConst)?.value
            when (name) {
                "AccountDataSize" -> if (value !is Int || value < 0) reject("@AccountDataSize requires a constant non-negative Int")
                "AccountExecutable" -> if (value !is Boolean) reject("@AccountExecutable requires a constant Boolean")
            }
            value
        }

    private val names = mutableMapOf<org.jetbrains.kotlin.ir.symbols.IrValueSymbol, String>()

    init {
        parameters.forEachIndexed { i, param -> names[param.symbol] = if (i == 0 && layout != null)"state" else param.name.asString() }
    }

    private var localIndex = 0

    private fun valueType(type: IrType): ValueType =
        when {
            isU64(type) -> ValueType.U64
            isState(type) -> ValueType.STATE
            accountType(type) != null -> accountType(type)!!
            type.isBoolean() && !type.isNullable() -> ValueType.BOOL
            else -> reject("Only ULong, Boolean, the declared account type, and account references are supported")
        }

    private fun number(e: IrExpression): ULong? =
        when (e) {
            is IrConst -> if (isU64(e.type)) (e.value as? Number)?.toLong()?.toULong() else null
            is IrConstructorCall ->
                if (isU64(e.type)) {
                    e.arguments
                        .filterNotNull()
                        .singleOrNull()
                        ?.let { (it as? IrConst)?.value as? Number }
                        ?.toLong()
                        ?.toULong()
                } else {
                    null
                }
            else -> null
        }

    private fun expression(e: IrExpression): Expression =
        at(e.startOffset) {
            number(e)?.let { return@at Expression.Number(it) }
            when (e) {
                is IrConst ->
                    if (e.value is Boolean) {
                        Expression.BooleanValue(
                            e.value as Boolean,
                        )
                    } else {
                        reject("Unsupported literal: ${e.type.classFqName?.asString() ?: "unknown type"}")
                    }
                is IrConstructorCall -> {
                    if (accountClass == null || e.symbol.owner.parentAsClass != accountClass) reject("Unsupported constructor")
                    Expression.Construct(e.arguments.filterNotNull().map(::expression))
                }
                is IrGetValue -> Expression.Variable(names[e.symbol] ?: reject("Unknown value"))
                is IrWhen -> branch(e, 0)
                is IrBlock -> blockExpression(e)
                is IrCall -> callExpression(e)
                else -> reject("Unsupported expression; use supported values, calls or conditions")
            }
        }

    fun branch(
        e: IrWhen,
        i: Int,
    ): Expression {
        val b = e.branches.getOrNull(i) ?: reject("An expression must cover every branch")
        return if (b is IrElseBranch) {
            expression(b.result)
        } else {
            Expression.Choice(
                expression(b.condition),
                expression(b.result),
                branch(e, i + 1),
            )
        }
    }

    private fun blockExpression(e: IrBlock): Expression =
        if (e.statements.size == 1 &&
            e.statements[0] is IrExpression
        ) {
            expression(e.statements[0] as IrExpression)
        } else {
            reject("Unsupported expression block")
        }

    private fun callExpression(e: IrCall): Expression {
        val target = e.symbol.owner
        val fq = target.fqNameWhenAvailable?.asString().orEmpty()
        val args = e.arguments.filterNotNull()
        val property = target.correspondingPropertySymbol?.owner
        if (isStateGetter(e)) {
            return Expression.Field(expression(args[0]), requireNotNull(property).name.asString())
        }
        val trusted =
            target.fileOrNull
                ?.fileEntry
                ?.name
                ?.let { File(it).canonicalFile } == api
        if (trusted && fq == "dev.koral.api.lamports" && args.size == 1) return Expression.AccountLamports(expression(args[0]))
        val checked =
            mapOf(
                "dev.koral.api.checkedAdd" to Operation.ADD,
                "dev.koral.api.checkedSubtract" to Operation.SUBTRACT,
                "dev.koral.api.checkedMultiply" to Operation.MULTIPLY,
            )
        if (trusted && checked.containsKey(fq) &&
            args.size == 2
        ) {
            return Expression.Binary(checked.getValue(fq), expression(args[0]), expression(args[1]))
        }
        comparison(e)?.let { return it }
        if (fq == "kotlin.internal.ir.EQEQ" &&
            args.size == 2
        ) {
            return Expression.Binary(Operation.EQ, expression(args[0]), expression(args[1]))
        }
        if (fq == "kotlin.Boolean.not" && args.size == 1) return Expression.Not(expression(args[0]))
        reject("Unsupported call $fq; use checkedAdd, checkedSubtract or checkedMultiply for arithmetic")
    }

    private fun branchBody(e: IrExpression): List<Statement> = if (e is IrBlock) statements(e.statements) else statements(listOf(e))

    private fun statements(list: List<IrStatement>): List<Statement> =
        list.map { s ->
            at(s.startOffset) {
                when (s) {
                    is IrVariable -> {
                        val initial = s.initializer ?: reject("Locals require an initializer")
                        var name: String
                        do {
                            name = "local_${localIndex++}"
                        } while (name in names.values)
                        val value = expression(initial)
                        names[s.symbol] = name
                        Statement.Bind(name, valueType(s.type), s.isVar, value)
                    }
                    is IrSetValue -> Statement.Assign(names[s.symbol] ?: reject("Unknown assignment target"), expression(s.value))
                    is IrReturn -> {
                        if (s.returnTargetSymbol != fn.symbol) reject("Unsupported return target")
                        Statement.Return(expression(s.value))
                    }
                    is IrWhen -> nested(s, 0).singleOrNull() ?: reject("Unsupported branch")
                    is IrCall -> callStatement(s)
                    is IrLoop -> reject("Unsupported statement: loops are not supported; use explicit conditions instead")
                    else -> reject("Unsupported statement; use locals, assignments, branches, returns, ensure or transferLamports")
                }
            }
        }

    private fun isStateGetter(e: IrCall): Boolean {
        val target = e.symbol.owner
        val property = target.correspondingPropertySymbol?.owner ?: return false
        val owned = accountClass != null && property.parent == accountClass
        return owned && target == property.getter && e.arguments.filterNotNull().size == 1
    }

    private fun comparison(e: IrCall): Expression? {
        val target = e.symbol.owner
        val fq = target.fqNameWhenAvailable?.asString().orEmpty()
        val args = e.arguments.filterNotNull()
        val comparison =
            mapOf(
                "less" to Operation.LT,
                "lessOrEqual" to Operation.LE,
                "greater" to Operation.GT,
                "greaterOrEqual" to Operation.GE,
            )
        if (fq.startsWith("kotlin.internal.ir.") && target.name.asString() in comparison && args.size == 2) {
            val compare = args[0] as? IrCall
            if (compare
                    ?.symbol
                    ?.owner
                    ?.fqNameWhenAvailable
                    ?.asString() == "kotlin.ULong.compareTo" && (args[1] as? IrConst)?.value == 0
            ) {
                val values = compare.arguments.filterNotNull()
                return Expression.Binary(
                    comparison.getValue(target.name.asString()),
                    expression(values[0]),
                    expression(values[1]),
                )
            }
        }
        return null
    }

    fun nested(
        s: IrWhen,
        i: Int,
    ): List<Statement> {
        val b = s.branches.getOrNull(i) ?: return emptyList()
        return if (b is IrElseBranch) {
            branchBody(b.result)
        } else {
            listOf(
                Statement.Branch(
                    expression(b.condition),
                    branchBody(b.result),
                    nested(s, i + 1),
                ),
            )
        }
    }

    private fun callStatement(s: IrCall): Statement {
        val target = s.symbol.owner
        val args = s.arguments.filterNotNull()
        val intrinsic = target.fqNameWhenAvailable?.asString()
        val isTrusted =
            target.fileOrNull
                ?.fileEntry
                ?.name
                ?.let { File(it).canonicalFile } == api
        if (!isTrusted) reject("Only trusted ensure and lamport operations may be used as call statements")
        if (intrinsic == "dev.koral.api.depositLamports") return deposit(args)
        if (args.size != 2) reject("Expected two intrinsic arguments")
        return when (intrinsic) {
            "dev.koral.api.withdrawLamports" -> {
                if (layout == null) reject("Vault operations require an owned @Account state")
                Statement.WithdrawLamports(expression(args[0]), expression(args[1]))
            }
            "dev.koral.api.transferLamports" -> Statement.TransferLamports(expression(args[0]), expression(args[1]))
            "dev.koral.api.ensure" -> {
                val code = number(args[1]) ?: reject("ensure requires a constant error code")
                if (code == 0uL || code > UInt.MAX_VALUE.toULong()) reject("Error code must be in 1..4294967295")
                Statement.Require(expression(args[0]), code.toUInt())
            }
            else -> reject("Only trusted ensure and lamport operations may be used as call statements")
        }
    }

    private fun deposit(args: List<IrExpression>): Statement {
        if (layout == null) reject("Vault operations require an owned @Account state")
        if (args.size != 1) reject("depositLamports requires one amount")
        return Statement.DepositLamports(expression(args[0]))
    }

    private fun invalidSignature(): Nothing =
        reject(
            (
                "Expected instruction(count: ULong, ... up to eight ULong arguments): ULong; " +
                    "use lower_snake_case names, initialize is reserved"
            ),
        )

    private fun validateSignature() {
        val validState =
            parameters.firstOrNull()?.let { if (layout == null) it.name.asString() == "count" && isU64(it.type) else isState(it.type) } ==
                true
        val validReturn = if (layout == null)isU64(fn.returnType) else isState(fn.returnType)
        if (!fn.name.asString().matches(Regex("[a-z][a-z0-9_]{0,63}")) || fn.name.asString() == "initialize") invalidSignature()
        if (fn.isSuspend || fn.typeParameters.isNotEmpty() || fn.parameters.size != parameters.size) invalidSignature()
        if (!validState || !validReturn) invalidSignature()
        validateParameters()
    }

    private fun validateParameters() {
        if (parameters.size !in 1..17 || parameters.drop(1).count { isU64(it.type) } > 8 ||
            parameters.drop(1).count { accountType(it.type) != null } > 8
        ) {
            invalidSignature()
        }
        if (parameters.drop(1).any { !isU64(it.type) && accountType(it.type) == null }) invalidSignature()
        if (parameters.any { it.defaultValue != null || it.varargElementType != null }) invalidSignature()
    }

    fun compile(): CounterInstruction {
        val metadataConstraints =
            parameters.associateWith {
                Pair(metadata(it, "AccountDataSize") as Int?, metadata(it, "AccountExecutable") as Boolean?)
            }
        val constraints = parameters.associateWith { Pair(constraint(it, "AccountOwner"), constraint(it, "AccountAddress")) }
        val dataParameters = parameters.drop(1).filter { isU64(it.type) }
        val accountParameters = parameters.drop(1).filter { accountType(it.type) != null }
        validateSignature()
        if (!fn.annotations.any {
                it.symbol.owner.parentAsClass.fqNameWhenAvailable
                    ?.asString() == "dev.koral.api.Instruction" && it.symbol.owner.fileOrNull
                    ?.fileEntry
                    ?.name
                    ?.let { path -> File(path).canonicalFile } == api
            }
        ) {
            reject("Missing trusted @Instruction")
        }
        val body = fn.body as? IrBlockBody ?: reject("Missing function body")
        return CounterInstruction(
            fn.name.asString(),
            dataParameters.map { it.name.asString() },
            statements(body.statements),
            accountParameters.map {
                AccountParameter(
                    it.name.asString(),
                    accountType(it.type) == ValueType.ACCOUNT_WRITABLE,
                    constraints.getValue(it).first,
                    constraints.getValue(it).second,
                    metadataConstraints.getValue(it).first,
                    metadataConstraints.getValue(it).second,
                )
            },
        )
    }
}
