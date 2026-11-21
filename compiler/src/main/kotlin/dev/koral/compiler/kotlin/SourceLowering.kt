package dev.koral.compiler.kotlin

import dev.koral.compiler.ir.AccountParameter
import dev.koral.compiler.ir.CounterInstruction
import dev.koral.compiler.ir.CounterProgram
import dev.koral.compiler.ir.Expression
import dev.koral.compiler.ir.Operation
import dev.koral.compiler.ir.ProgramCodec
import dev.koral.compiler.ir.PublicKey
import dev.koral.compiler.ir.StateLayout
import dev.koral.compiler.ir.Statement
import dev.koral.compiler.ir.ValueType
import dev.koral.compiler.validation.ProgramChecks
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

internal class RejectedSource : RuntimeException(null, null, false, false)

@OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)
internal class SourceLowering(
    private val messages: MessageCollector,
) {
    val source = File(System.getenv("KORAL_SOURCE")).canonicalFile
    val api = File(System.getenv("KORAL_API")).canonicalFile
    var accountClass: IrClass? = null
        private set
    var layout: StateLayout? = null
        private set
    private var diagnosticOffset = 0
    private var sourceFile: IrFile? = null

    fun <T> at(
        offset: Int,
        action: () -> T,
    ): T {
        val previous = diagnosticOffset
        diagnosticOffset = offset
        return try {
            action()
        } finally {
            diagnosticOffset = previous
        }
    }

    fun reject(reason: String): Nothing {
        val entry = sourceFile?.fileEntry
        val line = entry?.getLineNumber(diagnosticOffset.coerceAtLeast(0))?.plus(1) ?: 1
        val column = entry?.getColumnNumber(diagnosticOffset.coerceAtLeast(0))?.plus(1) ?: 1
        messages.report(CompilerMessageSeverity.ERROR, "Koral: $reason", CompilerMessageLocation.create(source.path, line, column, null))
        throw RejectedSource()
    }

    fun isU64(type: IrType) = type.classFqName?.asString() == "kotlin.ULong" && !type.isNullable()

    fun trusted(
        annotation: IrConstructorCall,
        name: String,
    ): Boolean =
        annotation.symbol.owner.parentAsClass.fqNameWhenAvailable
            ?.asString() == name &&
            annotation.symbol.owner.fileOrNull
                ?.fileEntry
                ?.name
                ?.let { File(it).canonicalFile } == api

    fun compile(moduleFragment: IrModuleFragment) {
        val file = moduleFragment.files.singleOrNull { File(it.fileEntry.name).canonicalFile == source } ?: reject("Missing entry source")
        if (source == api ||
            listOf("dev.koral", "kotlin", "java").any {
                file.packageFqName.asString() == it ||
                    file.packageFqName.asString().startsWith("$it.")
            }
        ) {
            reject("Reserved package")
        }
        sourceFile = file
        val classes = file.declarations.filterIsInstance<IrClass>()
        if (classes.size > 1 ||
            file.declarations.any { it !is IrSimpleFunction && it !is IrClass }
        ) {
            reject("Declare instruction functions and at most one account data class")
        }
        readAccount(classes)
        val functions = file.declarations.filterIsInstance<IrSimpleFunction>()
        if (functions.size !in 1..32) reject("Declare 1..32 instruction functions")
        val instructions = functions.map { fn -> at(fn.startOffset) { InstructionLowering(this, fn).compile() } }
        val program = CounterProgram(source.name, instructions, layout)
        try {
            ProgramChecks.validate(program)
        } catch (failure: IllegalArgumentException) {
            reject("Invalid program: ${failure.message ?: "model validation failed"}")
        }
        ProgramCodec.write(program, File(System.getenv("KORAL_IR_OUT")).toPath())
    }

    private fun readAccount(classes: List<IrClass>) {
        accountClass = classes.singleOrNull()
        layout =
            accountClass?.let { klass ->
                at(klass.startOffset) {
                    val annotation =
                        klass.annotations.singleOrNull { trusted(it, "dev.koral.api.Account") }
                            ?: reject("Account class needs the trusted @Account annotation")
                    validateClass(klass)
                    val fields = readFields(klass)
                    val seed =
                        annotation.arguments
                            .filterNotNull()
                            .singleOrNull()
                            ?.let { (it as? IrConst)?.value as? String } ?: reject("Account seed must be a constant string")
                    StateLayout(klass.name.asString(), seed, fields.map { it.name.asString() })
                }
            }
    }

    private fun validateClass(klass: IrClass) {
        val unsupportedForm = !klass.isData || klass.isInner || klass.typeParameters.isNotEmpty()
        val inheritsType = klass.superTypes.any { it.classFqName?.asString() != "kotlin.Any" }
        if (unsupportedForm || inheritsType) reject("Account must be a non-generic data class without inheritance")
    }

    private fun invalidField(field: IrValueParameter) = !isU64(field.type) || field.defaultValue != null || field.varargElementType != null

    private fun invalidProperty(property: IrProperty) = property.isVar || property.isDelegated || property.backingField == null

    private fun readFields(klass: IrClass): List<IrValueParameter> {
        val constructor = klass.primaryConstructor ?: reject("Account needs a primary constructor")
        val fields = constructor.parameters.filter { it.kind == IrParameterKind.Regular }
        if (fields.size !in 1..16 || fields.any(::invalidField)) reject("Account requires 1..16 ULong fields without defaults")
        if (fields.any { parameter ->
                parameter.annotations.any {
                    listOf("AccountOwner", "AccountAddress", "AccountDataSize", "AccountExecutable").any { name ->
                        trusted(it, "dev.koral.api.$name")
                    }
                }
            }
        ) {
            reject("Account constraints require external instruction parameters, not state fields")
        }
        validateMembers(klass, fields)
        return fields
    }

    private fun validateMembers(
        klass: IrClass,
        fields: List<IrValueParameter>,
    ) {
        val properties = klass.declarations.filterIsInstance<IrProperty>()
        if (properties.map { it.name.asString() } != fields.map { it.name.asString() } ||
            properties.any(::invalidProperty)
        ) {
            reject("Account fields must be immutable primary constructor properties")
        }
        if (klass.declarations.any(::unsupportedMember)) {
            reject("Account methods and init blocks are unsupported")
        }
    }

    private fun unsupportedMember(member: org.jetbrains.kotlin.ir.declarations.IrDeclaration): Boolean {
        if (member is IrAnonymousInitializer || member is IrClass) return true
        return member is IrSimpleFunction && member.origin == IrDeclarationOrigin.DEFINED
    }
}
