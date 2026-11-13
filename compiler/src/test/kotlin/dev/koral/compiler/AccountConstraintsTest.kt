package dev.koral.compiler

import dev.koral.compiler.model.*
import dev.koral.compiler.checks.ProgramChecks
import dev.koral.compiler.metadata.ProgramAbi
import dev.koral.compiler.backend.c.CounterC
import java.io.DataOutputStream
import java.nio.file.Files
import kotlin.test.*

class AccountConstraintsTest {
    private val system = "11111111111111111111111111111111"
    private val recipient = "GyGKxMyg1p9SsHfm15MkNUu1u9TN2JtTspcdmrtGUdse"
    private fun program(account: AccountParameter) = CounterProgram("test.kt", listOf(
        CounterInstruction("observe", emptyList(), listOf(Statement.Return(Expression.Variable("count"))), listOf(account))
    ))
    @Test fun base58PreservesZeroAndLeadingZeros() {
        assertContentEquals(ByteArray(32), PublicKey.decode(system))
        assertContentEquals(ByteArray(32).also { it[31]=1 }, PublicKey.decode("1".repeat(31)+"2"))
        assertEquals(32, PublicKey.decode(recipient).size)
    }
    @Test fun rejectsInvalidAlphabetAndNon32ByteKeys() {
        for (key in listOf("", "1".repeat(31), "1".repeat(33), "1".repeat(31)+"0", "z".repeat(44), "1".repeat(45))) {
            assertFailsWith<IllegalArgumentException>(key) { PublicKey.decode(key) }
        }
    }
    @Test fun constraintsSurviveCodecAndReachAbiAndBackend() {
        val source=program(AccountParameter("target",true,system,recipient))
        val file=Files.createTempFile("koral-constraints-", ".ir")
        try { ProgramCodec.write(source,file);assertEquals(source,ProgramCodec.read(file)) }
        finally { Files.delete(file) }
        val abi=ProgramAbi.encode(source)
        assertContains(abi,"\"owner\":\"$system\"")
        assertContains(abi,"\"address\":\"$recipient\"")
        val c=CounterC().compile(source,"@KORAL_VALIDATE@")
        assertContains(c,"account_owner(p.accounts[2])")
        assertContains(c,"account_key(p.accounts[2])")
        assertContains(c,"return 3007;")
        assertContains(c,"return 3011;")
    }
    @Test fun modelRejectsBadConstraintsBeforeCodeEmission() {
        assertFailsWith<IllegalArgumentException> { ProgramChecks.validate(program(AccountParameter("to",false,owner="bad"))) }
        assertFailsWith<IllegalArgumentException> { CounterC().compile(program(AccountParameter("to",false,address="bad")),"@KORAL_VALIDATE@") }
    }
    @Test fun legacyModelsRemainReadableWithoutConstraints() {
        val file=Files.createTempFile("koral-legacy-", ".ir")
        try {
            DataOutputStream(Files.newOutputStream(file)).use { out ->
                out.writeInt(0x4b4f5234);out.writeUTF("legacy.kt");out.writeBoolean(false)
                out.writeInt(1);out.writeUTF("observe");out.writeInt(0)
                out.writeInt(1);out.writeUTF("to");out.writeBoolean(false)
                out.writeInt(1);out.writeByte(2);out.writeByte(2);out.writeUTF("count")
            }
            assertEquals(listOf(AccountParameter("to",false)),ProgramCodec.read(file).instructions.single().accounts)
        } finally { Files.delete(file) }
    }
}
