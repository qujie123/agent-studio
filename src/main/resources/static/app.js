'use strict';
const $ = selector => document.querySelector(selector);
const state = {token:sessionStorage.getItem('agent-token'), user:null, system:{}, page:'overview', agents:[], tasks:[], filter:'all', taskFilter:'mine', register:false, detail:null, loadSequence:0};
const labels = {DRAFT:'草稿',ACTIVE:'已启用',STOPPED:'已停用',PENDING:'待执行',QUEUED:'排队中',RUNNING:'执行中',SUCCEEDED:'已完成',FAILED:'失败',CANCELLED:'已取消'};
const pages = {overview:['工作台','WORKSPACE OVERVIEW','从这里开始，让智能体为你分担工作。'],agents:['智能体','YOUR AGENT COLLECTION','创建、配置和管理你的 AI 协作伙伴。'],tasks:['任务中心','TASK OPERATIONS','追踪任务进度，查看每一次执行的结果。'],users:['用户管理','TEAM & ACCESS','管理平台成员及其访问权限。'],audit:['操作记录','ACTIVITY LOG','分页查看操作记录，不包含密码、密钥或任务正文。']};
const actions = {AGENT_CREATED:'创建智能体',AGENT_UPDATED:'更新智能体',AGENT_DELETED:'删除智能体',USER_REGISTERED:'注册用户',USER_ROLE_CHANGED:'变更角色',USER_DELETED:'删除用户',ADMIN_BOOTSTRAPPED:'初始化管理员',TASK_CREATED:'创建任务',TASK_QUEUED:'任务入队',TASK_STARTED:'开始执行',TASK_SUCCEEDED:'执行成功',TASK_FAILED:'执行失败',TASK_CANCELLED:'取消任务',TASK_RECOVERED:'恢复中断任务',TASK_MANUALLY_COMPLETED:'人工提交结果'};
Object.assign(pages,{skills:['技能库','YOUR SKILLS','维护可复用的指令模板，按需绑定到你创建的智能体。'],settings:['模型服务','MODEL CONNECTION','配置平台共用的模型服务，保存后即可发起真实任务。']});
const workspaceSections=[
  {id:'overview',title:'工作台',icon:'◫',pages:[['overview','概览']]},
  {id:'work',title:'工作空间',icon:'▤',pages:[['chats','多轮对话'],['tasks','任务与成品'],['workflows','工作流'],['skillRuns','技能执行记录']]},
  {id:'resources',title:'智能体中心',icon:'◇',pages:[['agents','智能体管理'],['skills','Skills 技能库'],['knowledge','知识库']]},
  {id:'models',title:'模型服务',icon:'◉',pages:[['personal','API 接入与余额'],['usage','调用用量']]},
  {id:'admin',title:'平台管理',icon:'⚙',admin:true,pages:[['users','成员管理'],['settings','平台模型'],['audit','操作记录']]}
];
function renderWorkspaceNavigation(){
  const nav=$('#workspace-nav');nav.replaceChildren();
  for(const section of workspaceSections){
    if(section.admin&&state.user.role!=='ADMIN')continue;
    const group=node('div',undefined,'nav-group'),children=node('div',undefined,'nav-children'),single=section.pages.length===1;
    children.id='nav-pages-'+section.id;children.hidden=true;
    const el=button('',()=>single?navigate(section.pages[0][0]):toggleNavigationGroup(section.id),'section-toggle');el.dataset.section=section.id;
    const icon=node('span',section.icon,'nav-icon');icon.setAttribute('aria-hidden','true');el.append(icon,node('span',section.title));
    if(single)el.dataset.page=section.pages[0][0];
    else{
      el.setAttribute('aria-expanded','false');el.setAttribute('aria-controls',children.id);
      const arrow=node('span','›','nav-chevron');arrow.setAttribute('aria-hidden','true');el.append(arrow);
      for(const [name,label]of section.pages){const child=button(label,()=>navigate(name));child.dataset.page=name;children.append(child);}
    }
    group.append(el,children);nav.append(group);
  }
}
function toggleNavigationGroup(id){
  const expand=$('#workspace-nav [data-section='+id+']').getAttribute('aria-expanded')!=='true';
  for(const el of document.querySelectorAll('#workspace-nav [aria-controls]')){
    const open=expand&&el.dataset.section===id;el.setAttribute('aria-expanded',String(open));$('#'+el.getAttribute('aria-controls')).hidden=!open;
  }
}
function updateWorkspaceNavigation(section,page){
  for(const el of document.querySelectorAll('#workspace-nav [data-section]')){
    const active=el.dataset.section===section?.id;el.classList.toggle('active',active);
    if(el.hasAttribute('aria-controls')){el.setAttribute('aria-expanded',String(active));$('#'+el.getAttribute('aria-controls')).hidden=!active;}
  }
  for(const el of document.querySelectorAll('#workspace-nav [data-page]')){
    const active=el.dataset.page===page;el.classList.toggle('active',active);if(active)el.setAttribute('aria-current','page');else el.removeAttribute('aria-current');
  }
}
function closeMobileNavigation(){$('aside').dataset.navigationOpen='false';$('#navigation-toggle').setAttribute('aria-expanded','false');}
$('#navigation-toggle').addEventListener('click',()=>{const open=$('#navigation-toggle').getAttribute('aria-expanded')!=='true';$('aside').dataset.navigationOpen=String(open);$('#navigation-toggle').setAttribute('aria-expanded',String(open));});
$('#account-settings').addEventListener('click',()=>navigate('account').catch(showError));
document.addEventListener('click',event=>{if(!$('#account-menu').contains(event.target))$('#account-menu').open=false;});
document.addEventListener('keydown',event=>{
  if(event.key!=='Escape')return;
  if($('#account-menu').open){$('#account-menu').open=false;$('#account-toggle').focus();}
  else if($('#navigation-toggle').getAttribute('aria-expanded')==='true'){closeMobileNavigation();$('#navigation-toggle').focus();}
});
Object.assign(actions,{SKILL_CREATED:'创建技能',SKILL_UPDATED:'更新技能',SKILL_DELETED:'删除技能',AGENT_SKILLS_UPDATED:'调整智能体技能',MODEL_SETTINGS_UPDATED:'更新模型服务',MODEL_CONNECTION_TESTED:'测试模型连接'});
function node(tag, text, className){const el=document.createElement(tag);if(text!==undefined)el.textContent=text;if(className)el.className=className;return el;}
function button(text, handler, className=''){const el=node('button',text,className);el.type='button';el.addEventListener('click',()=>Promise.resolve(handler()).catch(showError));return el;}
function badge(status){return node('span',labels[status]||status,'badge '+status);}
function date(value){return value?new Date(value).toLocaleString('zh-CN',{hour12:false}):'—';}
function toast(message){$('#toast').textContent=message;$('#toast').hidden=false;clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('#toast').hidden=true,5000);}
function showError(error){if(!error.stale)toast(error.message||'操作失败，请稍后重试');}
const translations={'Invalid username or password':'用户名或密码错误','Only pending tasks can be executed':'该任务已开始或已完成，请刷新后查看','Only pending or queued tasks can be cancelled':'只能取消尚未执行的任务','The last administrator cannot be demoted':'必须至少保留一位管理员','The last administrator cannot be deleted':'不能删除最后一位管理员'};
async function api(path,options={}){
  const requestToken=state.token,requestEpoch=state.sessionEpoch;const headers={'Content-Type':'application/json'};if(state.token)headers.Authorization='Bearer '+state.token;
  const requireCurrent=()=>{if(state.token!==requestToken||state.sessionEpoch!==requestEpoch){const error=new Error('旧会话请求已忽略');error.stale=true;throw error;}};
  let response;try{response=await fetch('/api'+path,{...options,headers,body:options.body?JSON.stringify(options.body):undefined});}catch{requireCurrent();throw new Error('无法连接服务器，请检查服务是否运行');}requireCurrent();
  if(response.status===204)return null;
  const body=await response.json().catch(()=>({}));
  requireCurrent();
  if(!response.ok){if(response.status===401&&state.user&&state.token===requestToken){logout();throw new Error('登录已失效，请重新登录');}const message=body.message||body.detail||body.error||'请求失败';const failure=new Error(translations[message]||({401:'用户名或密码错误',403:'没有权限进行此操作'}[response.status])||message);failure.status=response.status;throw failure;}
  return body;
}
function logout(){resetChat();state.loadSequence++;state.sessionEpoch=(state.sessionEpoch||0)+1;state.token=null;state.user=null;state.detail=null;state.agents=[];state.tasks=[];state.system={};state.filter='all';state.taskFilter='mine';state.pagination={};state.memberFilters={};state.resourceFilters={};state.dashboard=null;state.dialogEpoch=(state.dialogEpoch||0)+1;state.page='overview';sessionStorage.removeItem('agent-token');$('#modal').close();$('#account-menu').open=false;$('#workspace').hidden=true;$('#auth').hidden=false;if(state.register)$('#auth-switch').click();$('#auth-form').reset();}
$('#logout').addEventListener('click',logout);
$('#auth-switch').addEventListener('click',()=>{state.register=!state.register;$('#email-label').hidden=!state.register;$('#email-label input').required=state.register;$('#auth-title').textContent=state.register?'创建你的账号':'欢迎回来';$('#auth-caption').textContent=state.register?'注册后即可创建智能体和提交任务。':'登录，继续你的智能体协作。';$('#auth-submit').textContent=state.register?'创建账号 →':'登录工作台 →';$('#auth-switch').textContent=state.register?'已有账号？返回登录':'还没有账号？创建账号';$('#auth-error').textContent='';$('#auth-form [name=password]').autocomplete=state.register?'new-password':'current-password';});
$('#auth-form').addEventListener('submit',async event=>{event.preventDefault();const submit=$('#auth-submit');submit.disabled=true;$('#auth-error').textContent='';const data=Object.fromEntries(new FormData(event.target));try{if(state.register)await api('/users/register',{method:'POST',body:data});const result=await api('/users/login',{method:'POST',body:{username:data.username,password:data.password}});state.token=result.token;sessionStorage.setItem('agent-token',state.token);await enter();}catch(error){$('#auth-error').textContent=error.message;}finally{submit.disabled=false;}});
async function refreshSystem(){state.system=await api('/system/status');const source=state.system.connectionSource==='PERSONAL'?'个人配置':'平台默认';$('#connection').textContent=source+' · '+(state.system.demo?'模拟模式':state.system.modelConfigured?'真实 API':'未配置');const banner=$('#environment-banner'),messages=[];if(state.system.demo)messages.push('模拟模式 · 当前任务使用模拟响应。可在「模型服务 / API 接入与余额」填写自己的 API Key，或使用管理员配置的平台模型。');else if(!state.system.modelConfigured)messages.push('模型尚未配置完成，请检查「模型服务 / API 接入与余额」或联系管理员。');if(state.system.ephemeral)messages.push('演示环境的数据和模型设置将在服务重启后清空。');if(!state.system.workerEnabled)messages.push('后台执行器已停用。');banner.textContent=messages.join(' ');banner.hidden=!messages.length;}
async function enter(){state.user=await api('/users/me');await refreshSystem();$('#auth').hidden=true;$('#workspace').hidden=false;$('#account-name').textContent=(state.user.displayName||state.user.username);$('#avatar').textContent=(state.user.displayName||state.user.username).slice(0,1).toUpperCase();renderWorkspaceNavigation();await navigate('overview');}
async function navigate(page){
  const section=workspaceSections.find(section=>section.pages.some(([name])=>name===page));
  if(!state.user||!pages[page]||!section&&page!=='account'||section?.admin&&state.user.role!=='ADMIN'){toast('没有权限访问该页面');return;}
  state.dialogEpoch=(state.dialogEpoch||0)+1;state.page=page;state.detail=null;$('#modal').close();$('#account-menu').open=false;closeMobileNavigation();updateWorkspaceNavigation(section,page);
  const [title,kicker,description]=pages[page];$('#page-title').textContent=title;$('#page-kicker').textContent=kicker;$('#page-description').textContent=description;
  $('#breadcrumb').textContent=!section?'个人设置 / 资料与安全':page==='overview'?section.title:section.title+' / '+section.pages.find(([name])=>name===page)[1];
  $('#primary-action').hidden=['audit','settings','personal','account','usage','skillRuns'].includes(page);$('#primary-action').textContent=page==='users'?'＋ 添加成员':page==='chats'?'＋ 新建对话':page==='tasks'?'＋ 新建任务':page==='skills'?'＋ 创建技能':page==='knowledge'?'＋ 创建文档':page==='workflows'?'＋ 创建工作流':'＋ 创建智能体';$('#primary-action').onclick=()=>Promise.resolve(page==='users'?memberForm():page==='chats'?chatForm():page==='tasks'?taskForm():page==='skills'?skillForm():page==='knowledge'?knowledgeForm():page==='workflows'?workflowForm():agentForm()).catch(showError);await refresh();
}
async function refresh(quiet=false){if(!state.user)return;const page=state.page,userId=state.user.id,sequence=++state.loadSequence;const current=()=>state.page===page&&state.user?.id===userId&&state.loadSequence===sequence;if(!quiet)$('#content').replaceChildren(node('div','正在加载…','loading'));try{
  if(page==='overview'){const [agents,tasks,dashboard]=await Promise.all([api(pagePath('/agents/page',{page:0,size:3,status:'ACTIVE'})),api(pagePath('/tasks/page',{page:0,size:5})),api('/me/dashboard')]);if(!current())return;state.agents=agents.items;state.tasks=tasks.items;state.dashboard=dashboard;renderOverview();}
  else if(page==='agents'){const data=await api(pagePath('/agents/page',{...resourceFilter('agents'),scope:state.filter==='mine'?'mine':'visible'}));if(!current())return;state.agents=data.items;state.pageData=data;renderAgents();}
  else if(page==='tasks'){if(state.user.role!=='ADMIN'&&state.taskFilter==='all')state.taskFilter='mine';const data=await api(pagePath('/tasks/page',{...resourceFilter('tasks'),scope:state.taskFilter}));if(!current())return;state.tasks=data.items;state.pageData=data;renderTasks();}
  else if(page==='chats'){const sessions=await api(pagePath('/chats/page',{q:chatState.search||'',view:chatState.view||'active'}));if(current())await renderChats(sessions,current);}
  else if(page==='users'){const [users,summary]=await Promise.all([api(pagePath('/admin/users/page',state.memberFilters||{})),api('/admin/users/summary')]);if(current())renderMembers(users,summary);}
  else if(page==='knowledge'){const data=await api(pagePath('/knowledge/page',resourceFilter('knowledge')));if(current())renderKnowledge(data);}
  else if(page==='workflows'){const data=await api(pagePath('/workflows/page',resourceFilter('workflows')));if(current())renderWorkflows(data);}
  else if(page==='skillRuns'){const data=await api(pagePath('/skill-runs/page'));if(current())renderSkillRuns(data);}
  else if(page==='skills'){const skills=await api(pagePath('/skills/page',resourceFilter('skills')));if(current()){renderSkills(skills.items);$('#content').append(pageControls(skills));}}
  else if(page==='personal'){const settings=await api('/me/model-settings');if(current())renderSettings(settings,true);}
  else if(page==='account'){const user=await api('/users/me');if(current())renderAccount(user);}
  else if(page==='usage'){const usage=await api('/me/usage');if(current())renderUsage(usage);}
  else if(page==='settings'){const settings=await api('/admin/model-settings');if(current())renderSettings(settings);}
  else{const events=await api(pagePath('/admin/audit/page'));if(current()){renderAudit(events.items);$('#content').append(pageControls(events));}}
}catch(error){if(!current())return;if(!quiet){const empty=node('div',error.message,'empty');empty.append(button('重新加载',()=>refresh()));$('#content').replaceChildren(empty);}else showError(error);}}
function empty(title,description,action){const el=node('div',undefined,'empty');el.append(node('strong',title),node('div',description));if(action)el.append(action);return el;}
function pagePath(path,extra={}){state.pagination??={};const params=new URLSearchParams({page:state.pagination[state.page]||0,size:20,...extra});return path+'?'+params;}
function pageControls(data,load=page=>{state.pagination[state.page]=page;return refresh();}){const pager=node('div',undefined,'pager');const previous=button('上一页',()=>load(data.page-1)),next=button('下一页',()=>load(data.page+1));previous.disabled=data.page<=0;next.disabled=data.page+1>=data.totalPages;pager.append(node('span','共 '+data.total+' 条 · '+(data.page+1)+' / '+Math.max(1,data.totalPages)),previous,next);return pager;}
async function agentPicker(form,agentId){
  const label=node('label','选择智能体'),choice=select([],'',()=>{picked=available.find(a=>String(a.id)===choice.value)||picked;});choice.name='agentId';choice.required=true;label.append(choice);
  const toolbar=node('div',undefined,'toolbar'),search=node('input'),pager=node('div');search.type='search';search.maxLength=100;search.placeholder='搜索可用智能体';search.setAttribute('aria-label','搜索可用智能体');toolbar.append(search);form.append(label,toolbar,pager);
  let available=[],picked=agentId?await api('/agents/'+agentId):null,sequence=0,timer;
  if(picked?.status!=='ACTIVE')picked=null;
  const load=async page=>{const ticket=++sequence;const data=await api(pagePath('/agents/page',{page,size:20,status:'ACTIVE',q:search.value.trim()}));if(ticket!==sequence||!form.isConnected)return;available=data.items;if(!picked)picked=available[0];const values=[...(picked?[picked]:[]),...available.filter(a=>a.id!==picked?.id)];choice.replaceChildren();for(const agent of values){const option=node('option',agent.name+' · '+agent.modelName);option.value=agent.id;choice.append(option);}choice.value=picked?String(picked.id):'';pager.replaceChildren(pageControls(data,load));if(!data.total&&!picked)pager.append(node('p','没有匹配的已启用智能体，请调整搜索或先创建智能体。','muted'));};
  search.addEventListener('input',()=>{clearTimeout(timer);timer=setTimeout(()=>load(0).catch(showError),250);});await load(0);
}
function heading(title,action){const el=node('div',undefined,'section-heading');el.append(node('h2',title));if(action)el.append(action);return el;}
function renderOverview(){const root=$('#content');root.replaceChildren();const stats=node('div',undefined,'stats');[['可用智能体',state.dashboard.agents,'个'],['我的任务',state.dashboard.tasks,'项'],['执行队列',state.dashboard.running,'项'],['已完成',state.dashboard.succeeded,'项']].forEach(([label,value,unit])=>{const el=node('div',undefined,'stat');el.append(node('p',label),node('strong',value),node('small',unit));stats.append(el);});root.append(stats);const feature=node('div',undefined,'feature');const text=node('div');text.append(node('h2','你的下一个想法，交给智能体。'),node('p','选一个协作伙伴，描述任务，剩下的在任务中心持续跟进。'));feature.append(text,button('开始一个任务 ↗',()=>taskForm()));root.append(feature,heading('探索智能体',button('查看全部 →',()=>navigate('agents'),'link')),agentCards(state.agents.filter(a=>a.status==='ACTIVE').slice(0,3)),heading('最近任务',button('任务中心 →',()=>navigate('tasks'),'link')),taskTable(state.tasks.slice(0,5)));}
function agentCards(agents){
  if(!agents.length)return empty('还没有智能体','创建第一个智能体，配置它的角色与模型。',button('创建智能体',()=>agentForm(),'primary'));
  const cards=node('div',undefined,'cards');
  agents.forEach(agent=>{
    const card=node('article',undefined,'card'),top=node('div',undefined,'card-top');
    top.append(node('span','◇','agent-icon'),badge(agent.status));
    const body=node('div');body.append(node('h3',agent.name),node('p',agent.description||'尚未填写描述'));
    const meta=node('p',agent.modelName+' · '+(agent.skillCount||0)+' Skills · @'+agent.creatorUsername,'card-meta');
    const controls=node('div',undefined,'card-actions');
    if(agent.status==='ACTIVE')controls.append(button('开始对话',()=>newChat(agent.id)),button('新建任务 ↗',()=>taskForm(agent.id)));
    if(agent.creatorId===state.user.id||state.user.role==='ADMIN')controls.append(button('管理',()=>agentManagement(agent)));
    card.append(top,body,meta,controls);cards.append(card);
  });return cards;
}
function agentManagement(agent){
  const content=modal(agent.name+' · 管理'),controls=node('div',undefined,'agent-management');
  content.append(node('p','配置这个智能体的资料、模型参数和专属资源。','muted'));
  controls.append(button('编辑',()=>agentForm(agent)),button('Skills',()=>bindingForm(agent)));
  if(agent.creatorId===state.user.id)controls.append(button('知识库',()=>knowledgeBinding(agent)));
  controls.append(button('删除',async()=>{if(!confirm('删除智能体「'+agent.name+'」？已有任务记录的智能体不能删除，可改为停用。'))return;await api('/agents/'+agent.id,{method:'DELETE'});$('#modal').close();toast('智能体已删除');await refresh();},'danger'));
  content.append(controls);
}
function select(options,value,onChange){const el=node('select');for(const [key,label]of options){const option=node('option',label);option.value=key;el.append(option);}el.value=value;el.addEventListener('change',()=>Promise.resolve(onChange(el.value)).catch(showError));return el;}
function resourceFilter(page=state.page){state.resourceFilters??={};return state.resourceFilters[page]??={};}
function resourceSearch(label){
  const page=state.page,token=state.token,epoch=state.sessionEpoch,input=node('input');input.type='search';input.id='resource-search-'+page;input.maxLength=100;input.placeholder=label;input.setAttribute('aria-label',label);input.value=resourceFilter(page).q||'';
  let timer;input.addEventListener('input',()=>{clearTimeout(timer);timer=setTimeout(async()=>{if(!input.isConnected||state.page!==page||state.token!==token||state.sessionEpoch!==epoch)return;const position=input.selectionStart;resourceFilter(page).q=input.value.trim();state.pagination[page]=0;try{await refresh();if(state.page!==page||state.token!==token||state.sessionEpoch!==epoch)return;const replacement=$('#resource-search-'+page);if(replacement){replacement.focus();replacement.setSelectionRange(position,position);}}catch(error){showError(error);}},250);});return input;
}
function resourceStatus(options,label){const filters=resourceFilter(),status=select([['','全部状态'],...options.map(value=>[value,labels[value]])],filters.status||'',value=>{if(value)filters.status=value;else delete filters.status;state.pagination[state.page]=0;return refresh();});status.setAttribute('aria-label',label);return status;}
function renderAgents(){const toolbar=node('div',undefined,'toolbar resource-toolbar');const filter=select([['all',state.user.role==='ADMIN'?'所有智能体':'可用智能体'],['mine','我创建的']],state.filter,value=>{state.filter=value;state.pagination.agents=0;return refresh();});filter.setAttribute('aria-label','智能体范围');toolbar.append(filter,resourceStatus(['DRAFT','ACTIVE','STOPPED'],'智能体状态'),resourceSearch('搜索智能体名称或描述'),button('刷新',()=>refresh()));$('#content').replaceChildren(toolbar,agentCards(state.agents),pageControls(state.pageData));}
function taskTable(tasks){if(!tasks.length)return empty('暂无任务','从一个具体的问题开始，任务结果会保存在这里。',button('新建任务',()=>taskForm(),'primary'));return table(['任务 / 智能体','任务内容','状态','创建时间','操作'],tasks.map(task=>[node('span','#'+task.id+' · '+task.agentName),node('span',task.input.length>70?task.input.slice(0,70)+'…':task.input),badge(task.status),date(task.createdAt),button('查看详情',()=>taskDetail(task.id))]));}
function table(headers,rows){const wrap=node('div',undefined,'table-wrap'),el=node('table'),thead=node('thead'),head=node('tr');headers.forEach(text=>head.append(node('th',text)));thead.append(head);const body=node('tbody');rows.forEach(values=>{const tr=node('tr');values.forEach(value=>{const td=node('td');if(value instanceof Node)td.append(value);else td.textContent=value;tr.append(td);});body.append(tr);});el.append(thead,body);wrap.append(el);return wrap;}
function renderTasks(){const toolbar=node('div',undefined,'toolbar resource-toolbar');const options=[['mine','我提交的任务'],['assigned','我的智能体收到的任务']];if(state.user.role==='ADMIN')options.push(['all','平台全部任务']);const filter=select(options,state.taskFilter,value=>{state.taskFilter=value;state.pagination.tasks=0;return refresh();});filter.setAttribute('aria-label','任务范围');toolbar.append(filter,resourceStatus(['PENDING','QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED'],'任务状态'),resourceSearch('搜索任务内容或智能体'),button('刷新',()=>refresh()),node('span','排队和执行中的任务将自动刷新','muted'));$('#content').replaceChildren(toolbar,taskTable(state.tasks),pageControls(state.pageData));}
function modal(title){$('#modal').classList.remove('artifact-dialog');state.dialogEpoch=(state.dialogEpoch||0)+1;state.detail=null;$('#modal-title').textContent=title;$('#modal-content').replaceChildren();if(!$('#modal').open)$('#modal').showModal();return $('#modal-content');}
$('#modal-close').addEventListener('click',()=>$('#modal').close());$('#modal').addEventListener('close',()=>{if($('#modal').open)return;state.detail=null;state.dialogEpoch=(state.dialogEpoch||0)+1;});
function field(form,label,name,value='',options={}){const wrap=node('label',label);const input=node(options.multiline?'textarea':'input');input.name=name;input.value=value??'';input.required=options.required!==false;if(options.max)input.maxLength=options.max;if(options.placeholder)input.placeholder=options.placeholder;wrap.append(input);form.append(wrap);return input;}
function formFooter(form,label,submit){const error=node('p','','error');error.setAttribute('role','alert');const buttons=node('div',undefined,'form-actions');const save=node('button',label,'primary');save.type='submit';buttons.append(button('取消',()=>$('#modal').close()),save);form.append(error,buttons);form.addEventListener('submit',async event=>{event.preventDefault();save.disabled=true;error.textContent='';try{await submit(Object.fromEntries(new FormData(form)));}catch(failure){error.textContent=failure.message;}finally{save.disabled=false;}});}
function agentForm(agent){const content=modal(agent?'编辑智能体':'创建智能体'),form=node('form');field(form,'名称','name',agent?.name,{max:100});field(form,'描述','description',agent?.description,{max:500,required:false});field(form,'系统提示词','systemPrompt',agent?.systemPrompt,{multiline:true,max:10000,placeholder:'描述智能体的角色、目标和回答方式。'});field(form,'模型名称','modelName',agent?.modelName||state.system.defaultModel||(state.system.demo?'demo-model':''),{max:100,placeholder:'例如 deepseek-chat（须由服务商支持）'});const label=node('label','运行状态');const status=select([['DRAFT','草稿'],['ACTIVE','启用'],['STOPPED','停用']],agent?.status||'DRAFT',()=>{});status.name='status';label.append(status);form.append(label);const generation=node('fieldset',undefined,'generation-options');generation.append(node('legend','生成参数（可选）'));const temperature=field(generation,'温度（0–2）','temperature',agent?.temperature,{required:false,placeholder:'留空使用服务商默认'});temperature.type='number';temperature.min=0;temperature.max=2;temperature.step=0.05;const limit=field(generation,'最大输出 token（1–65536）','maxOutputTokens',agent?.maxOutputTokens,{required:false,placeholder:'留空使用服务商默认'});limit.type='number';limit.min=1;limit.max=65536;limit.step=1;generation.append(node('p','不支持温度或 max_tokens 的模型请留空。这是输出长度上限，不是费用预算；任务排队时固定参数，对话每轮使用当前参数。','muted'));form.append(generation);formFooter(form,'保存智能体',async data=>{await api('/agents'+(agent?'/'+agent.id:''),{method:agent?'PUT':'POST',body:{...data,version:agent?.version??0,temperature:data.temperature===''?null:Number(data.temperature),maxOutputTokens:data.maxOutputTokens===''?null:Number(data.maxOutputTokens)}});$('#modal').close();toast('智能体已保存，可点击 Skills 绑定技能');await refresh();});content.append(form);}
async function taskForm(agentId){const content=modal('新建任务'),form=node('form');content.append(form);await agentPicker(form,agentId);field(form,'任务内容','input','',{multiline:true,max:20000,placeholder:'告诉智能体，你希望完成什么？'});projectChoice(form);const checkLabel=node('label',undefined,'check'),check=node('input');check.type='checkbox';check.name='enqueue';check.checked=state.system.modelConfigured&&state.system.workerEnabled;check.disabled=!state.system.modelConfigured||!state.system.workerEnabled;checkLabel.append(check,document.createTextNode('提交后自动排队执行'));form.append(checkLabel);formFooter(form,'提交任务',async data=>{const task=await api('/tasks',{method:'POST',body:{agentId:Number(data.agentId),input:data.input,projectMode:!!data.projectMode}});if(data.enqueue){try{await api('/tasks/'+task.id+'/enqueue',{method:'POST'});}catch(error){$('#modal').close();await navigate('tasks');await taskDetail(task.id);toast('任务已保存，但未能入队：'+error.message);return;}}$('#modal').close();toast('任务已提交');await navigate('tasks');});}
async function taskDetail(id){
  const epoch=state.dialogEpoch,task=await api('/tasks/'+id);if(epoch!==state.dialogEpoch)return;const content=modal('任务 #'+task.id+' · '+task.agentName);state.detail=id;state.detailStatus=task.status;
  content.append(badge(task.status),node('p','提交人 '+task.username+' · '+date(task.createdAt),'muted'),
    node('p','任务输入','detail-label'),node('pre',task.input,'result'),
    node('p','执行结果'+(task.executionModel?' · '+task.executionModel:'')+(task.executionMode==='DEMO'?' · 模拟响应':task.executionMode==='API'?' · 真实 API':''),'detail-label'),
    node('pre',task.output||(['QUEUED','RUNNING'].includes(task.status)?'任务正在处理中，此页面会自动更新。':'暂无结果'),'result'));
  if(task.projectMode&&task.output&&task.status==='SUCCEEDED')content.querySelector('pre.result:last-of-type').textContent='项目文件已生成。使用下面的成品卡片查看、预览和下载。';
  attachArtifacts(content,'/artifacts/tasks/'+id,task.output,task.status,task.projectMode).catch(showError);
  if(task.temperature!=null||task.maxOutputTokens!=null)content.append(node('p','执行参数快照 · 温度：'+(task.temperature??'默认')+' · 最大输出 token：'+(task.maxOutputTokens??'默认'),'muted'));
  if(task.executionSkills)content.append(node('p','执行时的技能快照','detail-label'),node('pre',task.executionSkills,'result'));
  if(task.knowledge)content.append(knowledgeReferences(task.knowledge));
  const controls=node('div',undefined,'form-actions');
  if(task.output)controls.append(button('复制结果',()=>navigator.clipboard.writeText(task.output).then(()=>toast('已复制'))),button('下载回答文本',()=>downloadText('task-'+id+'.md',task.output)));
  if(task.status==='PENDING'){
    const enqueue=button('加入执行队列',async()=>{await api('/tasks/'+id+'/enqueue',{method:'POST'});await taskDetail(id);await refresh(true);},'primary');enqueue.disabled=!state.system.modelConfigured||!state.system.workerEnabled;controls.append(enqueue);
    if(state.user.role==='ADMIN'||task.agentCreatorId===state.user.id)controls.append(button('人工提交结果',()=>manualResult(id)));
  }
  if(['PENDING','QUEUED'].includes(task.status))controls.append(button('取消任务',async()=>{if(!confirm('取消此任务？'))return;await api('/tasks/'+id+'/cancel',{method:'POST'});await taskDetail(id);await refresh(true);}));
  if(['FAILED','CANCELLED'].includes(task.status))controls.append(button('创建新任务',()=>taskForm(task.agentId)));
  content.append(controls);
}
function manualResult(id){const content=modal('人工提交结果 #'+id),form=node('form');field(form,'结果内容','output','',{multiline:true,max:50000});const label=node('label','结果状态'),status=select([['true','成功'],['false','失败']],'true',()=>{});status.name='success';label.append(status);form.append(label);formFooter(form,'保存结果',async data=>{await api('/tasks/'+id+'/result',{method:'PATCH',body:{output:data.output,success:data.success==='true'}});await taskDetail(id);await refresh(true);});content.append(form);}
function renderAudit(events){$('#content').replaceChildren(events.length?table(['时间','操作人','操作','资源'],events.map(event=>[date(event.createdAt),event.actor,actions[event.action]||event.action,event.resourceType+' #'+event.resourceId])):empty('暂无操作记录','创建智能体或提交任务后，操作会记录在这里。'));}
let polling=false;
setInterval(async()=>{if(!state.user||document.hidden||polling)return;polling=true;try{const detail=state.detail;if(detail&&$('#modal').open){const task=await api('/tasks/'+detail);if(detail===state.detail&&task.status!==state.detailStatus){await taskDetail(detail);await refresh(true);}}if(!$('#modal').open&&['overview','tasks'].includes(state.page)&&(state.tasks.some(t=>['RUNNING','QUEUED'].includes(t.status))||state.page==='overview'&&state.dashboard?.running>0))await refresh(true);}catch(error){showError(error);}finally{polling=false;}},3000);
if(state.token)enter().catch(error=>{if(error.stale)return;logout();$('#auth-error').textContent=error.message;});
