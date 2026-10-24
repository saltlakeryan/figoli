const $ = (selector, root=document) => root.querySelector(selector);
const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
const params = new URLSearchParams(location.search);
const storeId = Number(params.get('storeId'));
const listId = Number(params.get('listId'));
const state = {aisles:[],list:null,view:params.get('view')==='map'?'map':'items',scale:1,x:0,y:0,pending:new Set()};
const viewport = $('#shopping-viewport');
const canvas = $('#shopping-canvas');
const preview = $('#shopping-preview');
let previewTimer = 0;
let longPressTimer = 0;
let suppressEntryClick = null;

function shoppingUrl(view=state.view){const url=new URL(location.href);url.searchParams.set('storeId',storeId);url.searchParams.set('listId',listId);if(view==='map')url.searchParams.set('view','map');else url.searchParams.delete('view');return `${url.pathname}${url.search}`}
function editItemUrl(entry){const url=new URL('/market/',location.origin);url.searchParams.set('storeId',storeId);url.searchParams.set('panel','items');url.searchParams.set('editItemId',entry.itemId);url.searchParams.set('returnTo',location.pathname+location.search);return `${url.pathname}${url.search}`}
function api(path,options={}){return request(`/api${path}`,options)}
async function request(path,options={}){
  const method=(options.method||'GET').toUpperCase();
  const headers={'Content-Type':'application/json',...(options.headers||{})};
  if(!['GET','HEAD','OPTIONS'].includes(method)){
    let csrf=document.cookie.split('; ').find(row=>row.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=');
    if(!csrf){await fetch('/api/auth/csrf',{credentials:'same-origin'});csrf=document.cookie.split('; ').find(row=>row.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=')}
    if(csrf)headers['X-XSRF-TOKEN']=decodeURIComponent(csrf);
  }
  const response=await fetch(path,{credentials:'same-origin',...options,headers});
  if(response.status===401){location.href=`/login?next=${encodeURIComponent(location.pathname+location.search)}`;throw new Error('Please sign in again.')}
  if(!response.ok){let message=`Request failed (${response.status})`;try{const data=await response.json();message=data.message||data.detail||data.title||message}catch{}throw new Error(message)}
  if(response.status===204)return null;
  return response.json();
}
function showNotice(message){const notice=$('#shopping-notice');notice.textContent=message;notice.hidden=false;clearTimeout(showNotice.timer);showNotice.timer=setTimeout(()=>notice.hidden=true,6000)}
function orderedEntries(){const aisleOrder=new Map([...state.aisles].sort((a,b)=>a.y-b.y||a.x-b.x||a.id-b.id).map((aisle,index)=>[aisle.id,index]));return [...state.list.entries].sort((a,b)=>(aisleOrder.get(a.aisleId)??Number.MAX_SAFE_INTEGER)-(aisleOrder.get(b.aisleId)??Number.MAX_SAFE_INTEGER)||(a.aislePercent??101)-(b.aislePercent??101)||a.name.localeCompare(b.name))}
function itemNumberMap(entries){return new Map(entries.map((entry,index)=>[entry.id,index+1]))}
function renderIndex(entries,numbers){
  const aisles=[...state.aisles].sort((a,b)=>a.y-b.y||a.x-b.x||a.id-b.id);
  const rowByY=new Map([...new Set(aisles.map(aisle=>aisle.y))].sort((a,b)=>a-b).map((y,index)=>[y,index+1]));
  const groups=aisles.map(aisle=>{const items=entries.filter(entry=>entry.aisleId===aisle.id);if(!items.length)return '';const span=aisle.orientation==='horizontal'?aisle.length:1;return `<section class="shopping-index-group" style="grid-column:${aisle.x} / span ${span};grid-row:${rowByY.get(aisle.y)}"><h2>${esc(aisle.name)}</h2>${items.map(entry=>`<div class="shopping-index-item"><button type="button" class="shopping-index-number ${entry.checked?'checked':''}" data-entry-toggle="${entry.id}" aria-label="${entry.checked?'Uncheck':'Check off'} ${esc(entry.name)}" aria-pressed="${entry.checked}">${numbers.get(entry.id)}</button><div class="shopping-index-copy ${entry.checked?'checked':''}">${esc(entry.name)}<small>× ${esc(entry.quantity)}${entry.aislePercent==null?'':` · ${entry.aislePercent}%`}</small></div><a class="shopping-index-edit" href="${editItemUrl(entry)}" target="_blank" rel="noopener" aria-label="Edit aisle for ${esc(entry.name)}">Edit aisle</a></div>`).join('')}</section>`}).join('');
  const unassigned=entries.filter(entry=>entry.aisleId==null);const lastRow=Math.max(0,...rowByY.values());
  const extra=unassigned.length?`<section class="shopping-index-group shopping-unassigned" style="grid-column:1 / -1;grid-row:${lastRow+1}"><h2>Aisle not assigned</h2>${unassigned.map(entry=>`<div class="shopping-index-item"><button type="button" class="shopping-index-number ${entry.checked?'checked':''}" data-entry-toggle="${entry.id}" aria-label="${entry.checked?'Uncheck':'Check off'} ${esc(entry.name)}" aria-pressed="${entry.checked}">${numbers.get(entry.id)}</button><div class="shopping-index-copy ${entry.checked?'checked':''}">${esc(entry.name)}<small>× ${esc(entry.quantity)}</small></div><a class="shopping-index-edit" href="${editItemUrl(entry)}" target="_blank" rel="noopener" aria-label="Edit aisle for ${esc(entry.name)}">Edit aisle</a></div>`).join('')}</section>`:'';
  const columns=Math.max(1,...aisles.map(aisle=>aisle.x+(aisle.orientation==='horizontal'?aisle.length-1:0)));
  return `<div class="shopping-index-grid" style="--index-columns:${columns}">${groups}${extra}</div>`;
}
function renderMap(entries,numbers){
  const columns=Math.max(8,...state.aisles.map(aisle=>aisle.x+(aisle.orientation==='horizontal'?aisle.length-1:0)));
  const rows=Math.max(6,...state.aisles.map(aisle=>aisle.y+(aisle.orientation==='vertical'?aisle.length-1:0)));
  const cells=Array.from({length:columns*rows},(_,index)=>`<div class="shopping-map-cell" style="grid-column:${index%columns+1};grid-row:${Math.floor(index/columns)+1}"></div>`).join('');
  const aisles=[...state.aisles].sort((a,b)=>a.y-b.y||a.x-b.x||a.id-b.id).map(aisle=>{
    const items=entries.filter(entry=>entry.aisleId===aisle.id);const colSpan=aisle.orientation==='horizontal'?aisle.length:1;const rowSpan=aisle.orientation==='vertical'?aisle.length:1;
    const pins=items.map((entry,index)=>{const percent=FigoMapPosition.percentForEntry(entry,index,items.length);const position=FigoMapPosition.markerStyle(aisle.orientation,percent);return `<div class="shopping-map-marker" style="left:${position.left};top:${position.top};transform:${position.transform}"><button type="button" class="shopping-map-number ${entry.checked?'checked':''}" data-entry-toggle="${entry.id}" aria-pressed="${entry.checked}" aria-label="${entry.checked?'Uncheck':'Check off'} item ${numbers.get(entry.id)}, ${esc(entry.name)}" title="${esc(entry.name)}">${numbers.get(entry.id)}</button><div class="shopping-map-tooltip" role="tooltip"><span>${esc(entry.name)}${entry.quantity&&entry.quantity!=='1'?` · × ${esc(entry.quantity)}`:''}</span><a href="${editItemUrl(entry)}" target="_blank" rel="noopener">Edit aisle ↗</a></div></div>`}).join('');
    return `<section class="shopping-aisle ${aisle.orientation==='horizontal'?'horizontal':''}" style="grid-column:${aisle.x} / span ${colSpan};grid-row:${aisle.y} / span ${rowSpan}"><strong>${esc(aisle.name)}</strong>${pins}</section>`;
  }).join('');
  const unassigned=entries.filter(entry=>entry.aisleId==null);
  const unassignedMarkup=unassigned.length?`<section class="shopping-unassigned-map"><h2>Aisle not assigned</h2>${unassigned.map(entry=>`<div class="shopping-map-marker"><button type="button" class="shopping-map-number ${entry.checked?'checked':''}" data-entry-toggle="${entry.id}" aria-pressed="${entry.checked}" aria-label="${entry.checked?'Uncheck':'Check off'} ${esc(entry.name)}" title="${esc(entry.name)}">${numbers.get(entry.id)}</button><div class="shopping-map-tooltip" role="tooltip"><span>${esc(entry.name)}</span><a href="${editItemUrl(entry)}" target="_blank" rel="noopener">Edit aisle ↗</a></div></div>`).join('')}</section>`:'';
  return `<div class="shopping-map-canvas" style="--grid-cols:${columns};--grid-rows:${rows}">${cells}${aisles}</div>${unassignedMarkup}`;
}
function render(fit=true){
  const entries=orderedEntries();const numbers=itemNumberMap(entries);const done=entries.filter(entry=>entry.checked).length;
  $('#shopping-list-name').textContent=state.list.name;$('#shopping-progress').textContent=`${done} of ${entries.length} gathered`;
  $('#shopping-view-toggle').textContent=state.view==='map'?'All list items':'Map view';
  $('#shopping-view-toggle').setAttribute('aria-label',state.view==='map'?'Show all list items':'Show store map');
  canvas.innerHTML=state.view==='map'?renderMap(entries,numbers):renderIndex(entries,numbers);
  if(fit)requestAnimationFrame(fitView);else updateTransform();
}
function updateTransform(){canvas.style.transform=`translate(${state.x}px,${state.y}px) scale(${state.scale})`}
function fitView(){if(!canvas.firstElementChild)return;const width=canvas.scrollWidth,height=canvas.scrollHeight;const scale=Math.max(.25,Math.min(1,(viewport.clientWidth-32)/width,(viewport.clientHeight-32)/height));state.scale=scale;state.x=(viewport.clientWidth-width*scale)/2;state.y=(viewport.clientHeight-height*scale)/2;updateTransform()}
function zoomAt(clientX,clientY,next){const bounds=viewport.getBoundingClientRect();const px=clientX-bounds.left,py=clientY-bounds.top;const scale=Math.max(.25,Math.min(4,next));const ratio=scale/state.scale;state.x=px-(px-state.x)*ratio;state.y=py-(py-state.y)*ratio;state.scale=scale;updateTransform()}
function updatePreview(entry){preview.replaceChildren();if(entry){const name=document.createElement('span');name.textContent=`${entry.name}${entry.quantity&&entry.quantity!=='1'?` · × ${entry.quantity}`:''}`;const link=document.createElement('a');link.href=editItemUrl(entry);link.target='_blank';link.rel='noopener';link.textContent='Edit aisle';preview.append(name,link)}preview.hidden=!entry;clearTimeout(previewTimer);if(entry)previewTimer=setTimeout(()=>preview.hidden=true,5000)}
function entryForButton(button){return state.list.entries.find(entry=>entry.id===Number(button.dataset.entryToggle))}
async function toggleEntry(id){const entry=state.list.entries.find(item=>item.id===id);if(!entry||state.pending.has(id))return;const checked=!entry.checked;entry.checked=checked;state.pending.add(id);render(false);try{const saved=await api(`/stores/${storeId}/entries/${id}`,{method:'PATCH',body:JSON.stringify({checked})});entry.checked=saved.checked;render(false)}catch(error){entry.checked=!checked;render(false);showNotice(`Couldn't save “${entry.name}”: ${error.message}`)}finally{state.pending.delete(id)}}
function setView(view){state.view=view;const url=shoppingUrl(view);history.replaceState(null,'',url);render()}

$('#shopping-view-toggle').addEventListener('click',()=>setView(state.view==='map'?'items':'map'));
document.querySelectorAll('[data-zoom]').forEach(button=>button.addEventListener('click',()=>{if(button.dataset.zoom==='reset'){fitView();return}const bounds=viewport.getBoundingClientRect();zoomAt(bounds.left+bounds.width/2,bounds.top+bounds.height/2,state.scale*(button.dataset.zoom==='in'?1.25:.8))}));
viewport.addEventListener('wheel',event=>{event.preventDefault();zoomAt(event.clientX,event.clientY,state.scale*(event.deltaY<0?1.1:.9))},{passive:false});
const pointers=new Map();let drag=null,pinch=null;
viewport.addEventListener('pointerdown',event=>{if(event.target.closest('button,a'))return;event.preventDefault();viewport.setPointerCapture(event.pointerId);pointers.set(event.pointerId,{x:event.clientX,y:event.clientY});if(pointers.size===1){drag={id:event.pointerId,startX:event.clientX,startY:event.clientY,originX:state.x,originY:state.y};viewport.classList.add('dragging')}else if(pointers.size===2){const [a,b]=[...pointers.values()];const mid={x:(a.x+b.x)/2,y:(a.y+b.y)/2};const bounds=viewport.getBoundingClientRect();pinch={distance:Math.hypot(a.x-b.x,a.y-b.y),scale:state.scale,canvasX:(mid.x-bounds.left-state.x)/state.scale,canvasY:(mid.y-bounds.top-state.y)/state.scale};drag=null}});
viewport.addEventListener('pointermove',event=>{if(!pointers.has(event.pointerId))return;pointers.set(event.pointerId,{x:event.clientX,y:event.clientY});if(pointers.size===1&&drag){state.x=drag.originX+event.clientX-drag.startX;state.y=drag.originY+event.clientY-drag.startY;updateTransform()}else if(pointers.size>=2&&pinch){const [a,b]=[...pointers.values()];const mid={x:(a.x+b.x)/2,y:(a.y+b.y)/2};const bounds=viewport.getBoundingClientRect();state.scale=Math.max(.25,Math.min(4,pinch.scale*Math.hypot(a.x-b.x,a.y-b.y)/pinch.distance));state.x=mid.x-bounds.left-pinch.canvasX*state.scale;state.y=mid.y-bounds.top-pinch.canvasY*state.scale;updateTransform()}});
function endPan(event){pointers.delete(event.pointerId);if(pointers.size<2)pinch=null;if(pointers.size===1){const [id,point]=[...pointers.entries()][0];drag={id,startX:point.x,startY:point.y,originX:state.x,originY:state.y}}else drag=null;if(!pointers.size)viewport.classList.remove('dragging')}
viewport.addEventListener('pointerup',endPan);viewport.addEventListener('pointercancel',endPan);viewport.addEventListener('lostpointercapture',endPan);
viewport.addEventListener('click',event=>{const button=event.target.closest('[data-entry-toggle]');if(!button)return;const id=Number(button.dataset.entryToggle);if(suppressEntryClick===id){suppressEntryClick=null;event.preventDefault();return}toggleEntry(id)});
viewport.addEventListener('pointerover',event=>{if(event.pointerType!=='mouse')return;const marker=event.target.closest('.shopping-map-marker');if(marker)marker.classList.add('previewing')});
viewport.addEventListener('pointerout',event=>{if(event.pointerType!=='mouse'||event.relatedTarget?.closest?.('.shopping-map-marker'))return;event.target.closest('.shopping-map-marker')?.classList.remove('previewing')});
viewport.addEventListener('focusin',event=>{const button=event.target.closest('.shopping-map-number');if(button)updatePreview(entryForButton(button))});
viewport.addEventListener('focusout',event=>{if(event.target.closest('.shopping-map-number'))preview.hidden=true});
viewport.addEventListener('pointerdown',event=>{const button=event.target.closest('.shopping-map-number');if(event.pointerType!=='touch'||!button)return;const id=Number(button.dataset.entryToggle);clearTimeout(longPressTimer);longPressTimer=setTimeout(()=>{updatePreview(entryForButton(button));suppressEntryClick=id},500)});
viewport.addEventListener('contextmenu',event=>{if(event.target.closest('.shopping-map-number'))event.preventDefault()});
for(const eventName of ['pointerup','pointercancel'])viewport.addEventListener(eventName,()=>{clearTimeout(longPressTimer);if(suppressEntryClick!==null){const id=suppressEntryClick;setTimeout(()=>{if(suppressEntryClick===id)suppressEntryClick=null},1200)}});

async function load(){
  if(!Number.isInteger(storeId)||storeId<1||!Number.isInteger(listId)||listId<1){canvas.innerHTML='<div class="shopping-empty">Choose a shopping list first. <a href="/market/">Back to Market Day</a></div>';return}
  $('#shopping-back').href=`/market/?storeId=${storeId}&listId=${listId}`;
  canvas.innerHTML='<div class="shopping-empty">Loading your list…</div>';
  try{const [aisles,list]=await Promise.all([api(`/stores/${storeId}/aisles`),api(`/stores/${storeId}/lists/${listId}`)]);state.aisles=aisles;state.list=list;render()}
  catch(error){canvas.innerHTML=`<div class="shopping-empty">${esc(error.message)} <a href="/market/">Back to Market Day</a></div>`}
}
load();
