import {type AccountInfo,type Mut,type Pubkey,type Signer,type SystemProgram,type u8,type u64,findProgramAddress,findProgramAddressBump,minimumBalance,programId,require} from "typesol";
export interface VaultState { authority: Pubkey; deposited: u64; withdrawn: u64; bump: u8; }
export enum VaultError { NotAuthority = "Wrong authority", NotCanonical = "Wrong vault PDA or bump", ZeroAmount = "Amount must be positive", InsufficientFunds = "Rent floor or insufficient funds", Alias = "Vault cannot receive its own withdrawal" }
export function deposit(authority: Mut<Signer>, state: Mut<VaultState>, systemProgram: SystemProgram, amount: u64): void {
    require(state.authority === authority.key, VaultError.NotAuthority);
    require(amount > 0n, VaultError.ZeroAmount);
    require(state.key === findProgramAddress(["vault", authority.key], programId()), VaultError.NotCanonical);
    require(state.bump === findProgramAddressBump(["vault", authority.key], programId()), VaultError.NotCanonical);
    systemProgram.transfer(authority, state, amount);
    state.deposited += amount;
}
export function withdraw(authority: Signer, state: Mut<VaultState>, recipient: Mut<AccountInfo>, amount: u64): void {
    require(state.authority === authority.key, VaultError.NotAuthority);
    require(amount > 0n, VaultError.ZeroAmount);
    require(state.key !== recipient.key, VaultError.Alias);
    require(state.key === findProgramAddress(["vault", authority.key], programId()), VaultError.NotCanonical);
    require(state.bump === findProgramAddressBump(["vault", authority.key], programId()), VaultError.NotCanonical);
    const floor = minimumBalance(state);
    require(state.lamports >= floor, VaultError.InsufficientFunds);
    require(amount <= state.lamports - floor, VaultError.InsufficientFunds);
    state.lamports -= amount;
    recipient.lamports += amount;
    state.withdrawn += amount;
}
