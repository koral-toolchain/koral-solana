import {type Mut,type Pubkey,type Signer,type u8,type u64,require} from "typesol";
export interface Quota { authority: Pubkey; used: u64; limit: u64; bump: u8; }
export enum Err { Unauthorized = "Wrong authority", ZeroAmount = "Zero amount", LimitExceeded = "Limit exceeded" }
export function consume(authority: Signer, state: Mut<Quota>, amount: u64): void {
    require(state.authority === authority.key, Err.Unauthorized);
    require(amount > 0n, Err.ZeroAmount);
    const next = state.used + amount;
    require(next <= state.limit, Err.LimitExceeded);
    state.used = next;
}
