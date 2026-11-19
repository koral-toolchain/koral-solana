// Your assertions define this contract. Run with koral test after koral setup/build.
import assert from 'node:assert/strict';
const {createLocalProgram}=await import(process.env.KORAL_TEST_CLIENT);
const program=await createLocalProgram({binary:process.env.KORAL_TEST_BINARY,
 programAddress:'EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN'});
const buyer=await program.actor(1),recipient=await program.actor(3),other=await program.actor(2);
assert.equal((await program.invoke('initialize',{authority:buyer})).success,true);
const initial=program.balance(recipient);
const paid=await program.invoke('purchase',{authority:buyer,args:{quantity:2n},accounts:{recipient}});
assert.equal(paid.success,true,paid.error);
assert.equal(program.balance(recipient)-initial,20_000_000n,'exact independent price expectation');
assert.deepEqual(await program.readState(buyer),{units:2n,paid:20_000_000n});
for(const [quantity,code] of [[0n,7001],[6n,7002]]) {
 const result=await program.invoke('purchase',{authority:buyer,args:{quantity},accounts:{recipient}});
 assert.equal(result.success,false);assert.equal(result.errorCode,code);
}
const wrong=await program.invoke('purchase',{authority:buyer,args:{quantity:1n},accounts:{recipient:other}});
assert.equal(wrong.success,false);assert.equal(wrong.errorCode,3011);
const before={buyer:program.balance(buyer),recipient:program.balance(recipient),state:await program.readState(buyer)};
const rejected=await program.invoke('purchase_then_fail',{authority:buyer,args:{quantity:1n},accounts:{recipient}});
assert.equal(rejected.success,false);assert.equal(rejected.errorCode,7003);
assert.ok(rejected.logs.some(line=>line.includes('Program 11111111111111111111111111111111 invoke')),'real CPI before rollback');
assert.deepEqual({buyer:program.balance(buyer),recipient:program.balance(recipient),state:await program.readState(buyer)},before);
console.log('Custom contract: exact payment, state, quantity/address rejection and post-CPI rollback passed.');
