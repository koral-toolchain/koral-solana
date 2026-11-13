package dev.koral.compiler

import dev.koral.compiler.model.*
import dev.koral.compiler.checks.ProgramChecks
import dev.koral.compiler.metadata.ProgramAbi
import dev.koral.compiler.backend.c.CounterC
import java.nio.file.Files
import java.io.DataOutputStream
import kotlin.test.*

class VaultTest {
    private fun program(body: List<Statement>, writable: Boolean=true, owned: Boolean=true) = CounterProgram("vault.kt",listOf(
        CounterInstruction("move_funds",listOf("amount"),body,listOf(AccountParameter("to",writable)))
    ),if(owned)StateLayout("VaultState","vault",listOf("balance")) else null)
    private val amount=Expression.Variable("amount")
    private val to=Expression.Variable("to")
    private val returned=Statement.Return(Expression.Variable("state"))
    @Test fun vaultOperationsRoundTripAndAdvertiseCorrectCpiPermissions() {
        val source=program(listOf(Statement.DepositLamports(amount),Statement.WithdrawLamports(to,amount),returned))
        ProgramChecks.validate(source)
        val file=Files.createTempFile("koral-vault-", ".ir")
        try { ProgramCodec.write(source,file);assertEquals(source,ProgramCodec.read(file)) }
        finally { Files.delete(file) }
        val abi=ProgramAbi.encode(source)
        assertContains(abi,"\"vaultDeposit\":true");assertContains(abi,"\"vaultWithdrawal\":true")
        assertContains(abi,"\"systemTransferFromAuthority\":true")
        val c=CounterC().compile(source,"@KORAL_FUNCTION@")
        assertContains(c,"koral_deposit_lamports");assertContains(c,"koral_withdraw_lamports")
    }
    @Test fun withdrawalAloneAndNestedBranchesDoNotRequireSystemCpi() {
        val source=program(listOf(Statement.Branch(Expression.BooleanValue(true),listOf(Statement.WithdrawLamports(to,amount)),emptyList()),returned))
        val abi=ProgramAbi.encode(source)
        assertContains(abi,"\"vaultWithdrawal\":true");assertContains(abi,"\"systemTransferFromAuthority\":false")
        assertFalse(abi.contains("vaultDeposit"))
    }
    @Test fun vaultOperationsRejectLegacyStateReadonlyRecipientsAndBadAmounts() {
        assertFailsWith<IllegalArgumentException> { ProgramChecks.validate(program(listOf(Statement.DepositLamports(amount),returned),owned=false)) }
        assertFailsWith<IllegalArgumentException> { ProgramChecks.validate(program(listOf(Statement.WithdrawLamports(to,amount),returned),writable=false)) }
        assertFailsWith<IllegalArgumentException> { ProgramChecks.validate(program(listOf(Statement.DepositLamports(Expression.BooleanValue(false)),returned))) }
    }
    @Test fun kor6MetadataModelsRemainReadable() {
        val file=Files.createTempFile("koral-kor6-", ".ir")
        try {
            DataOutputStream(Files.newOutputStream(file)).use { out ->
                out.writeInt(0x4b4f5236);out.writeUTF("legacy.kt");out.writeBoolean(false)
                out.writeInt(1);out.writeUTF("observe");out.writeInt(0)
                out.writeInt(1);out.writeUTF("to");out.writeBoolean(false)
                out.writeBoolean(false);out.writeBoolean(false)
                out.writeBoolean(true);out.writeInt(0);out.writeBoolean(true);out.writeBoolean(false)
                out.writeInt(1);out.writeByte(2);out.writeByte(2);out.writeUTF("count")
            }
            assertEquals(AccountParameter("to",false,dataSize=0,executable=false),ProgramCodec.read(file).instructions.single().accounts.single())
        } finally { Files.delete(file) }
    }
    @Test fun legacyFormatCannotSmuggleNewVaultStatements() {
        val file=Files.createTempFile("koral-old-node-", ".ir")
        try {
            DataOutputStream(Files.newOutputStream(file)).use { out ->
                out.writeInt(0x4b4f5236);out.writeUTF("legacy.kt");out.writeBoolean(false)
                out.writeInt(1);out.writeUTF("deposit");out.writeInt(0);out.writeInt(0)
                out.writeInt(1);out.writeByte(6);out.writeByte(0);out.writeLong(1)
            }
            assertFailsWith<IllegalArgumentException> { ProgramCodec.read(file) }
        } finally { Files.delete(file) }
    }

}
