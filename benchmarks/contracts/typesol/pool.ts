import {type Mut,type Pubkey,type Signer,type u8,type u64,require} from "typesol";
export interface Pool { authority: Pubkey; reserve_a: u64; reserve_b: u64; bump: u8; }
export enum Err { Unauthorized = "Wrong authority" }
export function add_reserves(authority: Signer, state: Mut<Pool>, amount_a: u64, amount_b: u64): void {
    require(state.authority === authority.key, Err.Unauthorized);
    state.reserve_a += amount_a;
    state.reserve_b += amount_b;
}
