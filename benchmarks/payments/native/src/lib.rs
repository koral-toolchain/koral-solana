//! Independent native Rust baseline for the selected pay instruction only.
//! State is seeded by the harness; initialization and ELF size are not compared.
use solana_program::{account_info::AccountInfo,entrypoint,entrypoint::ProgramResult,
    instruction::{AccountMeta,Instruction},program::invoke,program_error::ProgramError,pubkey::Pubkey};
entrypoint!(process);
const PAY: [u8;8] = [119, 18, 216, 65, 192, 117, 122, 220];
const DISC: [u8;8] = [108, 22, 191, 124, 9, 73, 22, 238];
const SIZE: usize = 57;
fn custom(code: u32) -> ProgramError { ProgramError::Custom(code) }

const SYSTEM: Pubkey=Pubkey::new_from_array([0;32]);
fn process(program_id: &Pubkey, accounts: &[AccountInfo], data: &[u8]) -> ProgramResult {
    if data.len()<16 || data[..8]!=PAY { return Err(custom(102)); }
    let amount=u64::from_le_bytes(data[8..16].try_into().unwrap());
    let [authority,state,recipient,..]=accounts else { return Err(custom(3005)); };
    if !recipient.is_writable { return Err(custom(3006)); }
    if !authority.is_signer { return Err(custom(3010)); }
    if !state.is_writable { return Err(custom(3006)); }
    if state.owner!=program_id { return Err(custom(3007)); }
    let paid={
        let bytes=state.try_borrow_data()?;
        if bytes.len()<SIZE { return Err(custom(3003)); }
        if bytes[..8]!=DISC { return Err(custom(3002)); }
        if bytes[8..40]!=authority.key.to_bytes() { return Err(custom(6000)); }
        u64::from_le_bytes(bytes[40..48].try_into().unwrap())
    };
    let system=accounts.get(3).ok_or(custom(3005))?;
    if system.key!=&SYSTEM || !system.executable { return Err(custom(3008)); }
    if !authority.is_writable { return Err(custom(3006)); }
    let mut transfer=vec![0u8;12];transfer[0]=2;
    transfer[4..12].copy_from_slice(&amount.to_le_bytes());
    let instruction=Instruction {program_id:SYSTEM,
        accounts:vec![AccountMeta::new(*authority.key,true),AccountMeta::new(*recipient.key,false)],data:transfer};
    invoke(&instruction,&[authority.clone(),recipient.clone()])?;
    let next=paid.checked_add(amount).ok_or(custom(5100))?;
    let observed=recipient.lamports();
    let mut bytes=state.try_borrow_mut_data()?;
    bytes[40..48].copy_from_slice(&next.to_le_bytes());
    bytes[48..56].copy_from_slice(&observed.to_le_bytes());
    Ok(())
}
