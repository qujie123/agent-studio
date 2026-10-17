'use strict';
Object.assign(pages,{
  account:['个人中心','YOUR ACCOUNT','维护个人资料和登录安全。'],
  personal:['API 接入与余额','YOUR MODEL CONNECTION','管理自己的模型连接，手动查询服务商余额或 Key 额度。'],
  usage:['调用用量','USAGE INSIGHTS','查看最近 30 天在本平台产生的执行记录，不等同于服务商账单。']
});
Object.assign(actions,{USER_CREATED_BY_ADMIN:'添加成员',USER_PROFILE_UPDATED:'编辑资料',USER_ENABLED:'启用账号',USER_DISABLED:'停用账号',USER_PASSWORD_RESET:'修改密码',USER_SESSIONS_REVOKED:'撤销登录',PERSONAL_MODEL_UPDATED:'更新个人模型',PERSONAL_MODEL_REMOVED:'恢复平台模型'});
Object.assign(translations,{'Email already exists':'邮箱已被使用','Username already exists':'用户名已存在'});
Object.assign(translations,{'Agent changed; refresh and try again':'智能体或技能绑定已在其他窗口修改，请刷新后重新编辑','Model response exceeds the 1 MiB size limit':'模型返回内容超过 1 MiB，已停止接收；请缩小请求范围'});
Object.assign(translations,{'Re-enter the API key when changing the service address or protocol':'更换服务地址或接口协议时，请重新填写对应服务商的 API Key','Unsupported model API protocol':'不支持此接口协议，请选择 OpenAI 兼容或 Anthropic 原生协议'});
Object.assign(actions,{MODEL_CONNECTIONS_CLEARED:'清除模型连接'});
function metricCards(items){
  const root=node('div',undefined,'stats');
  for(const [label,value,note] of items){const card=node('div',undefined,'stat');card.append(node('p',label),node('strong',value));if(note)card.append(node('small',note));root.append(card);}return root;
}
function passwordField(form,label,name){const input=field(form,label,name,'',{max:72});input.type='password';input.minLength=12;input.autocomplete='new-password';return input;}
function profileFields(form,user){
  field(form,'显示名称','displayName',user?.displayName||user?.username||'',{max:80});
  field(form,'邮箱','email',user?.email||'',{max:254}).type='email';
}
function memberForm(user){
  const content=modal(user?'编辑成员资料':'添加成员'),form=node('form');
  if(!user){const name=field(form,'用户名（创建后不可修改）','username','',{max:50});name.minLength=3;}
  profileFields(form,user);
  if(!user){
    passwordField(form,'初始密码（至少 12 字符，最多 72 个 UTF-8 字节）','password');
    const label=node('label','角色'),role=select([['USER','普通用户'],['ADMIN','管理员']],'USER',()=>{});role.name='role';label.append(role);form.append(label);
  }
  formFooter(form,user?'保存资料':'创建成员',async data=>{
    const result=await api('/admin/users'+(user?'/'+user.id:''),{method:user?'PUT':'POST',body:data});
    $('#modal').close();if(result.id===state.user.id){state.user=result;$('#account-name').textContent=result.displayName;}
    toast(user?'成员资料已保存':'成员已创建，请通过安全方式告知初始密码');await refresh();
  });content.append(form);
}
function renderMembers(data,summary){
  const stats=metricCards([['全部成员',summary.total],['已启用',summary.enabled],['有效管理员',summary.admins],['已停用',summary.disabled]]);
  const toolbar=node('div',undefined,'toolbar member-toolbar'),search=node('input');search.type='search';search.maxLength=100;search.placeholder='搜索名称、用户名或邮箱';search.setAttribute('aria-label','搜索成员');
  const filters=state.memberFilters||{};search.value=filters.q||'';
  let sequence=0,timer;const loginToken=state.token,body=node('div'),pager=node('div',undefined,'pager');
  const role=select([['','全部角色'],['ADMIN','管理员'],['USER','普通用户']],filters.role||'',()=>load(0));role.setAttribute('aria-label','筛选角色');
  const status=select([['','全部状态'],['enabled','已启用'],['disabled','已停用']],filters.enabled===undefined?'':String(filters.enabled)==='true'?'enabled':'disabled',()=>load(0));status.setAttribute('aria-label','筛选状态');
  const render=()=>{
    const rows=data.items.map(u=>{
      const identity=node('div',undefined,'member-identity');identity.append(node('strong',u.displayName||u.username),node('small','@'+u.username+(u.id===state.user.id?' · 你':'')));
      const controls=node('div',undefined,'member-actions');controls.append(button('详情',()=>memberDetail(u)),button('编辑',()=>memberForm(u)));
      return[identity,u.email,u.role==='ADMIN'?'管理员':'普通用户',badge(u.enabled?'ACTIVE':'STOPPED'),date(u.lastLoginAt),controls];
    });
    body.replaceChildren(rows.length?table(['成员','邮箱','角色','状态','最近登录','操作'],rows):empty('没有匹配的成员','调整搜索关键词或筛选条件。'));
    pager.replaceChildren(pageControls(data,load));
  };
  const load=async page=>{
    if(state.token!==loginToken||state.page!=='users'||!toolbar.isConnected)return;
    const ticket=++sequence;state.memberFilters={q:search.value.trim(),role:role.value,...(status.value?{enabled:status.value==='enabled'}:{})};state.pagination.users=page;
    const result=await api(pagePath('/admin/users/page',state.memberFilters));
    if(ticket!==sequence||state.token!==loginToken||state.page!=='users'||!toolbar.isConnected)return;
    data=result;render();
  };
  search.addEventListener('input',()=>{clearTimeout(timer);sequence++;timer=setTimeout(()=>load(0).catch(showError),250);});
  toolbar.append(search,role,status,button('刷新',()=>refresh()));render();
  $('#content').replaceChildren(stats,toolbar,body,pager,node('p','停用会阻止登录和后续排队执行，并使旧登录失效；已开始的请求不会被强行中断。','muted'));
}
async function memberDetail(user){
  const content=modal('成员详情 · '+user.username),expectedUser=state.user.id;
  content.append(node('h3',user.displayName),node('p',user.email,'muted'),node('p','创建时间：'+date(user.createdAt)+' · 最近登录：'+date(user.lastLoginAt),'muted'));
  const counts=node('p','正在读取资源数量…','muted');content.append(counts);
  const controls=node('div',undefined,'member-actions');
  const finish=async()=>{if(user.id===state.user.id){await enter();}else{$('#modal').close();await refresh();}};
  controls.append(button('编辑资料',()=>memberForm(user)),button(user.enabled?'停用账号':'启用账号',async()=>{
    if(!confirm((user.enabled?'停用':'启用')+' '+user.username+'？停用会立即使其登录失效。'))return;
    await api('/admin/users/'+user.id+'/status',{method:'PATCH',body:{enabled:!user.enabled}});if(user.id===state.user.id)logout();else await finish();
  }),button(user.role==='ADMIN'?'设为普通用户':'设为管理员',async()=>{
    if(!confirm('确认修改 '+user.username+' 的权限？'))return;
    await api('/admin/users/'+user.id+'/role',{method:'PATCH',body:{role:user.role==='ADMIN'?'USER':'ADMIN'}});await finish();
  }),button('重置密码',()=>passwordReset(user)),button('撤销所有登录',async()=>{
    if(!confirm('使 '+user.username+' 的全部已签发登录失效？'))return;
    await api('/admin/users/'+user.id+'/revoke-sessions',{method:'POST'});if(user.id===state.user.id)logout();else toast('已撤销该成员的所有登录');
  }),button('删除成员',async()=>{
    if(!confirm('删除 '+user.username+'？有关联业务数据的成员不能删除，可选择停用。其个人 Key 配置也会删除。'))return;
    await api('/admin/users/'+user.id,{method:'DELETE'});if(user.id===state.user.id)logout();else await finish();
  },'danger'));content.append(controls);
  try{const data=await api('/admin/users/'+user.id+'/resources');if(state.user?.id===expectedUser&&counts.isConnected)counts.textContent='拥有资源：'+data.agents+' 个智能体 · '+data.skills+' 个技能 · '+data.tasks+' 条任务 · '+data.conversations+' 个会话（不展示私聊内容）';}catch(error){if(counts.isConnected)counts.textContent=error.message;}
}
function passwordReset(user){
  const content=modal('重置密码 · '+user.username),form=node('form');passwordField(form,'新密码（至少 12 字符）','newPassword');
  form.append(node('p','保存后该成员全部旧登录立即失效。密码不会回显，也不会写入操作记录。','muted'));
  formFooter(form,'确认重置密码',async data=>{await api('/admin/users/'+user.id+'/password',{method:'POST',body:data});form.reset();$('#modal').close();if(user.id===state.user.id)logout();toast('密码已更新，旧登录已失效');});content.append(form);
}
function renderAccount(user){
  const loginToken=state.token,epoch=state.sessionEpoch;
  const grid=node('div',undefined,'settings-grid'),form=node('form',undefined,'panel'),safety=node('section',undefined,'panel');
  form.append(node('h2','个人资料'),node('p','@'+user.username+' · '+(user.role==='ADMIN'?'管理员':'普通用户'),'muted'));profileFields(form,user);
  inlineSubmit(form,'保存个人资料',async data=>{const updated=await api('/users/me',{method:'PUT',body:data});if(state.token!==loginToken||state.sessionEpoch!==epoch)return;state.user=updated;$('#account-name').textContent=state.user.displayName;$('#avatar').textContent=state.user.displayName.slice(0,1);toast('个人资料已保存');});
  safety.append(node('h2','登录安全'),node('p','最近登录：'+date(user.lastLoginAt),'muted'),node('p','修改密码或撤销登录后，需要在所有设备上重新登录。','muted'));
  safety.append(button('修改我的密码',()=>{
    const content=modal('修改密码'),passwords=node('form');const current=field(passwords,'当前密码','currentPassword');current.type='password';current.autocomplete='current-password';passwordField(passwords,'新密码（至少 12 字符）','newPassword');
    formFooter(passwords,'更新密码并退出',async data=>{await api('/users/me/password',{method:'POST',body:data});passwords.reset();logout();toast('密码已更新，请重新登录');});content.append(passwords);
  }),button('退出所有设备',async()=>{if(!confirm('撤销所有设备上的登录，包括当前设备？'))return;await api('/users/me/revoke-sessions',{method:'POST'});logout();toast('所有登录已撤销');}));
  grid.append(form,safety);$('#content').replaceChildren(grid);
}
function inlineSubmit(form,label,submit){
  const feedback=node('p','','error'),save=node('button',label,'primary');feedback.setAttribute('role','status');save.type='submit';form.append(feedback,save);
  form.addEventListener('submit',async event=>{event.preventDefault();save.disabled=true;feedback.textContent='';try{await submit(Object.fromEntries(new FormData(form)));}catch(error){feedback.textContent=error.message;}finally{save.disabled=false;}});
}
function balancePanel(endpoint,enabled){
  const panel=node('section',undefined,'panel balance-panel'),result=node('div');
  panel.append(node('p','PROVIDER ACCOUNT','eyebrow'),node('h2','余额与额度'),node('p','读取已保存 Key 对应的服务商数据。仅手动查询；不会发送聊天请求。修改配置后请先保存。','muted'));
  const query=button('查询余额 / 额度',async()=>{
    query.disabled=true;result.replaceChildren(node('p','正在查询服务商…','muted'));
    try{
      const data=await api(endpoint+'/balance',{method:'POST'});result.replaceChildren(node('p',data.provider||'未配置','eyebrow'),node('p',data.message,'muted'));
      if(data.status==='OK'&&data.kind==='ACCOUNT_BALANCE'){
        for(const amount of data.balances){const card=node('div',undefined,'balance-value');card.append(node('small',amount.currency+' · 账户余额'),node('strong',String(amount.total)),node('p','赠金 '+amount.granted+' · 充值余额 '+amount.toppedUp,'muted'));result.append(card);}
        result.append(node('p',data.available?'服务商状态：可用':'服务商状态：不可用','muted'));
      }else if(data.status==='OK'&&data.kind==='KEY_LIMIT'){
        const card=node('div',undefined,'balance-value');card.append(node('small','USD · Key 剩余消费额度'),node('strong',data.remaining===null?'未设上限':String(data.remaining)),node('p','该 Key 已用：'+data.used+' USD','muted'));result.append(card);
      }else result.append(node('strong',data.status==='UNSUPPORTED'?'暂不支持查询':'未取得余额'));
      if(data.consoleUrl){const link=node('a','前往服务商控制台 ↗','console-link');link.href=data.consoleUrl;link.target='_blank';link.rel='noopener noreferrer';result.append(link);}
      result.append(node('p','查询时间：'+date(data.checkedAt),'muted'));
    }catch(error){result.replaceChildren(node('p',error.message,'error'));}finally{query.disabled=false;}
  });
  query.disabled=!enabled;if(!enabled)result.append(node('p','先启用 API 模式并保存密钥，即可手动查询。','muted'));panel.append(query,result,node('p','DeepSeek：账户余额；OpenRouter：Key 额度。其他兼容服务暂不自动查询，不代表余额为零。','muted'));return panel;
}
function renderUsage(data){
  const root=$('#content');
  const stats=metricCards([['API 执行记录',data.apiCalls,'次'],['成功 / 失败',data.succeeded+' / '+data.failed],['输入 Token',data.inputTokens],['输出 Token',data.outputTokens]]);
  const controls=node('div',undefined,'toolbar');controls.append(button('刷新记录',()=>refresh()),button('API 接入与余额',()=>navigate('personal')));
  const note=node('div','近 '+data.days+' 天 · '+data.simulated+' 条模拟记录 · '+data.missingUsage+' 条 API 记录未返回完整 Token。按执行开始时间统计，包含连接测试；删除会话不会删除用量。未知 Token 不按零消耗解释。旧任务使用升级前最后更新时间近似回填；本页不是服务商账单。','banner');
  const rows=data.recent.map(r=>[({CHAT:'对话',TASK:'任务',TEST:'连接测试'}[r.type]||r.type)+' #'+r.id,r.model,r.mode==='API'?'真实 API':'模拟',badge(r.status),r.inputTokens??'未返回',r.outputTokens??'未返回',date(r.createdAt)]);
  root.replaceChildren(stats,controls,note,heading('最近执行 · 最多 50 条'),rows.length?table(['来源','模型','模式','状态','输入 Token','输出 Token','时间'],rows):empty('还没有执行记录','完成一轮对话或提交任务后，可在这里查看。'));
}
