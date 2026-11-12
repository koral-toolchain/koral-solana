import {type AccountInfo,type Mut,type Pubkey,type Signer,type SystemProgram,type u8,type u64,require} from "typesol";
export interface SplitState { authority: Pubkey; sent: u64; observed: u64; bump: u8; }
export enum Err { Unauthorized = "Wrong authority" }
export function distribute(authority: Mut<Signer>, state: Mut<SplitState>, first: Mut<AccountInfo>, second: Mut<AccountInfo>, systemProgram: SystemProgram, amount_a: u64, amount_b: u64): void {
    require(state.authority === authority.key, Err.Unauthorized);
    systemProgram.transfer(authority, first, amount_a);
    systemProgram.transfer(authority, second, amount_b);
    state.sent += amount_a + amount_b;
    state.observed = first.lamports + second.lamports;
}
