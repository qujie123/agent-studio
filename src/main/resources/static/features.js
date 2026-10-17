'use strict';
function downloadText(filename,text){const url=URL.createObjectURL(new Blob([text],{type:'text/markdown;charset=utf-8'}));const a=node('a');a.href=url;a.download=filename;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);}
const skillTemplates=[
  {name:'代码审查',description:'按严重程度检查正确性、边界条件和可维护性。',instructions:'检查用户提供的代码。先识别实际问题，逐项给出触发条件、影响和最小修复建议。区分已确认的问题与推测。若代码不足以判断，明确说明所缺信息。最后提供适合该改动的测试建议。'},
  {name:'可运行代码交付',description:'生成完整代码，并说明依赖和运行方式。',instructions:'交付尽可能完整、可运行的代码。注明语言版本、需要安装的依赖、启动命令和操作方式。说明关键参数如何调整。若没有执行环境，明确说明代码尚未实际运行，不能声称已测试。'},
  {name:'文章润色',description:'保持原意，改善结构与表达。',instructions:'保持用户原意和事实，不添加未经证实的信息。优化段落顺序、语句清晰度和表达自然度。先给出润色后的完整文本，再简要列出关键调整。'}
];
function renderSkills(skills){
  const root=$('#content'),toolbar=node('div',undefined,'toolbar resource-toolbar');
  toolbar.append(button('从模板创建',()=>templatePicker()),button('导入技能文件',()=>importSkill()),button('刷新',()=>refresh()));
  toolbar.prepend(resourceSearch('搜索技能名称或描述'));
  const cards=node('div',undefined,'cards');
  const render=()=>{cards.replaceChildren();const matches=skills;
    for(const skill of matches){const card=node('article',undefined,'card');card.append(node('h3',skill.name),badge(skill.enabled?'ACTIVE':'STOPPED'),node('p',skill.description||'暂无描述'),node('p',(skill.kind==='PYTHON'?'Python · 隔离执行':'指令模板')+' · v'+(skill.version+1)+' · '+date(skill.updatedAt),'card-meta'));const controls=node('div',undefined,'card-actions');if(skill.kind==='PYTHON'){const run=button('隔离执行',()=>pythonSkillForm(skill));run.disabled=!skill.enabled;controls.append(run);}controls.append(button('编辑',()=>skillForm(skill)),button(skill.enabled?'停用':'启用',async()=>{await api('/skills/'+skill.id,{method:'PUT',body:{...skill,enabled:!skill.enabled}});await refresh();}),button('导出',()=>downloadText('skill-'+skill.id+(skill.kind==='PYTHON'?'.py':'.md'),skill.kind==='PYTHON'?skill.code:skill.instructions)),button('删除',async()=>{if(!confirm('删除技能「'+skill.name+'」？已绑定的技能需要先解除绑定。'))return;await api('/skills/'+skill.id,{method:'DELETE'});await refresh();},'danger'));card.append(controls);cards.append(card);}
    if(!matches.length)cards.append(empty('暂无匹配技能','从模板创建，或导入自己的 Markdown 指令。'));};
  render();root.replaceChildren(toolbar,node('p','指令模板加入模型提示词；Python Skills 仅在明确点击或工作流节点中隔离执行，不会自动执行模型生成的代码。每个智能体最多绑定 10 个技能。','muted'),cards);
}
function templatePicker(){const content=modal('选择技能模板');for(const template of skillTemplates){const card=node('div',undefined,'template-row');card.append(node('h3',template.name),node('p',template.description,'muted'),button('使用此模板',()=>skillForm({...template,enabled:true}),'primary'));content.append(card);}}
function importSkill(){const picker=node('input');picker.type='file';picker.accept='.md,.txt,.py,text/plain,text/markdown';picker.addEventListener('change',async()=>{try{const file=picker.files[0];if(!file)return;if(file.size>40000)throw new Error('文件过大，请限制为 10000 字符以内');const instructions=await file.text();if(instructions.length>10000||!instructions.trim())throw new Error('技能内容须为 1–10000 字符');const python=/\.py$/i.test(file.name);skillForm({name:file.name.replace(/\.(md|txt|py)$/i,'').slice(0,100),instructions:python?'仅在隔离容器中明确执行此 Python Skill。':instructions,kind:python?'PYTHON':'PROMPT',code:python?instructions:null,enabled:true});}catch(error){showError(error);}});picker.click();}
function skillForm(skill){
  const content=modal(skill?.id?'编辑技能':'创建技能'),form=node('form');
  field(form,'技能名称','name',skill?.name,{max:100});field(form,'说明','description',skill?.description,{max:500,required:false});field(form,'技能指令（支持 Markdown）','instructions',skill?.instructions,{multiline:true,max:10000});
  const kindLabel=node('label','技能类型'),kind=select([['PROMPT','指令模板'],['PYTHON','Python · 隔离执行']],skill?.kind||'PROMPT',()=>updateKind());kind.name='kind';kindLabel.append(kind);form.append(kindLabel);const code=field(form,'Python 代码（通过 input_text 读取输入）','code',skill?.code||'print(input_text)',{multiline:true,max:10000,required:false});const updateKind=()=>{code.parentElement.hidden=kind.value!=='PYTHON';code.required=kind.value==='PYTHON';};updateKind();form.append(node('p','Python 运行需 Docker 和预置镜像；无网络、无主机目录访问，最多 10 秒。绑定 Python Skill 不会让模型自动执行代码。','muted'));
  const wrap=node('label',undefined,'check'),enabled=node('input');enabled.type='checkbox';enabled.name='enabled';enabled.checked=skill?.enabled??true;wrap.append(enabled,document.createTextNode('启用此技能'));form.append(wrap);
  formFooter(form,'保存技能',async data=>{await api('/skills'+(skill?.id?'/'+skill.id:''),{method:skill?.id?'PUT':'POST',body:{...data,code:data.kind==='PYTHON'?data.code:null,enabled:!!data.enabled,version:skill?.version??null}});$('#modal').close();toast('技能已保存，可在智能体的 Skills 中绑定');await refresh();});content.append(form);
}
async function bindingForm(agent){
  const content=modal(agent.name+' · Skills'),dialog=state.dialogEpoch;
  const snapshot=await api('/agents/'+agent.id+'/skills/snapshot');if(!$('#modal').open||state.dialogEpoch!==dialog)return;
  const form=node('form'),list=node('div');let selected=[...snapshot.skills],available=[],sequence=0,timer;
  content.append(node('p','指令模板按下列顺序加入提示词；Python Skills 需在技能页或工作流中明确执行。停用的技能保留绑定，但不加入后续任务。保存时校验版本，防止覆盖其他窗口的修改。','muted'));
  const choices=select([['','选择要添加的技能']],'',()=>{});choices.setAttribute('aria-label','选择技能');
  const search=node('input');search.type='search';search.maxLength=100;search.placeholder='搜索技能';search.setAttribute('aria-label','搜索可绑定技能');
  const toolbar=node('div',undefined,'toolbar'),pager=node('div');
  toolbar.append(search,choices,button('添加技能',()=>{const skill=available.find(s=>String(s.id)===choices.value);if(!skill)return;if(selected.some(s=>s.id===skill.id))throw new Error('该技能已经添加');if(selected.length>=10)throw new Error('最多绑定 10 个技能');selected.push(skill);render();}));
  const load=async page=>{const ticket=++sequence;const data=await api(pagePath('/agents/'+agent.id+'/skills/available/page',{page,q:search.value.trim()}));if(ticket!==sequence||!form.isConnected)return;available=data.items;choices.replaceChildren();for(const [key,label]of [['','选择要添加的技能'],...available.map(s=>[String(s.id),s.name+(s.enabled?'':'（已停用）')])]){const option=node('option',label);option.value=key;choices.append(option);}pager.replaceChildren(pageControls(data,load));};
  const render=()=>{list.replaceChildren();selected.forEach((skill,index)=>{const row=node('div',undefined,'binding-row');row.append(node('span',(index+1)+'. '+skill.name+(skill.enabled?'':'（已停用）')));const controls=node('div');const up=button('↑',()=>{[selected[index-1],selected[index]]=[selected[index],selected[index-1]];render();});up.disabled=index===0;up.setAttribute('aria-label','上移 '+skill.name);const down=button('↓',()=>{[selected[index+1],selected[index]]=[selected[index],selected[index+1]];render();});down.disabled=index===selected.length-1;down.setAttribute('aria-label','下移 '+skill.name);controls.append(up,down,button('移除',()=>{selected.splice(index,1);render();}));row.append(controls);list.append(row);});if(!selected.length)list.append(node('p','尚未绑定技能。可先到技能库创建或导入。','muted'));};
  search.addEventListener('input',()=>{clearTimeout(timer);sequence++;timer=setTimeout(()=>load(0).catch(showError),250);});render();form.append(toolbar,pager,list);
  formFooter(form,'保存绑定',async()=>{await api('/agents/'+agent.id+'/skills',{method:'PUT',body:{skillIds:selected.map(s=>s.id),version:snapshot.version}});$('#modal').close();toast('技能绑定已更新');await refresh();});content.append(form);await load(0);
}
function modelCatalogPanel(endpoint,settings,modelInput){
  const panel=node('section',undefined,'model-catalog'),status=node('p','仅查询已保存的服务地址与密钥；不会发送对话。服务商不支持 /models 时仍可手动填写模型 ID。','muted');
  const choice=node('select');choice.setAttribute('aria-label','服务商模型');choice.hidden=true;
  const filter=node('input');filter.type='search';filter.placeholder='筛选模型列表';filter.setAttribute('aria-label','筛选模型列表');filter.maxLength=100;filter.hidden=true;let models=[];
  const draw=()=>{choice.replaceChildren();const q=filter.value.trim().toLowerCase();for(const model of models.filter(id=>id.toLowerCase().includes(q))){const option=node('option',model);option.value=model;choice.append(option);}use.disabled=!choice.options.length;};
  const use=button('填入模型 ID',()=>{modelInput.value=choice.value;modelInput.dispatchEvent(new Event('input',{bubbles:true}));toast('模型 ID 已填入，请保存配置后使用');});use.hidden=true;
  const read=button('读取已保存配置的模型',async()=>{read.disabled=true;status.textContent='正在读取模型列表…';try{const result=await api(endpoint+'/models',{method:'POST'});if(!panel.isConnected)return;models=result.models;filter.hidden=choice.hidden=use.hidden=false;draw();status.textContent='已读取 '+models.length+' 个模型'+(result.truncated?'（服务商仍有更多结果，未列出的 ID 可手动填写）':'')+'。选择后仍需保存配置。';}catch(error){if(panel.isConnected)status.textContent=error.message;}finally{if(panel.isConnected)read.disabled=false;}});
  read.disabled=settings.mode!=='API'||!settings.hasKey;filter.addEventListener('input',draw);panel.append(node('h3','服务商模型目录'),status,read,filter,choice,use);return panel;
}
function renderSettings(settings,personal=false){
  const endpoint=personal?'/me/model-settings':'/admin/model-settings',page=personal?'personal':'settings',userId=state.user.id;
  const form=node('form',undefined,'settings-form'),status=node('div',undefined,'banner'),inherited=settings.mode==='INHERIT';
  status.textContent=(inherited?'正在使用平台默认配置':settings.mode==='API'?'当前为真实 API 模式':'当前为模拟模式')+' · '+(settings.hasKey?'密钥已保存，留空表示保留':'尚未保存密钥')+(state.system.ephemeral?' · 演示环境重启后会清空':' · 配置保存在数据库，密钥加密存储');
  form.append(node('h2',personal?'个人模型连接':'平台默认模型'),status);
  const providerLabel=node('label','模型服务商'),providerNote=node('p','','muted'),providerDocs=node('a','查看官方接入说明 ↗','console-link');providerDocs.target='_blank';providerDocs.rel='noopener noreferrer';
  const describeProvider=p=>{providerNote.textContent=p.note+' 当前仅接入文本对话，余额查询能力与聊天接入独立。';providerDocs.hidden=!p.docs;if(p.docs)providerDocs.href=p.docs;else providerDocs.removeAttribute('href');};
  const currentProvider=matchingModelProvider(settings.baseUrl,settings.protocol||'OPENAI');
  const provider=select(modelProviders.map(p=>[p.id,p.name]),currentProvider.id,()=>{
    const p=modelProviders.find(p=>p.id===provider.value);describeProvider(p);if(p.id==='custom')return;
    form.elements.mode.value='API';form.elements.protocol.value=p.protocol;form.elements.baseUrl.value=p.url;form.elements.defaultModel.value=p.model||'';form.elements.apiKey.value='';form.elements.clearKey.checked=false;
    form.dispatchEvent(new Event('input',{bubbles:true}));toast('已填入服务地址与协议；请填写对应的 Key 和模型 ID 后保存');
  });provider.id='model-provider';providerLabel.append(provider);form.append(providerLabel,providerNote,providerDocs);describeProvider(currentProvider);
  const modeLabel=node('label','执行模式'),mode=select([['DEMO','模拟响应'],['API','真实 API 调用']],inherited?'API':settings.mode,()=>{});mode.name='mode';modeLabel.append(mode);form.append(modeLabel);
  const protocolLabel=node('label','接口协议'),protocol=select([['OPENAI','OpenAI 兼容 · Chat Completions'],['ANTHROPIC','Anthropic 原生 · Messages']],settings.protocol||'OPENAI',()=>{});protocol.name='protocol';protocolLabel.append(protocol);form.append(protocolLabel);
  field(form,'服务地址（API 前缀）','baseUrl',settings.baseUrl,{max:1000,required:false,placeholder:'https://api.example.com/v1'});
  const key=field(form,'API Key','apiKey','',{max:2000,required:false,placeholder:settings.hasKey?'已保存，输入新密钥可替换':'输入服务商提供的 API Key'});key.type='password';key.autocomplete='new-password';
  const modelInput=field(form,'默认模型 ID','defaultModel',settings.defaultModel,{max:100,required:false,placeholder:'服务商支持的模型名称，如 deepseek-chat'});
  const timeout=field(form,'请求超时（秒）','timeoutSeconds',settings.timeoutSeconds);timeout.type='number';timeout.min=1;timeout.max=120;
  const clearLabel=node('label',undefined,'check'),clear=node('input');clear.type='checkbox';clear.name='clearKey';clearLabel.append(clear,document.createTextNode('清除已保存密钥（需切换为模拟模式）'));
  const syncProvider=()=>{const p=matchingModelProvider(form.elements.baseUrl.value,protocol.value);provider.value=p.id;describeProvider(p);};form.elements.baseUrl.addEventListener('input',syncProvider);protocol.addEventListener('change',syncProvider);
  form.append(node('p','填 API 前缀，不要附加 /chat/completions 或 /messages。模型 ID 以服务商控制台为准；保存后可读取模型目录。未列出的厂商也可通过自定义地址接入。','muted'));
  form.append(clearLabel,node('p',personal?'仅影响你提交的任务与对话，使用此处的模型 ID。个人配置失败不会自动转用平台 Key；修改服务地址时必须重新填写密钥。':'供未配置个人模型的用户使用。修改服务地址必须重新填写密钥，保存后不会自动发起请求。','muted'));
  const feedback=node('p','','error');feedback.setAttribute('role','status');
  const controls=node('div',undefined,'form-actions'),save=node('button','保存配置','primary');save.type='submit';
  const test=button('测试已保存配置',async()=>{
    if(!confirm('将使用已保存的 API 配置发起一次简短测试请求，服务商可能计费。继续？'))return;
    test.disabled=true;feedback.textContent='正在连接模型…';
    try{const result=await api(endpoint+'/test',{method:'POST'});feedback.textContent=result.message;feedback.className=result.success?'success':'error';}catch(error){feedback.textContent=error.message;}finally{test.disabled=false;}
  });test.disabled=settings.mode!=='API';controls.append(test,save);
  const disconnect=button('断开并清除连接',async()=>{
    if(!confirm('清除本配置的 API Key、服务地址和模型 ID，并切回模拟模式？历史对话和成品保留；旧的排队 API 任务不会继续使用旧密钥。'))return;
    disconnect.disabled=true;try{await api(endpoint,{method:'PUT',body:{mode:'DEMO',baseUrl:'',apiKey:'',defaultModel:'demo-model',protocol:'OPENAI',timeoutSeconds:60,clearKey:true,revision:settings.revision}});if(state.user?.id!==userId||state.page!==page)return;await refreshSystem();await refresh();toast('连接已清除；服务商处的 Key 未被注销');}finally{disconnect.disabled=false;}
  },'danger');disconnect.disabled=inherited||(!settings.hasKey&&!settings.baseUrl&&settings.mode!=='API');controls.prepend(disconnect);
  if(personal&&!inherited)controls.prepend(button('恢复平台默认',async()=>{
    if(!confirm('删除已保存的个人 Key 并恢复平台默认配置？已排队的 API 任务可能因配置变更而失败。'))return;
    await api(endpoint+'/inherit',{method:'POST',body:{revision:settings.revision}});if(state.user?.id!==userId||state.page!==page)return;await refreshSystem();await refresh();
  }));
  const catalog=modelCatalogPanel(endpoint,settings,modelInput);form.append(feedback,controls,catalog);
  form.addEventListener('submit',async event=>{
    event.preventDefault();save.disabled=true;feedback.textContent='';const data=Object.fromEntries(new FormData(form));
    try{const result=await api(endpoint,{method:'PUT',body:{...data,timeoutSeconds:Number(data.timeoutSeconds),clearKey:!!data.clearKey,revision:settings.revision}});key.value='';if(state.user?.id!==userId||state.page!==page)return;await refreshSystem();if(state.user?.id===userId&&state.page===page)renderSettings(result,personal);toast('模型配置已保存');}
    catch(error){feedback.textContent=error.message;}finally{save.disabled=false;}
  });
  const grid=node('div',undefined,'settings-grid');grid.append(form,balancePanel(endpoint,settings.mode==='API'&&settings.hasKey));$('#content').replaceChildren(grid);
  const dirty=event=>{if(event.target!==form&&!event.target.name)return;test.disabled=true;catalog.querySelector('button').disabled=true;grid.querySelector('.balance-panel button').disabled=true;feedback.textContent='配置已修改，请先保存，再测试连接、读取模型或查询余额。';};form.addEventListener('input',dirty);form.addEventListener('change',dirty);
  if(personal){const toolbar=node('div',undefined,'toolbar');toolbar.append(button('查看调用用量',()=>navigate('usage')));$('#content').prepend(toolbar);}
}
