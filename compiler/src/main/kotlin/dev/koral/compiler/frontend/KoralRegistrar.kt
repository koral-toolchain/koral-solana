package dev.koral.compiler.frontend

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class KoralRegistrar : CompilerPluginRegistrar() {
    override val supportsK2 = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val messages = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        IrGenerationExtension.registerExtension(
            object : IrGenerationExtension {
                override fun generate(
                    moduleFragment: IrModuleFragment,
                    pluginContext: IrPluginContext,
                ) {
                    try {
                        SourceLowering(messages).compile(moduleFragment)
                    } catch (_: RejectedSource) {
                        // The collector already has a positioned error. Let Kotlin fail normally.
                    }
                }
            },
        )
    }
}
