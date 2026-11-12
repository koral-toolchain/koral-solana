#include <solana_sdk.h>
#include "koral.h"

/* The input is the Solana loader's trusted serialized ABI, not arbitrary user bytes. */
#define INLINE static inline __attribute__((always_inline))
#define MAX_ACCOUNTS 32
#define STATE_SIZE (41+8*@FIELD_COUNT@)
static const uint8_t STATE_SEED[]={@SEED_BYTES@};
#define INVALID_DATA 102ULL
#define OVERFLOW 5100ULL
extern uint64_t sol_get_rent_sysvar(void *);
/* Canonical address derivation uses the public Solana SDK syscall. */
static inline uint64_t counter_pda(const SolPubkey *authority,const SolPubkey *program,SolPubkey *out,uint8_t *bump) {
    const SolSignerSeed seeds[2]={{STATE_SEED,sizeof(STATE_SEED)},{(const uint8_t *)authority,32}};
    return sol_try_find_program_address(seeds,2,program,out,bump);
}

typedef struct { uint8_t *accounts[MAX_ACCOUNTS]; uint64_t count; const uint8_t *ix; uint64_t ix_len; const SolPubkey *id; } Input;
INLINE bool equal32(const void *a, const void *b) {
    const uint64_t *x = a, *y = b;
    return ((x[0]^y[0]) | (x[1]^y[1]) | (x[2]^y[2]) | (x[3]^y[3])) == 0;
}
INLINE uint64_t load64(const void *p) { uint64_t v; __builtin_memcpy(&v,p,8); return v; }
INLINE void store64(void *p,uint64_t v) { __builtin_memcpy(p,&v,8); }
INLINE uint64_t account_len(uint8_t *a) { return *(uint64_t *)(a+80); }
INLINE uint8_t *account_data(uint8_t *a) { return a+88; }
INLINE SolPubkey *account_key(uint8_t *a) { return (SolPubkey *)(a+8); }
INLINE SolPubkey *account_owner(uint8_t *a) { return (SolPubkey *)(a+40); }
INLINE bool parse(const uint8_t *raw, Input *p) {
    uint64_t n=load64(raw); if(n>MAX_ACCOUNTS) return false;
    const uint8_t *cursor=raw+8;
    for(uint64_t i=0;i<n;i++) {
        if(cursor[0]==255) {
            p->accounts[i]=(uint8_t *)cursor;
            cursor=(const uint8_t *)(((uint64_t)(cursor+88+load64(cursor+80)+10240)+7)&~7ULL)+8;
        } else {
            if(cursor[0]>=i) return false;
            p->accounts[i]=p->accounts[cursor[0]]; cursor+=8;
        }
    }
    p->count=n; p->ix_len=load64(cursor); p->ix=cursor+8; p->id=(const SolPubkey *)(p->ix+p->ix_len);
    return true;
}
INLINE SolAccountInfo info(uint8_t *a) {
    return (SolAccountInfo){.key=account_key(a),.lamports=(uint64_t *)(a+72),.data_len=account_len(a),.data=account_data(a),.owner=account_owner(a),.rent_epoch=0,.is_signer=a[1],.is_writable=a[2],.executable=a[3]};
}

/* CPI privileges come from the outer instruction. No PDA signing is added here. */
INLINE uint64_t koral_transfer_lamports(Input *p,uint8_t *to,uint64_t amount,uint64_t program_index) {
    if(p->count<=program_index) return 3005;
    uint8_t *from=p->accounts[0],*system=p->accounts[program_index];
    const SolPubkey zero={0};
    if(!equal32(account_key(system),&zero) || !system[3]) return 3008;
    if(!from[1]) return 3010;
    if(!from[2] || !to[2]) return 3006;
    uint8_t data[12]={2,0,0,0};store64(data+4,amount);
    SolAccountMeta metas[2]={{account_key(from),true,true},{account_key(to),true,false}};
    SolInstruction ix={(SolPubkey *)&zero,metas,2,data,12};
    SolAccountInfo infos[2]={info(from),info(to)};
    return sol_invoke(&ix,infos,2);
}

static __attribute__((noinline)) uint64_t initialize(Input *p) {
    @INIT_ARGUMENT_CHECK@
    if(p->count<3) return 3005;
    uint8_t *a=p->accounts[0],*c=p->accounts[1],*system=p->accounts[2];
    if(!a[1]) return 3010;
    if(!a[2] || !c[2]) return 3006;
    if(a==c) return 2040;
    const SolPubkey zero={0};
    if(!equal32(account_key(system),&zero) || !system[3]) return 3008;
    SolSignerSeed seeds[3]={{(uint8_t *)STATE_SEED,sizeof(STATE_SEED)},{(uint8_t *)account_key(a),32},{0,1}};
    SolPubkey expected; uint8_t bump;
    uint64_t status=counter_pda(account_key(a),p->id,&expected,&bump); if(status) return status;
    if(!equal32(account_key(c),&expected)) return 2006;
    struct {uint64_t per_year; double threshold; uint8_t burn;} rent={0};
    status=sol_get_rent_sysvar(&rent); if(status) return status;
    uint64_t min_balance;
    /* Fast path for the current sysvar; use the actual sysvar, never a fixed lamport amount. */
    if(load64(&rent.threshold)==0x4000000000000000ULL) {
        if(rent.per_year>UINT64_MAX/(2*(STATE_SIZE+128))) return OVERFLOW;
        min_balance=rent.per_year*(2*(STATE_SIZE+128));
    } else {
        double balance=(double)(STATE_SIZE+128)*(double)rent.per_year*rent.threshold;
        if(!(balance>=0.0) || balance>=18446744073709551616.0) return OVERFLOW;
        min_balance=(uint64_t)balance;
    }
    uint8_t data[52]={0}; store64(data+4,min_balance);store64(data+12,STATE_SIZE);
    __builtin_memcpy(data+20,p->id,32);
    SolAccountMeta metas[2]={{account_key(a),true,true},{account_key(c),true,true}};
    SolInstruction ix={(SolPubkey *)&zero,metas,2,data,52};
    SolAccountInfo infos[2]={info(a),info(c)};
    seeds[2].addr=&bump; const SolSignerSeeds signers={seeds,3};
    status=sol_invoke_signed(&ix,infos,2,&signers,1);if(status) return status;
    uint8_t *state=account_data(c);
    store64(state,@ACCOUNT_DISC@ULL);
    __builtin_memcpy(state+8,account_key(a),32);@INIT_STATE_WRITE@ state[STATE_SIZE-1]=bump;
    return SUCCESS;
}

@KORAL_FUNCTION@

extern uint64_t entrypoint(const uint8_t *raw) {
    Input p;
    if(!parse(raw,&p) || p.ix_len<8) return INVALID_DATA;
    uint64_t selector=load64(p.ix);
    if(selector==@INIT_DISC@ULL) return initialize(&p);
    @KORAL_VALIDATE@
    if(p.count<2) return 3005;
    uint8_t *authority=p.accounts[0], *counter=p.accounts[1];
    if(!authority[1]) return 3010;
    if(!counter[2]) return 3006;
    if(!equal32(account_owner(counter),p.id)) return 3007;
    if(account_len(counter)<STATE_SIZE) return 3003;
    uint8_t *state=account_data(counter);
    if(load64(state)!=@ACCOUNT_DISC@ULL) return 3002;
    if(!equal32(state+8,account_key(authority))) return 6000;
    @KORAL_LOAD@
    uint64_t status=INVALID_DATA;
    @KORAL_DISPATCH@
    if(status) return status;
    @KORAL_STORE@
    return SUCCESS;
}
