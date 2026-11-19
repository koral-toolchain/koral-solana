import assert from 'node:assert/strict';
import {writeFileSync} from 'node:fs';
const {createLocalProgram}=await import(process.env.KORAL_TEST_CLIENT);
const p=await createLocalProgram({binary:process.env.KORAL_TEST_BINARY,programAddress:'EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN'});
const owner=await p.actor(1),other=await p.actor(2),large=await p.actor(4),bad=await p.actor(5);
let assertions=0;const rows=[];
const eq=(a,b,message)=>{assert.deepEqual(a,b,message);assertions++;};
const invariant=s=>eq(s.received,s.available+s.reserved+s.sold,'inventory conservation');
eq((await p.invoke('initialize',{authority:owner})).success,true);
eq(await p.readState(owner),{received:0n,available:0n,reserved:0n,sold:0n});
const originalBalance=p.balance(owner);
for(const [name,quantity,expected] of [
 ['receive',100n,[100n,100n,0n,0n]],['reserve',30n,[100n,70n,30n,0n]],
 ['cancel',10n,[100n,80n,20n,0n]],['dispatch',15n,[100n,80n,5n,15n]],
 ['restock_return',5n,[100n,85n,5n,10n]],['reserve',85n,[100n,0n,90n,10n]],
 ['dispatch',90n,[100n,0n,0n,100n]],['receive',10n,[110n,10n,0n,100n]]
]) {
 const r=await p.invoke(name,{authority:owner,args:{quantity}});eq(r.success,true,r.error);
 const state=await p.readState(owner);eq(Object.values(state),expected,'independent expected transition');invariant(state);
 eq(p.balance(owner),originalBalance,'no owner funds transfer');
 eq(r.logs.some(v=>v.includes('Program 11111111111111111111111111111111 invoke')),false,'no System transfer on stock operations');
 rows.push({name,quantity:String(quantity),success:r.success,state});
}
for(const [name,quantity,code] of [['receive',0n,7101],['reserve',11n,7102],['cancel',1n,7103],['dispatch',1n,7103],['restock_return',101n,7104]]) {
 const before=await p.readState(owner);const r=await p.invoke(name,{authority:owner,args:{quantity}});
 eq(r.success,false);eq(r.errorCode,code);eq(await p.readState(owner),before,'failed operation preserves all fields');
 rows.push({name,quantity:String(quantity),success:false,errorCode:r.errorCode});
}
const before=await p.readState(owner);
const unauthorized=await p.invoke('receive',{authority:other,state:await p.stateAddress(owner),args:{quantity:1n}});
eq(unauthorized.success,false);eq(unauthorized.errorCode,6000);eq(await p.readState(owner),before);
const max=(1n<<64n)-1n;
eq((await p.invoke('initialize',{authority:large,args:{received:max,available:max,reserved:0n,sold:0n}})).success,true);
const overflow=await p.invoke('receive',{authority:large,args:{quantity:1n}});eq(overflow.success,false);eq(overflow.errorCode,5100);
eq(await p.readState(large),{received:max,available:max,reserved:0n,sold:0n});
// Probe a real unmet application requirement, separately from the passing core scope.
const untrusted=await p.invoke('initialize',{authority:bad,args:{received:10n,available:999n,reserved:0n,sold:0n}});
const finding={requirement:'reject invalid initial inventory conservation on chain',met:!untrusted.success,
 observedInitializationAccepted:untrusted.success,scope:'initial field payloads currently follow the generic ABI, not Kotlin instruction guards'};
if(untrusted.success) {
 finding.actualState=await p.readState(bad);
 const guarded=await p.invoke('receive',{authority:bad,args:{quantity:1n}});
 eq(guarded.success,false);eq(guarded.errorCode,7100,'operation guard still rejects invalid existing state');
 finding.laterOperationRejected=guarded.errorCode===7100;
}
const result={assertions,coreScope:'owner-operated aggregate stock from zero initialization; no per-reservation IDs',corePassed:true,rows,findings:[finding],network:'Local LiteSVM',onChain:false};
if(process.env.KORAL_INVENTORY_RESULT)writeFileSync(process.env.KORAL_INVENTORY_RESULT,JSON.stringify(result,(_,v)=>typeof v==='bigint'?String(v):v,2)+'\n');
console.log(JSON.stringify({assertions,corePassed:true,unmetRequirements:result.findings.filter(v=>!v.met).map(v=>v.requirement)}));
