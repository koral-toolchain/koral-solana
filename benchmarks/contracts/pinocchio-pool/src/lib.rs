//! Selected pool instruction, independently compiled through public pinocchio SDK APIs.
//! Seeded state; no initializer or whole-program size comparison.
use pinocchio::{address::Address,entrypoint,error::ProgramError,AccountView,ProgramResult};
entrypoint!(process);
const IX:[u8;8]=[145, 174, 119, 58, 214, 170, 212, 216];
const DISC:[u8;8]=[241, 154, 109, 4, 17, 177, 109, 188];
fn error(code:u32)->ProgramError { ProgramError::Custom(code) }
fn process(program_id:&Address,accounts:&[AccountView],data:&[u8])->ProgramResult {
    if data.len()<24 || data[..8]!=IX { return Err(error(102)); }
    if accounts.len()<2 { return Err(error(3005)); }
    let authority=&accounts[0];let state=&accounts[1];
    if !authority.is_signer() { return Err(error(3010)); }
    if !state.is_writable() { return Err(error(3006)); }
    if !state.owned_by(program_id) { return Err(error(3007)); }
    let (a,b)={
        let bytes=state.try_borrow()?;
        if bytes.len()<57 { return Err(error(3003)); }
        if bytes[..8]!=DISC { return Err(error(3002)); }
        if &bytes[8..40]!=authority.address().as_ref() { return Err(error(6000)); }
        (u64::from_le_bytes(bytes[40..48].try_into().unwrap()),u64::from_le_bytes(bytes[48..56].try_into().unwrap()))
    };
    let x=u64::from_le_bytes(data[8..16].try_into().unwrap());
    let y=u64::from_le_bytes(data[16..24].try_into().unwrap());
    let next_a=a.checked_add(x).ok_or(error(5100))?;
    let next_b=b.checked_add(y).ok_or(error(5100))?;
    let mut bytes=state.try_borrow_mut()?;
    bytes[40..48].copy_from_slice(&next_a.to_le_bytes());bytes[48..56].copy_from_slice(&next_b.to_le_bytes());
    Ok(())
}
