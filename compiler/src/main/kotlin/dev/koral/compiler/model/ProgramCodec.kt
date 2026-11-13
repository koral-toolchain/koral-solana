package dev.koral.compiler.model

import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path

/** A bounded data-only format; it does not deserialize Java objects or load classes. */
object ProgramCodec {
    private const val MAGIC = 0x4b4f5235
    fun write(program: CounterProgram, path: Path) {
        DataOutputStream(Files.newOutputStream(path)).use { out ->
            out.writeInt(MAGIC); out.writeUTF(program.source)
            out.writeBoolean(program.account!=null)
            program.account?.let { layout ->
                out.writeUTF(layout.name);out.writeUTF(layout.seed);out.writeInt(layout.fields.size)
                layout.fields.forEach(out::writeUTF)
            }
            out.writeInt(program.instructions.size)
            for(instruction in program.instructions) {
                out.writeUTF(instruction.name);out.writeInt(instruction.arguments.size)
                instruction.arguments.forEach(out::writeUTF)
                out.writeInt(instruction.accounts.size)
                instruction.accounts.forEach {
                    out.writeUTF(it.name);out.writeBoolean(it.writable)
                    out.writeBoolean(it.owner!=null);it.owner?.let(out::writeUTF)
                    out.writeBoolean(it.address!=null);it.address?.let(out::writeUTF)
                }
                writeStatements(out,instruction.body)
            }
        }
    }
    fun read(path: Path): CounterProgram {
        require(Files.size(path) <= 1_048_576) { "Compiler model is too large" }
        DataInputStream(Files.newInputStream(path)).use { input ->
            val format = input.readInt()
            require(format == MAGIC || format == 0x4b4f5234) { "Unsupported compiler model" }
            val source=input.readUTF()
            val layout=if(input.readBoolean()) {
                val name=input.readUTF();val seed=input.readUTF();val size=input.readInt()
                require(size in 1..16) { "Expected 1..16 state fields" }
                StateLayout(name,seed,List(size) { input.readUTF() })
            } else null
            val count=input.readInt();require(count in 1..32) { "Expected 1..32 instructions" }
            val instructions=List(count) {
                val name=input.readUTF();val size=input.readInt()
                require(size in 0..8) { "Too many instruction arguments" }
                val arguments=List(size) { input.readUTF() }
                val accountCount=input.readInt();require(accountCount in 0..8) { "Too many external accounts" }
                val accounts=List(accountCount) {
                    val accountName=input.readUTF();val writable=input.readBoolean()
                    val owner=if(format==MAGIC && input.readBoolean())input.readUTF() else null
                    val address=if(format==MAGIC && input.readBoolean())input.readUTF() else null
                    AccountParameter(accountName,writable,owner,address)
                }
                CounterInstruction(name,arguments,readStatements(input,0),accounts)
            }
            val program=CounterProgram(source,instructions,layout)
            require(input.read() == -1) { "Unexpected trailing compiler model data" }
            return program
        }
    }
    private fun writeExpression(out: DataOutputStream, e: Expression) {
        when(e) {
            is Expression.Number -> { out.writeByte(0); out.writeLong(e.value.toLong()) }
            is Expression.BooleanValue -> { out.writeByte(1); out.writeBoolean(e.value) }
            is Expression.Variable -> { out.writeByte(2); out.writeUTF(e.name) }
            is Expression.Binary -> { out.writeByte(3); out.writeUTF(e.operation.name); writeExpression(out,e.left); writeExpression(out,e.right) }
            is Expression.Choice -> { out.writeByte(4); writeExpression(out,e.condition); writeExpression(out,e.yes); writeExpression(out,e.no) }
            is Expression.Not -> { out.writeByte(5); writeExpression(out,e.value) }
            is Expression.Construct -> { out.writeByte(6);out.writeInt(e.values.size);e.values.forEach { writeExpression(out,it) } }
            is Expression.Field -> { out.writeByte(7);writeExpression(out,e.value);out.writeUTF(e.name) }
            is Expression.AccountLamports -> { out.writeByte(8);writeExpression(out,e.account) }
        }
    }
    private fun readExpression(input: DataInputStream, depth: Int): Expression {
        require(depth < 128) { "Expression nesting is too deep" }
        fun next() = readExpression(input,depth+1)
        return when(input.readUnsignedByte()) {
            0 -> Expression.Number(input.readLong().toULong())
            1 -> Expression.BooleanValue(input.readBoolean())
            2 -> Expression.Variable(input.readUTF())
            3 -> Expression.Binary(Operation.valueOf(input.readUTF()),next(),next())
            4 -> Expression.Choice(next(),next(),next())
            5 -> Expression.Not(next())
            6 -> { val size=input.readInt();require(size in 1..16);Expression.Construct(List(size) { next() }) }
            7 -> Expression.Field(next(),input.readUTF())
            8 -> Expression.AccountLamports(next())
            else -> error("Unknown expression in compiler model")
        }
    }
    private fun writeStatements(out: DataOutputStream, statements: List<Statement>) {
        out.writeInt(statements.size)
        for(s in statements) when(s) {
            is Statement.Bind -> { out.writeByte(0); out.writeUTF(s.name); out.writeUTF(s.type.name); out.writeBoolean(s.mutable); writeExpression(out,s.value) }
            is Statement.Assign -> { out.writeByte(1); out.writeUTF(s.name); writeExpression(out,s.value) }
            is Statement.Return -> { out.writeByte(2); writeExpression(out,s.value) }
            is Statement.Branch -> { out.writeByte(3); writeExpression(out,s.condition); writeStatements(out,s.yes); writeStatements(out,s.no) }
            is Statement.Require -> { out.writeByte(4); writeExpression(out,s.condition); out.writeInt(s.code.toInt()) }
            is Statement.TransferLamports -> { out.writeByte(5);writeExpression(out,s.to);writeExpression(out,s.amount) }
        }
    }
    private fun readStatements(input: DataInputStream, depth: Int): List<Statement> {
        require(depth < 128) { "Statement nesting is too deep" }
        val size=input.readInt();require(size in 0..10000) { "Too many statements" }
        return List(size) {
            when(input.readUnsignedByte()) {
                0 -> Statement.Bind(input.readUTF(),ValueType.valueOf(input.readUTF()),input.readBoolean(),readExpression(input,0))
                1 -> Statement.Assign(input.readUTF(),readExpression(input,0))
                2 -> Statement.Return(readExpression(input,0))
                3 -> Statement.Branch(readExpression(input,0),readStatements(input,depth+1),readStatements(input,depth+1))
                4 -> Statement.Require(readExpression(input,0),input.readInt().toUInt())
                5 -> Statement.TransferLamports(readExpression(input,0),readExpression(input,0))
                else -> error("Unknown statement in compiler model")
            }
        }
    }
}
