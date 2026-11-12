import {type AccountInfo,type Mut,type Pubkey,type Signer,type SystemProgram,type u8,type u64,require} from "typesol";
export interface PaymentState { authority: Pubkey; paid: u64; observed: u64; bump: u8; }
export enum PaymentError { NotAuthority = "Wrong authority" }
export function pay(authority: Mut<Signer>, state: Mut<PaymentState>, recipient: Mut<AccountInfo>, systemProgram: SystemProgram, amount: u64): void {
    require(state.authority === authority.key, PaymentError.NotAuthority);
    systemProgram.transfer(authority, recipient, amount);
    state.paid += amount;
    state.observed = recipient.lamports;
}
