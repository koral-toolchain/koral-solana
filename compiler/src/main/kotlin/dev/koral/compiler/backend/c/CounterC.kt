package dev.koral.compiler.backend.c

import dev.koral.compiler.checks.ProgramChecks
import dev.koral.compiler.model.*
import java.security.MessageDigest

/** Emits C from Koral's data model. No other language compiler is called here. */
class CounterC {
    private val out=StringBuilder()
    private val names=mutableMapOf("count" to "count","by" to "by")
    private var layout: StateLayout?=null
    private val types=mutableMapOf<String,ProgramChecks.Binding>()
    private fun cType(type: ValueType)=when(type) { ValueType.U64 -> "uint64_t";ValueType.BOOL -> "bool";ValueType.STATE -> "KoralState";ValueType.ACCOUNT_READONLY,ValueType.ACCOUNT_WRITABLE -> "uint8_t *" }
    private var systemIndex=2
    private var index=0
    private var level=1
    private fun line(s: String) { out.append("    ".repeat(level)).appendLine(s) }
    private fun cCondition(value: String) = if(value.startsWith("(") && value.endsWith(")")) value else "($value)"
    private fun temp()="koral_value_${index++}"
    private fun expression(e: Expression): String = when(e) {
        is Expression.AccountLamports -> "load64(${expression(e.account)}+72)"
        is Expression.Construct -> {
            val result=temp();line("KoralState $result;")
            e.values.forEachIndexed { i,value -> val field=expression(value);line("$result.field_$i = $field;") }
            result
        }
        is Expression.Field -> "(${expression(e.value)}).field_${requireNotNull(layout).fields.indexOf(e.name)}"
        is Expression.Number -> "${e.value}ULL"
        is Expression.BooleanValue -> e.value.toString()
        is Expression.Variable -> names.getValue(e.name)
        is Expression.Not -> "(!${expression(e.value)})"
        is Expression.Choice -> {
            val condition=expression(e.condition);val result=temp()
            // The initial instruction's numeric choices have U64 results; Boolean choices also fit.
            line("${cType(ProgramChecks.type(e,types,layout))} $result;");line("if ${cCondition(condition)} {");level++
            val yes=expression(e.yes);line("$result = $yes;");level--;line("} else {");level++
            val no=expression(e.no);line("$result = $no;");level--;line("}");result
        }
        is Expression.Binary -> {
            val left=expression(e.left)
            when(e.operation) {
                Operation.ADD,Operation.SUBTRACT,Operation.MULTIPLY -> {
                    val right=expression(e.right);val value=temp()
                    val operation=mapOf(Operation.ADD to "add",Operation.SUBTRACT to "sub",Operation.MULTIPLY to "mul").getValue(e.operation)
                    line("uint64_t $value;")
                    line("if (__builtin_${operation}_overflow($left, $right, &$value)) return 5100ULL;")
                    value
                }
                Operation.AND,Operation.OR -> {
                    val value=temp();line("bool $value = $left;")
                    line("if (${if(e.operation==Operation.OR)"!" else ""}$value) {");level++
                    val right=expression(e.right);line("$value = $right;");level--;line("}");value
                }
                else -> {
                    val right=expression(e.right)
                    val op=mapOf(Operation.EQ to "==",Operation.NE to "!=",Operation.LT to "<",Operation.LE to "<=",Operation.GT to ">",Operation.GE to ">=").getValue(e.operation)
                    "($left $op $right)"
                }
            }
        }
    }
    private fun statements(list: List<Statement>) {
        for(s in list) when(s) {
            is Statement.Bind -> {
                val value=expression(s.value);val name=temp();names[s.name]=name
                types[s.name]=ProgramChecks.Binding(s.type,s.mutable)
                line("${cType(s.type)} $name = $value;")
            }
            is Statement.Assign -> { val value=expression(s.value);line("${names.getValue(s.name)} = $value;") }
            is Statement.TransferLamports -> {
                val to=expression(s.to);val amount=expression(s.amount);val status=temp()
                line("uint64_t $status = koral_transfer_lamports(input, $to, $amount, $systemIndex);")
                line("if ($status) return $status;")
            }
            is Statement.Return -> { val value=expression(s.value);line("*out_count = $value;");line("return 0;") }
            is Statement.Require -> { val value=expression(s.condition);line("if (!($value)) return ${s.code}ULL;") }
            is Statement.Branch -> {
                val condition=expression(s.condition);line("if ${cCondition(condition)} {");level++
                val before=names.toMap();val beforeTypes=types.toMap();statements(s.yes);names.clear();names.putAll(before);types.clear();types.putAll(beforeTypes)
                level--;line("} else {");level++;statements(s.no);names.clear();names.putAll(before);types.clear();types.putAll(beforeTypes);level--;line("}")
            }
        }
    }
    fun compile(program: CounterProgram, runtime: String): String {
        ProgramChecks.validate(program)
        layout=program.account
        val stateType=if(layout==null)"uint64_t" else "KoralState"
        val stateName=if(layout==null)"count" else "state"
        val fieldCount=layout?.fields?.size ?: 1
        val declaration=if(layout==null)"" else "typedef struct { "+layout!!.fields.indices.joinToString(" ") { "uint64_t field_$it;" }+" } KoralState;\n"
        val functions=program.instructions.mapIndexed { instructionIndex,instruction ->
            out.clear();names.clear();names[stateName]=stateName;types.clear()
            types[stateName]=ProgramChecks.Binding(if(layout==null)ValueType.U64 else ValueType.STATE,false)
            index=0;level=1;systemIndex=2+instruction.accounts.size
            instruction.arguments.forEachIndexed { i,name -> names[name]="arg_$i";types[name]=ProgramChecks.Binding(ValueType.U64,false) }
            instruction.accounts.forEachIndexed { i,account -> names[account.name]="account_$i";types[account.name]=ProgramChecks.Binding(if(account.writable)ValueType.ACCOUNT_WRITABLE else ValueType.ACCOUNT_READONLY,false) }
            val parameters=(listOf("$stateType $stateName")+instruction.arguments.indices.map { "uint64_t arg_$it" }+instruction.accounts.indices.map { "uint8_t *account_$it" }+listOf("Input *input","$stateType *out_count")).joinToString(", ")
            out.appendLine("static inline __attribute__((always_inline)) uint64_t koral_instruction_$instructionIndex($parameters) {")
            statements(instruction.body);out.appendLine("}")
            out.toString()
        }.joinToString("\n")
        val allDiscriminators=listOf(discriminator("global:initialize"))+program.instructions.map { discriminator("global:${it.name}") }
        require(allDiscriminators.distinct().size==allDiscriminators.size) { "Instruction discriminator collision" }
        val validation=program.instructions.joinToString(" else ") { instruction ->
            val countCheck=if(instruction.accounts.isEmpty())"" else "if(p.count<${2+instruction.accounts.size}) return 3005;"
            val writableChecks=instruction.accounts.mapIndexedNotNull { i,a -> if(a.writable)"if(!p.accounts[${2+i}][2]) return 3006;" else null }.joinToString(" ")
            "if(selector==${discriminator("global:${instruction.name}")}ULL) { if(p.ix_len<${8+instruction.arguments.size*8}) return INVALID_DATA; $countCheck $writableChecks }"
        }+" else return INVALID_DATA;"
        val dispatch=program.instructions.mapIndexed { i,instruction ->
            val values=(listOf(if(layout==null)"load64(state+40)" else "current")+instruction.arguments.indices.map { "load64(p.ix+${8+it*8})" }+instruction.accounts.indices.map { "p.accounts[${2+it}]" }+listOf("&p","&next")).joinToString(",")
            "if(selector==${discriminator("global:${instruction.name}")}ULL) status=koral_instruction_$i($values);"
        }.joinToString(" else ")
        return runtime.replace("@KORAL_FUNCTION@",declaration+functions)
            .replace("@ACCOUNT_DISC@",discriminator("account:${layout?.name ?: "Counter"}"))
            .replace("@INIT_DISC@",discriminator("global:initialize"))
            .replace("@KORAL_VALIDATE@",validation)
            .replace("@KORAL_DISPATCH@",dispatch)
            .replace("@FIELD_COUNT@",fieldCount.toString())
            .replace("@SEED_BYTES@",(layout?.seed ?: "counter").toByteArray().joinToString(",") { "${it.toInt() and 255}" })
            .replace("@INIT_ARGUMENT_CHECK@",if(layout==null)"" else "if(p->ix_len!=8 && p->ix_len<${8+fieldCount*8}) return INVALID_DATA;")
            .replace("@INIT_STATE_WRITE@",(0 until fieldCount).joinToString(" ") { "store64(state+${40+it*8},${if(layout==null)"0" else "(p->ix_len==8?0:load64(p->ix+${8+it*8}))"});" })
            .replace("@KORAL_LOAD@",if(layout==null)"uint64_t next;" else "KoralState current = {"+layout!!.fields.indices.joinToString(",") { ".field_$it=load64(state+${40+it*8})" }+"}; KoralState next;")
            .replace("@KORAL_STORE@",if(layout==null)"store64(state+40,next);" else layout!!.fields.indices.joinToString(" ") { "store64(state+${40+it*8},next.field_$it);" })
    }
    private fun discriminator(value: String): String {
        val bytes=MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return (0..7).fold(0uL){sum,i->sum or ((bytes[i].toInt() and 255).toULong() shl (i*8))}.toString()
    }
}
