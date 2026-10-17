'use strict';
const {go,setTheme,agentAction}=require('./browser-navigation.cjs');
const {chromium}=require('playwright'),assert=require('node:assert/strict'),http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const base=process.env.APP_URL||'http://127.0.0.1:18080';
async function run(){
  let modelCalls=0,chatCalls=0,payload;
  const markdown='# Example\n\n**Safe answer** with `inline` code.\n\n| Key | Value |\n| --- | --- |\n| x | 1 |\n\n```python\nprint("hello")\n```\n\n<img src=x onerror="window.injection=true">\n<script>window.injection=true</script>\n[unsafe](javascript:alert(1))\n[documentation](https://example.com/docs)';
  const provider=http.createServer((req,res)=>{
    res.setHeader('Content-Type','application/json');
    if(req.url==='/v1/models'){modelCalls++;assert.equal(req.headers.authorization,'Bearer productivity-fixture-key');res.end(JSON.stringify({data:[{id:'z-model'},{id:'a-model'},{id:'a-model'},{id:''}]}));return;}
    let data='';req.on('data',c=>data+=c);req.on('end',()=>{chatCalls++;payload=JSON.parse(data);res.end(JSON.stringify({choices:[{message:{content:markdown},finish_reason:'stop'}],usage:{prompt_tokens:17,completion_tokens:9}}));});
  });
  await new Promise(resolve=>provider.listen(0,'127.0.0.1',resolve));
  const browser=await chromium.launch({headless:true,executablePath:process.env.EDGE_PATH||'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'});
  const context=await browser.newContext({viewport:{width:1440,height:1050},permissions:['clipboard-read','clipboard-write']});
  const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
  let token;
  async function api(route,method='GET',data){const response=await page.request.fetch(base+'/api'+route,{method,headers:{Authorization:'Bearer '+token},data});assert(response.ok(),route+' '+response.status());return response.status()===204?null:response.json();}
  async function nav(name){await go(page,name);await page.waitForFunction(name=>state.page===name&&!document.querySelector('#content .loading'),name);}
  try{
    await page.goto(base);await page.locator('#auth-form [name=username]').fill('demo_admin');await page.locator('#auth-form [name=password]').fill('DemoOnly_2026!');await page.locator('#auth-submit').click();await page.locator('#workspace').waitFor();token=await page.evaluate(()=>sessionStorage.getItem('agent-token'));
    assert.equal((await api('/system/status')).ephemeral,true,'Run only against an isolated demo');
    const suffix=Date.now(),needle='needle-'+suffix;let agent;
    for(let i=0;i<24;i++){
      const created=await api('/agents','POST',{name:(i===0?needle:'Productivity '+suffix+' '+i),description:'Search coverage',systemPrompt:'Answer.',modelName:'demo-model',status:i===1?'STOPPED':'ACTIVE'});if(i===0)agent=created;
      await api('/skills','POST',{name:(i===0?needle:'Skill '+suffix+' '+i),description:'Search coverage',instructions:'Safe instructions',enabled:true});
    }
    await nav('skills');await page.getByLabel('搜索技能名称或描述').fill(needle);await page.getByRole('heading',{name:needle,exact:true}).waitFor();assert.equal(await page.locator('.card').count(),1,'Search must find an older skill beyond page one');
    await nav('agents');await page.getByLabel('搜索智能体名称或描述').fill(needle);await page.getByRole('heading',{name:needle,exact:true}).waitFor();assert.equal(await page.locator('.card').count(),1);
    await page.getByLabel('智能体状态').selectOption('STOPPED');await page.getByText('还没有智能体',{exact:true}).waitFor();await page.getByLabel('智能体状态').selectOption('ACTIVE');await page.getByRole('heading',{name:needle,exact:true}).waitFor();
    await agentAction(page,page.locator('.card'),'编辑');await page.getByLabel('温度（0–2）').fill('0.35');await page.getByLabel('最大输出 token（1–65536）').fill('512');await page.getByRole('button',{name:'保存智能体',exact:true}).click();await page.locator('#modal').waitFor({state:'hidden'});agent=await api('/agents/'+agent.id);assert.equal(agent.temperature,0.35);assert.equal(agent.maxOutputTokens,512);
    let firstTask;for(let i=0;i<24;i++){const task=await api('/tasks','POST',{agentId:agent.id,input:i===0?needle+' task':'Productivity task '+suffix+' '+i});if(i===0)firstTask=task;}
    await nav('tasks');await page.getByLabel('搜索任务内容或智能体').fill(needle+' task');await page.waitForFunction(id=>document.querySelector('tbody tr td')?.textContent.includes('#'+id+' ·'),firstTask.id);assert.equal(await page.locator('tbody tr').count(),1);
    await page.getByLabel('任务状态').selectOption('SUCCEEDED');await page.getByText('暂无任务',{exact:true}).waitFor();await page.getByLabel('任务状态').selectOption('PENDING');await page.locator('tbody tr').waitFor();
    await nav('personal');await page.getByLabel('执行模式').selectOption('API');await page.getByLabel('服务地址（API 前缀）').fill('http://127.0.0.1:'+provider.address().port+'/v1');await page.getByLabel('API Key',{exact:true}).fill('productivity-fixture-key');await page.getByLabel('默认模型 ID').fill('fixture-model');await page.getByRole('button',{name:'保存配置',exact:true}).click();await page.waitForFunction(()=>!document.querySelector('.model-catalog button').disabled);
    assert.equal(modelCalls,0,'Saving must not auto-fetch models');await page.getByRole('button',{name:'读取已保存配置的模型',exact:true}).click();await page.getByLabel('服务商模型').waitFor();assert.equal(modelCalls,1);assert.equal(chatCalls,0,'Catalog must not call completions');assert.deepEqual(await page.getByLabel('服务商模型').locator('option').allTextContents(),['a-model','z-model']);
    await page.getByLabel('筛选模型列表').fill('z-');await page.getByRole('button',{name:'填入模型 ID',exact:true}).click();assert.equal(await page.getByLabel('默认模型 ID').inputValue(),'z-model');await page.getByRole('button',{name:'保存配置',exact:true}).click();await page.waitForFunction(()=>!document.querySelector('.settings-form button[type=submit]').disabled);
    await page.evaluate(id=>newChat(id),agent.id);await page.getByLabel('消息',{exact:true}).fill('Markdown fixture');await page.getByRole('button',{name:'发送消息',exact:true}).click();await page.locator('.markdown-body h1').waitFor();assert.equal(chatCalls,1);assert.equal(payload.temperature,0.35);assert.equal(payload.max_tokens,512);assert.equal(payload.model,'z-model');
    assert.equal(await page.locator('.markdown-body table tbody tr').count(),1);assert.equal(await page.locator('.markdown-body img,.markdown-body script').count(),0);assert.equal(await page.evaluate(()=>window.injection),undefined);assert.equal(await page.locator('.markdown-body a').count(),1);assert.equal(await page.locator('.markdown-body a').getAttribute('rel'),'noopener noreferrer');
    await page.getByRole('button',{name:'复制代码',exact:true}).click();assert.equal(await page.evaluate(()=>navigator.clipboard.readText()),'print("hello")');
    const chatId=await page.evaluate(()=>chatState.id);await page.getByRole('button',{name:'置顶',exact:true}).click();await page.getByRole('button',{name:'取消置顶',exact:true}).waitFor();assert.equal((await api('/chats/'+chatId+'/session')).pinned,true);
    await page.getByLabel('会话范围').selectOption('pinned');await page.getByRole('button',{name:'归档',exact:true}).click();await page.getByText('开始一段持续的交流',{exact:true}).waitFor();
    await page.getByLabel('会话范围').selectOption('archived');await page.getByRole('button',{name:'恢复会话',exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'发送消息',exact:true}).isDisabled(),true);assert.equal(await page.getByLabel('消息',{exact:true}).isDisabled(),true);assert.equal(chatCalls,1);
    await page.getByRole('button',{name:'恢复会话',exact:true}).click();await page.getByText('开始一段持续的交流',{exact:true}).waitFor();await page.getByLabel('会话范围').selectOption('active');await page.locator('.markdown-body h1').waitFor();assert.equal(await page.getByRole('button',{name:'发送消息',exact:true}).isDisabled(),false);
    await page.getByLabel('搜索会话').fill('detached');await page.evaluate(()=>refresh());await page.getByLabel('会话范围').selectOption('pinned');await page.waitForTimeout(350);assert.equal(await page.evaluate(()=>chatState.view),'pinned');assert.equal(await page.evaluate(()=>chatState.search),'','Detached search timer must not mutate state');
    await setTheme(page,'dark');assert.equal(await page.locator('html').getAttribute('data-theme'),'dark');await page.reload();await page.locator('#workspace').waitFor();assert.equal(await page.getByLabel('界面主题').inputValue(),'dark');
    await nav('chats');await page.locator('.markdown-body h1').waitFor();fs.mkdirSync(path.join(__dirname,'../target/verification'),{recursive:true});await page.screenshot({path:path.join(__dirname,'../target/verification/v1.5-chat-dark.png'),fullPage:true});
    await setTheme(page,'system');await page.emulateMedia({colorScheme:'light'});await page.waitForFunction(()=>document.documentElement.dataset.theme==='light');await page.emulateMedia({colorScheme:'dark'});await page.waitForFunction(()=>document.documentElement.dataset.theme==='dark');await setTheme(page,'light');
    await page.setViewportSize({width:390,height:844});for(const name of ['overview','agents','skills','tasks','chats','personal','users']){await nav(name);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),name+' mobile overflow');}
    await nav('chats');await page.screenshot({path:path.join(__dirname,'../target/verification/v1.5-chat-mobile.png'),fullPage:true});assert.deepEqual(errors,[]);
    console.log('Productivity browser passed: whole-library search, task filters, generation options, catalog, safe Markdown/code copy, pin/archive/restore, detached debounce, themes and mobile layout.');
  }finally{await browser.close();await new Promise(resolve=>provider.close(resolve));}
}
run().catch(error=>{console.error(error);process.exitCode=1;});
