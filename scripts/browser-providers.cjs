'use strict';
const {chromium}=require('playwright'),http=require('node:http'),assert=require('node:assert/strict'),crypto=require('node:crypto'),fs=require('node:fs'),path=require('node:path');
const {go}=require('./browser-navigation.cjs');
const base=process.env.APP_URL||'http://127.0.0.1:18080';
async function run(){
  let calls=0;const requests=[];
  const provider=http.createServer((req,res)=>{let body='';req.on('data',chunk=>body+=chunk);req.on('end',()=>{
    calls++;requests.push({url:req.url,headers:req.headers,body:body?JSON.parse(body):null});res.setHeader('Content-Type','application/json');
    if(req.url==='/v1/models'){res.end(JSON.stringify({data:[{id:'native-test'}],has_more:false}));return;}
    const payload=JSON.parse(body);
    if(req.url==='/v1/messages'&&payload.stream){res.setHeader('Content-Type','text/event-stream');for(const value of [{type:'message_start',message:{usage:{input_tokens:13,output_tokens:0}}},{type:'content_block_delta',delta:{type:'text_delta',text:'Native browser reply'}},{type:'message_delta',delta:{stop_reason:'end_turn'},usage:{output_tokens:7}},{type:'message_stop'}])res.write('data: '+JSON.stringify(value)+'\n\n');res.end();return;}
    if(req.url==='/v1/messages')res.end(JSON.stringify({content:[{type:'text',text:'Native browser reply'}],stop_reason:'end_turn',usage:{input_tokens:13,output_tokens:7}}));
    else res.end(JSON.stringify({choices:[{message:{content:'Compatible browser reply'},finish_reason:'stop'}],usage:{prompt_tokens:13,completion_tokens:7}}));
  });});
  await new Promise(resolve=>provider.listen(0,'127.0.0.1',resolve));const url='http://127.0.0.1:'+provider.address().port+'/v1';
  const browser=await chromium.launch({headless:true,executablePath:process.env.EDGE_PATH||'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'});
  const page=await browser.newPage({viewport:{width:1440,height:1100}}),errors=[];page.on('pageerror',e=>errors.push(e.message));let token,chatId;
  async function api(route,method='GET',data){const r=await fetch(base+'/api'+route,{method,headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:data?JSON.stringify(data):undefined});assert(r.ok,route+' '+r.status);return r.status===204?null:r.json();}
  async function saved(){await page.waitForFunction(()=>document.querySelector('[name=apiKey]').value==='');}
  async function configure(protocol){await page.locator('[name=protocol]').selectOption(protocol);await page.locator('[name=mode]').selectOption('API');await page.locator('[name=baseUrl]').fill(url);await page.locator('[name=defaultModel]').fill('native-test');await page.locator('[name=apiKey]').fill('fake-browser-secret');await page.getByRole('button',{name:'保存配置',exact:true}).click();await saved();}
  try{
    await page.goto(base);await page.locator('#auth-form [name=username]').fill('demo_admin');await page.locator('#auth-form [name=password]').fill('DemoOnly_2026!');await page.locator('#auth-submit').click();await page.locator('#workspace').waitFor();token=await page.evaluate(()=>state.token);
    assert.equal(await page.evaluate(()=>state.system.ephemeral),true,'Never mutate the real local workspace in this test');
    for(const name of ['settings','personal']){
      await go(page,name);await page.locator('#model-provider').waitFor();
      const presets=await page.evaluate(()=>modelProviders);assert.equal(presets.length,15);
      for(const preset of presets.filter(p=>p.id!=='custom')){
        await page.locator('[name=apiKey]').fill('draft-secret-not-to-forward');await page.locator('#model-provider').selectOption(preset.id);
        assert.equal(await page.locator('[name=baseUrl]').inputValue(),preset.url);assert.equal(await page.locator('[name=protocol]').inputValue(),preset.protocol);assert.equal(await page.locator('[name=apiKey]').inputValue(),'');
        assert.equal(await page.getByRole('button',{name:'测试已保存配置'}).isDisabled(),true);
      }
    }
    assert.equal(calls,0,'Choosing presets must not call providers');
    await go(page,'settings');await configure('ANTHROPIC');
    const config=await api('/admin/model-settings');assert.equal(config.protocol,'ANTHROPIC');assert.equal(config.hasKey,true);assert(!JSON.stringify(config).includes('fake-browser-secret'));
    await page.getByRole('button',{name:'读取已保存配置的模型',exact:true}).click();await page.getByLabel('服务商模型').waitFor();await page.getByRole('button',{name:'填入模型 ID',exact:true}).click();assert.equal(await page.getByRole('button',{name:'测试已保存配置'}).isDisabled(),true);
    await page.getByRole('button',{name:'保存配置',exact:true}).click();await page.waitForFunction(()=>!document.querySelector('.settings-form')?.textContent.includes('配置已修改，请先保存'));
    page.once('dialog',d=>d.accept());await page.getByRole('button',{name:'测试已保存配置'}).click();await page.getByText('连接成功，模型已返回有效文本',{exact:true}).waitFor();
    const agent=await api('/agents','POST',{name:'Native protocol QA '+Date.now(),description:'isolated fixture',systemPrompt:'system instructions',modelName:'native-test',status:'ACTIVE',version:0});
    const task=await api('/tasks','POST',{agentId:agent.id,input:'native task'});const result=await api('/tasks/'+task.id+'/execute','POST');assert.equal(result.output,'Native browser reply');
    const chat=await api('/chats','POST',{agentId:agent.id});chatId=chat.id;const first=await api('/chats/'+chat.id+'/messages','POST',{requestId:crypto.randomUUID(),input:'first'});assert.equal(first.output,'Native browser reply');
    const stream=await fetch(base+'/api/chats/'+chat.id+'/messages/stream',{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify({requestId:crypto.randomUUID(),input:'second'})});assert(stream.ok);const streamText=await stream.text();assert(streamText.includes('Native browser reply'));assert(streamText.includes('SUCCEEDED'));
    assert.equal(requests.at(-1).body.messages.length,3);assert.equal(requests.at(-1).body.system,'system instructions');
    for(const req of requests){assert.equal(req.headers['x-api-key'],'fake-browser-secret');assert.equal(req.headers.authorization,undefined);}
    fs.mkdirSync(path.join(__dirname,'../target/verification'),{recursive:true});await page.screenshot({path:path.join(__dirname,'../target/verification/provider-settings-desktop.png'),fullPage:true});
    await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(__dirname,'../target/verification/provider-settings-mobile.png'),fullPage:true});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    page.once('dialog',d=>d.accept());await page.getByRole('button',{name:'断开并清除连接',exact:true}).click();await page.waitForFunction(()=>document.querySelector('[name=mode]')?.value==='DEMO');
    const cleared=await api('/admin/model-settings');assert.equal(cleared.hasKey,false);assert.equal(cleared.baseUrl,'');assert.equal(cleared.defaultModel,'demo-model');assert.equal((await api('/chats/'+chat.id)).turns.length,2);
    await go(page,'personal');await configure('OPENAI');page.once('dialog',d=>d.accept());await page.getByRole('button',{name:'测试已保存配置'}).click();await page.getByText('连接成功，模型已返回有效文本',{exact:true}).waitFor();assert.equal(requests.at(-1).headers.authorization,'Bearer fake-browser-secret');assert.equal(requests.at(-1).headers['x-api-key'],undefined);
    page.once('dialog',d=>d.accept());await page.getByRole('button',{name:'恢复平台默认'}).click();await page.waitForFunction(()=>document.querySelector('.settings-form')?.textContent.includes('正在使用平台默认配置'));const personal=await api('/me/model-settings');assert.equal(personal.hasKey,false);assert.equal(personal.baseUrl,'');
    assert.deepEqual(errors,[]);console.log('Provider browser verification passed: 14 presets + custom in both scopes, key isolation, native/compatible tests, model discovery, task, multi-turn + streaming chat, disconnect preserves history, mobile layout.');
  }finally{
    if(token&&chatId)await api('/chats/'+chatId,'DELETE');
    if(token){try{const current=await api('/admin/model-settings');await api('/admin/model-settings','PUT',{mode:'DEMO',baseUrl:'',apiKey:'',defaultModel:'demo-model',protocol:'OPENAI',timeoutSeconds:60,clearKey:true,revision:current.revision});const personal=await api('/me/model-settings');if(personal.mode!=='INHERIT')await api('/me/model-settings/inherit','POST',{revision:personal.revision});}catch{}}
    await browser.close();await new Promise(resolve=>provider.close(resolve));
  }
}
run().catch(e=>{console.error(e);process.exitCode=1;});
