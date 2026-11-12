//! Selected quota instruction, independently compiled through public native SDK APIs.
//! Seeded state; no initializer or whole-program size comparison.
use solana_program::{account_info::AccountInfo,entrypoint,entrypoint::ProgramResult,program_error::ProgramError,pubkey::Pubkey};
entrypoint!(process);
const IX:[u8;8]=[65, 178, 141, 13, 95, 57, 76, 154];
const DISC:[u8;8]=[141, 43, 105, 117, 251, 232, 160, 249];
fn error(code:u32)->ProgramError { ProgramError::Custom(code) }
fn process(program_id:&Pubkey,accounts:&[AccountInfo],data:&[u8])->ProgramResult {
    if data.len()<16 || data[..8]!=IX { return Err(error(102)); }
    if accounts.len()<2 { return Err(error(3005)); }
    let authority=&accounts[0];let state=&accounts[1];
    if !authority.is_signer { return Err(error(3010)); }
    if !state.is_writable { return Err(error(3006)); }
    if state.owner!=program_id { return Err(error(3007)); }
    let (a,b)={
        let bytes=state.try_borrow_data()?;
        if bytes.len()<57 { return Err(error(3003)); }
        if bytes[..8]!=DISC { return Err(error(3002)); }
        if &bytes[8..40]!=authority.key.as_ref() { return Err(error(6000)); }
        (u64::from_le_bytes(bytes[40..48].try_into().unwrap()),u64::from_le_bytes(bytes[48..56].try_into().unwrap()))
    };
    let x=u64::from_le_bytes(data[8..16].try_into().unwrap());
    if x==0 { return Err(error(6001)); }
    let next_a=a.checked_add(x).ok_or(error(5100))?;
    if next_a>b { return Err(error(6002)); }
    let next_b=b;
    let mut bytes=state.try_borrow_mut_data()?;
    bytes[40..48].copy_from_slice(&next_a.to_le_bytes());bytes[48..56].copy_from_slice(&next_b.to_le_bytes());
    Ok(())
}
