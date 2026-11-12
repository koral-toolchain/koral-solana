package dev.koral.compiler.metadata

import dev.koral.compiler.checks.ProgramChecks
import dev.koral.compiler.model.*
import java.security.MessageDigest

/** Preview client metadata, not an Anchor IDL or a stable published schema. */
object ProgramAbi {
    private fun hasTransfer(body: List<Statement>): Boolean = body.any {
        it is Statement.TransferLamports || (it is Statement.Branch && (hasTransfer(it.yes) || hasTransfer(it.no)))
    }
    private fun discriminator(name: String)=MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    private fun account(index:Int,name:String,signer:Boolean,writable:Boolean,address:String?=null)=linkedMapOf<String,Any?>(
        "index" to index,"name" to name,"signer" to signer,"writable" to writable,"address" to address
    )
    fun encode(program: CounterProgram): String {
        ProgramChecks.validate(program)
        val layout=program.account
        val fields=layout?.fields ?: listOf("count")
        fun data(names:List<String>)=names.mapIndexed { i,name -> linkedMapOf("name" to name,"type" to "u64","offset" to 8+i*8) }
        val system="11111111111111111111111111111111"
        val instructions=program.instructions.map { instruction ->
            val cpi=hasTransfer(instruction.body)
            val accounts=mutableListOf(account(0,"authority",true,cpi),account(1,"state",false,true))
            instruction.accounts.forEachIndexed { i,a -> accounts+=account(i+2,a.name,false,a.writable) }
            if(cpi)accounts+=account(2+instruction.accounts.size,"system_program",false,false,system)
            linkedMapOf("name" to instruction.name,"discriminatorHex" to discriminator("global:${instruction.name}"),
                "data" to data(instruction.arguments),"accounts" to accounts,"systemTransferFromAuthority" to cpi)
        }
        val value=linkedMapOf(
            "schema" to "koral-preview-abi/1","target" to "sbpf-v0",
            "state" to linkedMapOf("type" to (layout?.name ?: "Counter"),"pdaSeed" to (layout?.seed ?: "counter"),
                "authoritySeed" to "authority public key","discriminatorHex" to discriminator("account:${layout?.name ?: "Counter"}"),
                "bytes" to 41+8*fields.size,"authorityOffset" to 8,"bumpOffset" to 40+8*fields.size,
                "fields" to fields.mapIndexed { i,name -> linkedMapOf("name" to name,"type" to "u64","offset" to 40+i*8) }),
            "initialize" to linkedMapOf("discriminatorHex" to discriminator("global:initialize"),
                "accounts" to listOf(account(0,"authority",true,true),account(1,"state",false,true),account(2,"system_program",false,false,system)),
                "optionalAllOrNoneFields" to (layout!=null),"zeroDefault" to true,"data" to data(if(layout!=null)fields else emptyList())),
            "instructions" to instructions
        )
        return json(value)+"\n"
    }
    private fun json(value:Any?):String=when(value) {
        null -> "null"
        is String -> "\""+buildString { for(c in value) append(when(c) { '\\' -> "\\\\"; '"' -> "\\\""; '\n' -> "\\n"; '\r' -> "\\r"; '\t' -> "\\t"; else -> if(c.code<32)"\\u%04x".format(c.code) else c.toString() }) }+"\""
        is Number,is Boolean -> value.toString()
        is List<*> -> value.joinToString(",","[","]") { json(it) }
        is Map<*,*> -> value.entries.joinToString(",","{","}") { json(it.key as String)+":"+json(it.value) }
        else -> error("Unsupported metadata value")
    }
}
