//! Independent Pinocchio selected-update baseline; state is seeded by the harness.
use pinocchio::{address::Address,cpi::invoke,entrypoint,error::ProgramError,
    instruction::{InstructionAccount,InstructionView},sysvars::{rent::Rent,Sysvar},AccountView,ProgramResult};
entrypoint!(process);
const DEPOSIT:[u8;8]=[242,35,198,137,82,225,242,182];
const WITHDRAW:[u8;8]=[183,18,70,156,148,109,161,34];
const DISC:[u8;8]=[228,196,82,165,98,210,235,152];
const SYSTEM:Address=Address::new_from_array([0;32]);
fn custom(c:u32)->ProgramError{ProgramError::Custom(c)}
fn process(id:&Address,accounts:&[AccountView],data:&[u8])->ProgramResult{
    if data.len()<16{return Err(custom(102));}
    let deposit=data[..8]==DEPOSIT;
    if !deposit && data[..8]!=WITHDRAW{return Err(custom(102));}
    if accounts.len()<if deposit{2}else{3}{return Err(custom(3005));}
    if !deposit && !accounts[2].is_writable(){return Err(custom(3006));}
    let authority=&accounts[0];let state=&accounts[1];
    if !authority.is_signer(){return Err(custom(3010));}
    if !state.is_writable(){return Err(custom(3006));}
    if !state.owned_by(id){return Err(custom(3007));}
    let (incoming,outgoing,bump)={
        let b=state.try_borrow()?;
        if b.len()<57{return Err(custom(3003));}
        if b[..8]!=DISC{return Err(custom(3002));}
        if &b[8..40]!=authority.address().as_ref(){return Err(custom(6000));}
        (u64::from_le_bytes(b[40..48].try_into().unwrap()),u64::from_le_bytes(b[48..56].try_into().unwrap()),b[56])
    };
    let amount=u64::from_le_bytes(data[8..16].try_into().unwrap());
    if amount==0{return Err(custom(6100));}
    if !deposit && state.address()==accounts[2].address(){return Err(custom(2040));}
    let (expected,canonical)=Address::try_find_program_address(&[b"vault",authority.address().as_ref()],id).ok_or(custom(2006))?;
    if &expected!=state.address() || canonical!=bump{return Err(custom(2006));}
    if deposit{
        let system=accounts.get(2).ok_or(custom(3005))?;
        if system.address()!=&SYSTEM || !system.executable(){return Err(custom(3008));}
        if !authority.is_writable(){return Err(custom(3006));}
        let mut bytes=[0u8;12];bytes[0]=2;bytes[4..12].copy_from_slice(&amount.to_le_bytes());
        let metas=[InstructionAccount::writable_signer(authority.address()),InstructionAccount::writable(state.address())];
        invoke(&InstructionView{program_id:&SYSTEM,data:&bytes,accounts:&metas},&[authority,state])?;
        let next=incoming.checked_add(amount).ok_or(custom(5100))?;
        state.try_borrow_mut()?[40..48].copy_from_slice(&next.to_le_bytes());
    }else{
        let to=&accounts[2];let floor=Rent::get()?.try_minimum_balance(state.data_len())?;
        let available=state.lamports();
        if amount>available || available-amount<floor{return Err(custom(3014));}
        let credited=to.lamports().checked_add(amount).ok_or(custom(5100))?;
        state.set_lamports(available-amount);to.set_lamports(credited);
        let next=outgoing.checked_add(amount).ok_or(custom(5100))?;
        state.try_borrow_mut()?[48..56].copy_from_slice(&next.to_le_bytes());
    }
    Ok(())
}
