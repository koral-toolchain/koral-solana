package dev.koral.compiler.frontend

import java.io.File
import dev.koral.compiler.model.*
import dev.koral.compiler.checks.ProgramChecks
import org.jetbrains.kotlin.backend.common.extensions.*
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.*

private class RejectedSource : RuntimeException(null, null, false, false)

@OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)
class KoralRegistrar : CompilerPluginRegistrar() {
    override val supportsK2 = true
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val messages = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        IrGenerationExtension.registerExtension(object : IrGenerationExtension {
            override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
                try {
                    compile(moduleFragment, messages)
                } catch (_: RejectedSource) {
                    // The collector already has a positioned error. Let Kotlin fail normally.
                }
            }
            private fun compile(moduleFragment: IrModuleFragment, messages: MessageCollector) {
                val source = File(System.getenv("KORAL_SOURCE")).canonicalFile
                val api = File(System.getenv("KORAL_API")).canonicalFile
                var diagnosticOffset=0
                var sourceFile: IrFile?=null
                fun <T> at(offset: Int, action: () -> T): T {
                    val previous = diagnosticOffset
                    diagnosticOffset = offset
                    return try { action() } finally { diagnosticOffset = previous }
                }
                fun reject(reason: String): Nothing {
                    val entry=sourceFile?.fileEntry
                    val line=entry?.getLineNumber(diagnosticOffset.coerceAtLeast(0))?.plus(1) ?: 1
                    val column=entry?.getColumnNumber(diagnosticOffset.coerceAtLeast(0))?.plus(1) ?: 1
                    messages.report(CompilerMessageSeverity.ERROR, "Koral: $reason", CompilerMessageLocation.create(source.path,line,column,null))
                    throw RejectedSource()
                }
                fun isU64(type: IrType) = type.classFqName?.asString() == "kotlin.ULong" && !type.isNullable()
                fun trusted(annotation: IrConstructorCall, name: String): Boolean =
                    annotation.symbol.owner.parentAsClass.fqNameWhenAvailable?.asString()==name &&
                    annotation.symbol.owner.fileOrNull?.fileEntry?.name?.let { File(it).canonicalFile }==api
                val file = moduleFragment.files.singleOrNull { File(it.fileEntry.name).canonicalFile == source } ?: reject("Missing entry source")
                if (source == api || listOf("dev.koral", "kotlin", "java").any { file.packageFqName.asString() == it || file.packageFqName.asString().startsWith("$it.") }) reject("Reserved package")
                sourceFile=file
                val classes=file.declarations.filterIsInstance<IrClass>()
                if(classes.size>1 || file.declarations.any { it !is IrSimpleFunction && it !is IrClass }) reject("Declare instruction functions and at most one account data class")
                val accountClass=classes.singleOrNull()
                val layout=accountClass?.let { klass -> at(klass.startOffset) {
                    val annotation=klass.annotations.singleOrNull { trusted(it,"dev.koral.api.Account") } ?: reject("Account class needs the trusted @Account annotation")
                    if(!klass.isData || klass.isInner || klass.typeParameters.isNotEmpty() || klass.superTypes.any { it.classFqName?.asString()!="kotlin.Any" }) reject("Account must be a non-generic data class without inheritance")
                    val constructor=klass.primaryConstructor ?: reject("Account needs a primary constructor")
                    val fields=constructor.parameters.filter { it.kind==IrParameterKind.Regular }
                    if(fields.size !in 1..16 || fields.any { !isU64(it.type) || it.defaultValue!=null || it.varargElementType!=null }) reject("Account requires 1..16 ULong fields without defaults")
                    if(fields.any { parameter -> parameter.annotations.any { trusted(it,"dev.koral.api.AccountOwner") || trusted(it,"dev.koral.api.AccountAddress") } }) reject("Account constraints require external instruction parameters, not state fields")
                    val properties=klass.declarations.filterIsInstance<IrProperty>()
                    if(properties.map { it.name.asString() }!=fields.map { it.name.asString() } || properties.any { it.isVar || it.isDelegated || it.backingField==null }) reject("Account fields must be immutable primary constructor properties")
                    if(klass.declarations.any { it is IrAnonymousInitializer || it is IrClass || (it is IrSimpleFunction && it.origin==IrDeclarationOrigin.DEFINED) }) reject("Account methods and init blocks are unsupported")
                    val seed=annotation.arguments.filterNotNull().singleOrNull()?.let { (it as? IrConst)?.value as? String } ?: reject("Account seed must be a constant string")
                    StateLayout(klass.name.asString(),seed,fields.map { it.name.asString() })
                } }
                val functions=file.declarations.filterIsInstance<IrSimpleFunction>()
                if(functions.size !in 1..32) reject("Declare 1..32 instruction functions")
                val instructions=functions.map { fn -> at(fn.startOffset) {
                val parameters = fn.parameters.filter { it.kind == IrParameterKind.Regular }
                fun isState(type: IrType) = accountClass!=null && type.classFqName==accountClass.fqNameWhenAvailable && !type.isNullable()
                fun accountType(type: IrType): ValueType? = if(type.isNullable())null else when(type.classFqName?.asString()) {
                    "dev.koral.api.AccountInfo" -> ValueType.ACCOUNT_READONLY
                    "dev.koral.api.WritableAccount" -> ValueType.ACCOUNT_WRITABLE
                    else -> null
                }
                fun constraint(parameter: IrValueParameter, name: String): String? = at(parameter.startOffset) {
                    val annotation=parameter.annotations.singleOrNull { trusted(it,"dev.koral.api.$name") } ?: return@at null
                    if(parameter==parameters.firstOrNull() || accountType(parameter.type)==null) reject("@$name requires an external AccountInfo or WritableAccount parameter")
                    val value=(annotation.arguments.filterNotNull().singleOrNull() as? IrConst)?.value as? String
                        ?: reject("@$name requires a constant Base58 public key")
                    try { PublicKey.decode(value) } catch (failure: IllegalArgumentException) {
                        reject("@$name: ${failure.message}")
                    }
                    value
                }
                val constraints=parameters.associateWith { Pair(constraint(it,"AccountOwner"),constraint(it,"AccountAddress")) }
                val dataParameters=parameters.drop(1).filter { isU64(it.type) }
                val accountParameters=parameters.drop(1).filter { accountType(it.type)!=null }
                val validState=parameters.firstOrNull()?.let { if(layout==null) it.name.asString()=="count" && isU64(it.type) else isState(it.type) }==true
                val validReturn=if(layout==null)isU64(fn.returnType) else isState(fn.returnType)
                if (!fn.name.asString().matches(Regex("[a-z][a-z0-9_]{0,63}")) || fn.name.asString()=="initialize" || fn.isSuspend || fn.typeParameters.isNotEmpty() || parameters.size !in 1..17 || dataParameters.size>8 || accountParameters.size>8 || fn.parameters.size != parameters.size || !validState || parameters.drop(1).any { !isU64(it.type) && accountType(it.type)==null } || parameters.any { it.defaultValue != null || it.varargElementType != null } || !validReturn) reject("Expected instruction(count: ULong, ... up to eight ULong arguments): ULong; use lower_snake_case names, initialize is reserved")
                if (!fn.annotations.any { it.symbol.owner.parentAsClass.fqNameWhenAvailable?.asString() == "dev.koral.api.Instruction" && it.symbol.owner.fileOrNull?.fileEntry?.name?.let { path -> File(path).canonicalFile } == api }) reject("Missing trusted @Instruction")
                val names = mutableMapOf<org.jetbrains.kotlin.ir.symbols.IrValueSymbol,String>()
                parameters.forEachIndexed { i,param -> names[param.symbol]=if(i==0 && layout!=null)"state" else param.name.asString() }
                var localIndex = 0
                fun valueType(type: IrType): ValueType = when {
                    isU64(type) -> ValueType.U64
                    isState(type) -> ValueType.STATE
                    accountType(type)!=null -> accountType(type)!!
                    type.isBoolean() && !type.isNullable() -> ValueType.BOOL
                    else -> reject("Only ULong, Boolean, the declared account type, and account references are supported")
                }
                lateinit var expression: (IrExpression) -> Expression
                fun number(e: IrExpression): ULong? = when(e) {
                    is IrConst -> if (isU64(e.type)) (e.value as? Number)?.toLong()?.toULong() else null
                    is IrConstructorCall -> if (isU64(e.type)) e.arguments.filterNotNull().singleOrNull()?.let { (it as? IrConst)?.value as? Number }?.toLong()?.toULong() else null
                    else -> null
                }
                expression = fun(e: IrExpression): Expression = at(e.startOffset) {
                    number(e)?.let { return@at Expression.Number(it) }
                    when(e) {
                        is IrConst -> if(e.value is Boolean) Expression.BooleanValue(e.value as Boolean) else reject("Unsupported literal: ${e.type.classFqName?.asString() ?: "unknown type"}")
                        is IrConstructorCall -> {
                            if(accountClass==null || e.symbol.owner.parentAsClass!=accountClass) reject("Unsupported constructor")
                            Expression.Construct(e.arguments.filterNotNull().map(expression))
                        }
                        is IrGetValue -> Expression.Variable(names[e.symbol] ?: reject("Unknown value"))
                        is IrWhen -> {
                            fun branch(i: Int): Expression {
                                val b=e.branches.getOrNull(i) ?: reject("An expression must cover every branch")
                                return if(b is IrElseBranch) expression(b.result) else Expression.Choice(expression(b.condition),expression(b.result),branch(i+1))
                            }
                            branch(0)
                        }
                        is IrBlock -> if(e.statements.size==1 && e.statements[0] is IrExpression) expression(e.statements[0] as IrExpression) else reject("Unsupported expression block")
                        is IrCall -> {
                            val target=e.symbol.owner
                            val fq=target.fqNameWhenAvailable?.asString().orEmpty()
                            val args=e.arguments.filterNotNull()
                            val property=target.correspondingPropertySymbol?.owner
                            if(accountClass!=null && property?.parent==accountClass && target==property.getter && args.size==1) return@at Expression.Field(expression(args[0]),property.name.asString())
                            val trusted=target.fileOrNull?.fileEntry?.name?.let { File(it).canonicalFile }==api
                            if(trusted && fq=="dev.koral.api.lamports" && args.size==1) return@at Expression.AccountLamports(expression(args[0]))
                            val checked=mapOf("dev.koral.api.checkedAdd" to Operation.ADD,"dev.koral.api.checkedSubtract" to Operation.SUBTRACT,"dev.koral.api.checkedMultiply" to Operation.MULTIPLY)
                            if(trusted && checked.containsKey(fq) && args.size==2) return@at Expression.Binary(checked.getValue(fq),expression(args[0]),expression(args[1]))
                            val comparison=mapOf("less" to Operation.LT,"lessOrEqual" to Operation.LE,"greater" to Operation.GT,"greaterOrEqual" to Operation.GE)
                            if(fq.startsWith("kotlin.internal.ir.") && target.name.asString() in comparison && args.size==2) {
                                val compare=args[0] as? IrCall
                                if(compare?.symbol?.owner?.fqNameWhenAvailable?.asString()=="kotlin.ULong.compareTo" && (args[1] as? IrConst)?.value==0) {
                                    val values=compare.arguments.filterNotNull()
                                    return@at Expression.Binary(comparison.getValue(target.name.asString()),expression(values[0]),expression(values[1]))
                                }
                            }
                            if(fq=="kotlin.internal.ir.EQEQ" && args.size==2) return@at Expression.Binary(Operation.EQ,expression(args[0]),expression(args[1]))
                            if(fq=="kotlin.Boolean.not" && args.size==1) return@at Expression.Not(expression(args[0]))
                            reject("Unsupported call $fq; use checkedAdd, checkedSubtract or checkedMultiply for arithmetic")
                        }
                        else -> reject("Unsupported expression; use supported values, calls or conditions")
                    }
                }
                lateinit var statements: (List<IrStatement>) -> List<Statement>
                fun branchBody(e: IrExpression): List<Statement> = if(e is IrBlock) statements(e.statements) else statements(listOf(e))
                statements = fun(list: List<IrStatement>): List<Statement> = list.map { s -> at(s.startOffset) {
                    when(s) {
                        is IrVariable -> {
                            val initial=s.initializer ?: reject("Locals require an initializer")
                            var name: String
                            do { name="local_${localIndex++}" } while(name in names.values)
                            val value=expression(initial);names[s.symbol]=name
                            Statement.Bind(name,valueType(s.type),s.isVar,value)
                        }
                        is IrSetValue -> Statement.Assign(names[s.symbol] ?: reject("Unknown assignment target"),expression(s.value))
                        is IrReturn -> {
                            if(s.returnTargetSymbol!=fn.symbol) reject("Unsupported return target")
                            Statement.Return(expression(s.value))
                        }
                        is IrWhen -> {
                            fun nested(i: Int): List<Statement> {
                                val b=s.branches.getOrNull(i) ?: return emptyList()
                                return if(b is IrElseBranch) branchBody(b.result) else listOf(Statement.Branch(expression(b.condition),branchBody(b.result),nested(i+1)))
                            }
                            nested(0).singleOrNull() ?: reject("Unsupported branch")
                        }
                        is IrCall -> {
                            val target=s.symbol.owner
                            val args=s.arguments.filterNotNull()
                            val intrinsic=target.fqNameWhenAvailable?.asString()
                            val isTrusted=target.fileOrNull?.fileEntry?.name?.let { File(it).canonicalFile }==api
                            if(!isTrusted || args.size!=2) reject("Only ensure and transferLamports may be used as call statements")
                            when(intrinsic) {
                                "dev.koral.api.transferLamports" -> Statement.TransferLamports(expression(args[0]),expression(args[1]))
                                "dev.koral.api.ensure" -> {
                                    val code=number(args[1]) ?: reject("ensure requires a constant error code")
                                    if(code==0uL || code>UInt.MAX_VALUE.toULong()) reject("Error code must be in 1..4294967295")
                                    Statement.Require(expression(args[0]),code.toUInt())
                                }
                                else -> reject("Only ensure and transferLamports may be used as call statements")
                            }
                        }
                        is IrLoop -> reject("Unsupported statement: loops are not supported; use explicit conditions instead")
                        else -> reject("Unsupported statement; use locals, assignments, branches, returns, ensure or transferLamports")
                    }
                } }
                val body=fn.body as? IrBlockBody ?: reject("Missing function body")
                CounterInstruction(fn.name.asString(),dataParameters.map { it.name.asString() },statements(body.statements),accountParameters.map { AccountParameter(it.name.asString(),accountType(it.type)==ValueType.ACCOUNT_WRITABLE,constraints.getValue(it).first,constraints.getValue(it).second) })
                } }
                val program=CounterProgram(source.name,instructions,layout)
                try {
                    ProgramChecks.validate(program)
                } catch (failure: IllegalArgumentException) {
                    reject("Invalid program: ${failure.message ?: "model validation failed"}")
                }
                ProgramCodec.write(program, File(System.getenv("KORAL_IR_OUT")).toPath())
            }
        })
    }
}
