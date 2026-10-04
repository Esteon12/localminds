const $ = s => document.querySelector(s);
const pending = new Map();

function nativeAvailable(){ return typeof LocalMind !== 'undefined'; }
function refreshStatus(){
  if(!nativeAvailable()){ $('#status').textContent='Android bridge bulunamadı.'; return; }
  try{
    const s=JSON.parse(LocalMind.status());
    $('#dot').classList.toggle('on',s.modelReady);
    $('#status').textContent=s.modelReady?'Model hazır. Her şey cihaz üzerinde çalışıyor.':(s.modelExists?'Model yükleniyor…':'Başlamak için bir GGUF model seç.');
    $('#modelBtn').textContent=s.modelExists?'MODELİ DEĞİŞTİR':'GGUF MODEL SEÇ';
  }catch(e){ $('#status').textContent=e.message; }
}
function pickModel(){ LocalMind.pickModel(); }
function setPrompt(v){ $('#prompt').value=v; $('#prompt').focus(); }
function addBubble(text,cls){ const d=document.createElement('div'); d.className='bubble '+cls; d.textContent=text; $('#chat').appendChild(d); d.scrollIntoView({behavior:'smooth',block:'end'}); return d; }
function send(){
  const p=$('#prompt').value.trim(); if(!p)return;
  addBubble(p,'user'); $('#prompt').value='';
  const bubble=addBubble('Düşünüyor…','ai');
  const id=LocalMind.chat(p); pending.set(id,bubble);
}
window.onNativeMessage=function(raw){
  const m=JSON.parse(raw); const p=m.payload||{};
  if(m.type==='chat'){
    const b=pending.get(p.id); if(b){ b.textContent=p.error||p.text||''; if(p.error)b.classList.add('error'); pending.delete(p.id); }
  } else if(m.type==='model'){ refreshStatus(); }
  else if(m.type==='error'){ addBubble(p.message||'Hata','ai error'); }
};
function openSystem(){ $('#systemModal').classList.add('show'); $('#sys').textContent=LocalMind.status(); }
function closeSystem(){ $('#systemModal').classList.remove('show'); }
function showSource(){
  const js=LocalMind.readRuntime('app.js');
  addBubble('runtime/app.js\n\n'+js.slice(0,5000),'ai');
}
function rollback(){ const ok=LocalMind.rollback(); addBubble(ok?'Son runtime sürümüne geri dönüldü.':'Geri alınacak snapshot yok.','ai'); }

// Programmatic self-edit API. The local model or a future self-developer screen can call this.
window.applySelfUpdate=function(files){
  const result=JSON.parse(LocalMind.applyPatch(JSON.stringify({files})));
  if(result.ok){ LocalMind.reload(); }
  return result;
};

refreshStatus();
