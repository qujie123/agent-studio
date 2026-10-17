'use strict';
const chatStyle=document.createElement('link');chatStyle.rel='stylesheet';chatStyle.href='/chat.css';document.head.append(chatStyle);
pages.chats=['对话工作台','CONVERSATIONS','与智能体持续交流，保留上下文与历史。'];
Object.assign(actions,{CHAT_PREFERENCES_UPDATED:'调整会话置顶与归档',CHAT_CREATED:'创建会话',CHAT_DELETED:'删除会话',CHAT_MESSAGE_STARTED:'发送消息',CHAT_MESSAGE_SUCCEEDED:'回复完成',CHAT_MESSAGE_FAILED:'回复失败',CHAT_MESSAGE_RECOVERED:'恢复中断会话'});
const chatState={id:null,owner:null,drafts:new Map(),pending:new Map(),busy:false};
function resetChat(){chatState.id=null;chatState.owner=null;chatState.drafts.clear();chatState.pending.clear();chatState.busy=false;chatState.stream=false;chatState.projectMode=false;chatState.search='';chatState.view='active';chatState.turnPage=0;chatState.turnAnchor=null;}
async function newChat(agentId){const user=state.user.id;const session=await api('/chats',{method:'POST',body:{agentId}});if(state.user?.id!==user)return;chatState.id=session.id;chatState.owner=user;chatState.search='';chatState.view='active';chatState.turnPage=0;chatState.turnAnchor=null;state.pagination.chats=0;$('#modal').close();await navigate('chats');}
async function chatForm(){const content=modal('新建对话'),form=node('form');content.append(form);await agentPicker(form);form.append(node('p','每轮使用智能体当时启用的 Skills。会话只有你本人可见。','muted'));formFooter(form,'开始对话',data=>newChat(Number(data.agentId)));}
async function openChat(id){chatState.id=id;chatState.turnPage=0;chatState.turnAnchor=null;await refresh();}
async function renderChats(data,current){
  const sessions=data.items;
  if(chatState.owner!==state.user.id){resetChat();chatState.owner=state.user.id;}
  if(!chatState.id)chatState.id=sessions[0]?.id||null;
  const id=chatState.id;let detail=null,turnData=null;
  if(id){try{const params={page:chatState.turnPage||0,size:20};if(chatState.turnPage&&chatState.turnAnchor)params.beforeId=chatState.turnAnchor;const [session,turns]=await Promise.all([api('/chats/'+id+'/session'),api(pagePath('/chats/'+id+'/turns',params))]);turnData=turns;detail={session,turns:[...turns.items].reverse()};if(!chatState.turnPage)chatState.turnAnchor=turns.items[0]?.id||null;}catch(error){if(error.status===404&&current()){chatState.id=null;chatState.turnPage=0;return refresh();}throw error;}}
  if(!current()||chatState.id!==id)return;
  const layout=node('div',undefined,'chat-layout'),sidebar=node('section',undefined,'chat-sessions'),panel=node('section',undefined,'chat-panel');
  const search=node('input');search.id='chat-search';search.maxLength=100;search.value=chatState.search||'';search.type='search';search.placeholder='搜索会话';search.setAttribute('aria-label','搜索会话');
  const list=node('div',undefined,'chat-list');
  const drawList=()=>{list.replaceChildren();for(const session of sessions){const entry=button('',()=>openChat(session.id),'chat-session'+(session.id===id?' selected':''));entry.append(node('strong',(session.pinned?'★ ':'')+session.title),node('small',session.agentName+(session.archived?' · 已归档':'')+' · '+date(session.updatedAt)));list.append(entry);}};
  const token=state.token,epoch=state.sessionEpoch;let searchTimer;search.addEventListener('input',()=>{clearTimeout(searchTimer);searchTimer=setTimeout(async()=>{if(!search.isConnected||state.page!=='chats'||state.token!==token||state.sessionEpoch!==epoch)return;try{chatState.search=search.value;state.pagination.chats=0;chatState.id=null;chatState.turnPage=0;chatState.turnAnchor=null;const position=search.selectionStart;await refresh();if(state.page!=='chats'||state.token!==token||state.sessionEpoch!==epoch)return;const replacement=$('#chat-search');if(replacement){replacement.focus();replacement.setSelectionRange(position,position);}}catch(error){showError(error);}},250);});
  const view=select([['active','当前会话'],['pinned','已置顶'],['archived','已归档'],['all','全部会话']],chatState.view||'active',value=>{chatState.view=value;state.pagination.chats=0;chatState.id=null;chatState.turnPage=0;chatState.turnAnchor=null;return refresh();});view.className='chat-view';view.setAttribute('aria-label','会话范围');
  drawList();sidebar.append(button('＋ 新建对话',()=>chatForm(),'primary wide'),view,search,list,pageControls(data,page=>{state.pagination.chats=page;chatState.id=null;chatState.turnPage=0;return refresh();}));layout.append(sidebar,panel);
  if(!detail){panel.append(empty('开始一段持续的交流','选择智能体，提问、追问，历史会保存在这里。',button('新建对话',()=>chatForm(),'primary')));$('#content').replaceChildren(layout);chatState.busy=false;return;}
  const stored=chatState.pending.get(id);if(stored&&!stored.sending&&detail.turns.some(t=>t.requestId===stored.requestId))chatState.pending.delete(id);
  const session=detail.session,pending=chatState.pending.get(id),running=detail.session.running||detail.turns.some(t=>t.status==='RUNNING')||pending?.sending;
  chatState.busy=!!running;
  const header=node('div',undefined,'chat-heading'),titles=node('div'),controls=node('div',undefined,'card-actions');
  titles.append(node('h2',session.title),node('p',session.agentName+' · 私人会话','muted'));
  const preferences=async(pinned,archived)=>{await api('/chats/'+id+'/preferences',{method:'PATCH',body:{pinned,archived}});const view=chatState.view||'active';if(view==='active'&&archived||view==='archived'&&!archived||view==='pinned'&&(!pinned||archived)){chatState.id=null;chatState.turnPage=0;chatState.turnAnchor=null;}toast(archived?'会话已归档，仍可在已归档中查看':'会话设置已更新');await refresh();};
  const archive=button(session.archived?'恢复会话':'归档',()=>preferences(session.pinned,!session.archived));archive.disabled=!!running;
  controls.append(button(session.pinned?'取消置顶':'置顶',()=>preferences(!session.pinned,session.archived)),archive,button('重命名',()=>renameChat(session)),button('导出',()=>exportConversation(id)),button('刷新',()=>refresh()),button('删除',async()=>{if(!confirm('永久删除这段会话和所有消息？此操作不可撤销。'))return;await api('/chats/'+id,{method:'DELETE'});chatState.drafts.delete(id);chatState.pending.delete(id);chatState.id=null;await refresh();},'danger'));
  if(running)controls.lastChild.disabled=true;header.append(titles,controls);panel.append(header);
  panel.append(node('p','每轮使用当前 Skills；最多携带最近 20 轮成功对话，总输入上限 60000 字符（不是 token）。更早的历史仍可查看、导出。','chat-hint'));
  const previous=$('.chat-messages'),messages=node('div',undefined,'chat-messages');messages.dataset.sessionId=String(id);messages.dataset.page=String(chatState.turnPage||0);messages.setAttribute('aria-label','会话消息');
  for(const turn of detail.turns){messages.append(chatBubble('你',turn.input,'user'));const answer=chatBubble(turn.mode==='DEMO'?'智能体 · 模拟回复':'智能体',(pending?.requestId===turn.requestId&&pending.partial)||turn.output||turn.error||'正在生成，请稍候…','assistant',pending?.stream&&pending.sending&&pending.requestId===turn.requestId);answer.dataset.requestId=turn.requestId;
    const meta=node('p',turn.model+' · '+labels[turn.status]+' · 携带 '+turn.contextTurns+' 轮历史','chat-hint');answer.append(meta);if(turn.temperature!=null||turn.maxOutputTokens!=null)answer.append(node('p','温度：'+(turn.temperature??'默认')+' · 最大输出 token：'+(turn.maxOutputTokens??'默认'),'chat-hint'));
    if(turn.error&&turn.output)answer.append(node('p','回复未完成：'+turn.error,'error'));
    if(turn.knowledge)answer.append(knowledgeReferences(turn.knowledge));
    if(turn.skills)answer.append(node('p','Skills：'+turn.skills.replaceAll('\n',' / '),'chat-hint'));
    if(turn.projectMode&&turn.status==='SUCCEEDED'&&turn.output?.includes('```agent-project'))answer.querySelector('.markdown-body')?.replaceChildren(node('p','项目文件已生成，可在下面查看成品并保存完整目录。'));
    attachArtifacts(answer,'/artifacts/chats/'+id+'/turns/'+turn.id,turn.output,turn.status,turn.projectMode).catch(showError);
    if(turn.output)answer.append(button('复制回复',()=>navigator.clipboard.writeText(turn.output).then(()=>toast('已复制')),'link'));
    if(turn.status==='FAILED')answer.append(button('重新发送此消息',()=>{chatState.drafts.set(id,turn.input);return refresh();}));messages.append(answer);
  }
  if(pending?.sending&&!detail.turns.some(t=>t.requestId===pending.requestId)){const answer=chatBubble('智能体',pending.partial||'正在生成，请稍候…','assistant',pending.stream);answer.dataset.requestId=pending.requestId;messages.append(chatBubble('你',pending.input,'user'),answer);}
  if(!detail.turns.length&&!pending)messages.append(node('p','发送第一条消息，开始对话。','muted'));
  panel.append(messages,pageControls(turnData,page=>{chatState.turnPage=page;return refresh();}));
  const form=node('form',undefined,'chat-composer'),label=node('label','消息'),input=node('textarea');input.name='message';input.maxLength=20000;input.required=true;input.placeholder='输入消息，Ctrl + Enter 发送';input.value=chatState.drafts.get(id)||'';input.setAttribute('aria-label','消息');input.addEventListener('input',()=>chatState.drafts.set(id,input.value));label.append(input);
  const send=node('button',running?'生成中…':'发送消息','primary');send.type='submit';send.disabled=!!running||session.archived||session.agentStatus!=='ACTIVE'||!state.system.modelConfigured||!!pending;
  input.disabled=!!running||session.archived||!!pending;const streamLabel=node('label',undefined,'check stream-choice'),stream=node('input');stream.type='checkbox';stream.checked=!!chatState.stream;stream.disabled=!!running||!!pending;stream.addEventListener('change',()=>chatState.stream=stream.checked);streamLabel.append(stream,document.createTextNode('流式回复（服务商须支持 SSE）'));form.append(label,send,streamLabel);const project=projectChoice(form,!!chatState.projectMode);project.disabled=!!running||!!pending;project.addEventListener('change',()=>chatState.projectMode=project.checked);
  if(session.archived)form.append(node('p','此会话已归档。恢复会话后可继续发送消息。','muted'));
  if(session.agentStatus!=='ACTIVE')form.append(node('p','智能体已停用，历史仍可查看。','error'));
  if(pending&&!pending.sending){form.append(node('p','上次发送的结果尚未确认。先刷新历史，或使用同一请求标识确认结果；不会重复调用模型。','error'),button('确认上次发送结果',()=>sendChat(id,pending)));}
  form.addEventListener('submit',event=>{event.preventDefault();if(send.disabled||!input.value.trim())return;send.disabled=true;input.disabled=true;sendChat(id,{requestId:crypto.randomUUID(),input:input.value.trim(),stream:stream.checked,projectMode:project.checked}).catch(showError);});
  input.addEventListener('keydown',event=>{if((event.ctrlKey||event.metaKey)&&event.key==='Enter'){event.preventDefault();form.requestSubmit();}});
  panel.append(form);$('#content').replaceChildren(layout);const sameView=previous?.dataset.sessionId===String(id)&&previous.dataset.page===messages.dataset.page;const atBottom=!sameView||previous.scrollHeight-previous.scrollTop-previous.clientHeight<48;messages.scrollTop=atBottom?messages.scrollHeight:previous.scrollTop;
}
function chatBubble(label,text,kind,streaming=false){const bubble=node('article',undefined,'chat-bubble '+kind);bubble.append(node('strong',label),streaming?node('pre',text,'stream-output'):kind==='assistant'?renderMarkdown(text):node('pre',text));return bubble;}
function renameChat(session){const content=modal('重命名会话'),form=node('form');field(form,'标题','title',session.title,{max:100});formFooter(form,'保存',async data=>{await api('/chats/'+session.id,{method:'PATCH',body:data});$('#modal').close();await refresh();});content.append(form);}
function exportChat(detail){return '# '+detail.session.title+'\n\n智能体：'+detail.session.agentName+'\n\n'+detail.turns.map(t=>'## 你\n\n'+t.input+'\n\n## 智能体'+(t.mode==='DEMO'?'（模拟）':'')+'\n\n'+(t.output||t.error||'生成中')+'\n\n模型：'+t.model+'；历史轮数：'+t.contextTurns+'\n\nSkills：'+(t.skills||'无')+(t.knowledge?'\n\n检索引用：\n\n'+t.knowledge:'')).join('\n\n---\n\n');}
async function sendChat(id,request){
  const user=state.user.id,epoch=state.sessionEpoch;chatState.turnPage=0;chatState.turnAnchor=null;chatState.pending.set(id,{...request,sending:true});chatState.drafts.delete(id);
  const sameUser=()=>state.sessionEpoch===epoch&&state.user?.id===user&&chatState.owner===user;
  const redraw=async()=>{if(sameUser()&&state.page==='chats'&&chatState.id===id)await refresh();};
  // Start transport before redraw so a failed history refresh cannot prevent message submission.
  const body={requestId:request.requestId,input:request.input,projectMode:!!request.projectMode};const delta=text=>{if(!sameUser())return;const pending=chatState.pending.get(id);if(!pending||pending.requestId!==request.requestId)return;pending.partial=(pending.partial||'')+text;if(state.page!=='chats'||chatState.id!==id)return;const answer=Array.from(document.querySelectorAll('.chat-bubble.assistant')).find(el=>el.dataset.requestId===request.requestId);if(!answer)return;let pre=answer.querySelector('pre.stream-output');if(!pre){answer.querySelector('.markdown-body')?.remove();pre=node('pre',undefined,'stream-output');answer.insertBefore(pre,answer.children[1]||null);}pre.textContent=pending.partial;const messages=answer.closest('.chat-messages');if(messages&&messages.scrollHeight-messages.scrollTop-messages.clientHeight<120)messages.scrollTop=messages.scrollHeight;};
  const sending=(request.stream?apiStream('/chats/'+id+'/messages/stream',body,delta):api('/chats/'+id+'/messages',{method:'POST',body})).then(result=>({result}),error=>({error}));
  await redraw().catch(showError);
  try{const outcome=await sending;if(outcome.error)throw outcome.error;if(sameUser())chatState.pending.delete(id);}
  catch(error){if(sameUser()){if(error.status>=400&&error.status<500){chatState.pending.delete(id);chatState.drafts.set(id,request.input);}else{chatState.pending.set(id,{...request,sending:false});}showError(error);}}
  await redraw();
}
let chatPolling=false;
setInterval(async()=>{if(chatPolling||!state.user||state.page!=='chats'||document.hidden||!chatState.busy||chatState.pending.get(chatState.id)?.sending||$('#modal').open)return;chatPolling=true;try{await refresh();}catch(error){showError(error);}finally{chatPolling=false;}},3000);

async function exportConversation(id){
  const session=await api('/chats/'+id+'/session'),first=await api(pagePath('/chats/'+id+'/turns',{page:0,size:100}));const turns=[...first.items],anchor=first.items[0]?.id;
  for(let page=1;page<first.totalPages;page++){const data=await api(pagePath('/chats/'+id+'/turns',{page,size:100,beforeId:anchor}));turns.push(...data.items);}
  downloadText('conversation-'+id+'.md',exportChat({session,turns:turns.reverse()}));
}
