//! Selected split instruction, independently compiled through public pinocchio SDK APIs.
//! Seeded state; no initializer or whole-program size comparison.
use pinocchio::{address::Address,entrypoint,error::ProgramError,AccountView,ProgramResult};
use pinocchio::{cpi::invoke,instruction::{InstructionAccount,InstructionView}};
entrypoint!(process);
const IX:[u8;8]=[191, 44, 223, 207, 164, 236, 126, 61];
const DISC:[u8;8]=[155, 39, 248, 17, 118, 112, 30, 58];
fn error(code:u32)->ProgramError { ProgramError::Custom(code) }
fn process(program_id:&Address,accounts:&[AccountView],data:&[u8])->ProgramResult {
    if data.len()<24 || data[..8]!=IX { return Err(error(102)); }
    if accounts.len()<4 { return Err(error(3005)); }
    if !accounts[2].is_writable() || !accounts[3].is_writable() { return Err(error(3006)); }
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
    let system=accounts.get(4).ok_or(error(3005))?;
    let system_id=Address::new_from_array([0;32]);
    if system.address()!=&system_id || !system.executable() { return Err(error(3008)); }
    if !authority.is_writable() { return Err(error(3006)); }
    for (to,amount) in [(&accounts[2],x),(&accounts[3],y)] {
        let mut payload=[0u8;12];payload[0]=2;payload[4..12].copy_from_slice(&amount.to_le_bytes());
        let metas=[InstructionAccount::writable_signer(authority.address()),InstructionAccount::writable(to.address())];
        invoke(&InstructionView{program_id:&system_id,data:&payload,accounts:&metas},&[authority,to])?;
    }
    let next_b=accounts[2].lamports().checked_add(accounts[3].lamports()).ok_or(error(5100))?;
    let next_a=a.checked_add(x.checked_add(y).ok_or(error(5100))?).ok_or(error(5100))?;
    let mut bytes=state.try_borrow_mut()?;
    bytes[40..48].copy_from_slice(&next_a.to_le_bytes());bytes[48..56].copy_from_slice(&next_b.to_le_bytes());
    Ok(())
}
