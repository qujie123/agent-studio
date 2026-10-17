const {chromium}=require('playwright');
const {go}=require('./browser-navigation.cjs');
const http=require('node:http');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const base=process.env.APP_URL||'http://127.0.0.1:18080';
async function run(){
  let calls=0,received,fail=false;
  const provider=http.createServer((req,res)=>{let data='';req.on('data',c=>data+=c);req.on('end',()=>{calls++;received=JSON.parse(data);setTimeout(()=>{res.setHeader('Content-Type','application/json');if(fail){fail=false;res.writeHead(429);res.end('{}');return;}res.end(JSON.stringify({choices:[{message:{content:'History messages: '+received.messages.length+'; answer: '+received.messages.at(-1).content},finish_reason:'stop'}]}));},350);});});
  await new Promise(resolve=>provider.listen(0,'127.0.0.1',resolve));
  const browser=await chromium.launch({headless:true,executablePath:process.env.EDGE_PATH||'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'});
  const page=await browser.newPage({viewport:{width:1440,height:1050}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  let token;
  async function api(route,method='GET',data){const r=await fetch(base+'/api'+route,{method,headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:data?JSON.stringify(data):undefined});assert(r.ok,route+' '+r.status);return r.status===204?null:r.json();}
  try{
    await page.goto(base);await page.locator('#auth-form [name=username]').fill('demo_admin');await page.locator('#auth-form [name=password]').fill('DemoOnly_2026!');await page.locator('#auth-submit').click();await page.locator('#workspace').waitFor();
    token=await page.evaluate(()=>sessionStorage.getItem('agent-token'));let config=await api('/admin/model-settings');
    await api('/admin/model-settings','PUT',{mode:'API',baseUrl:'http://127.0.0.1:'+provider.address().port+'/v1',apiKey:'browser-chat-test-key',defaultModel:'test-chat',timeoutSeconds:5,revision:config.revision,clearKey:false});
    const agent=await api('/agents','POST',{name:'Conversation QA '+Date.now(),description:'Browser test',systemPrompt:'Answer accurately.',modelName:'test-chat',status:'ACTIVE'});
    await page.reload();await page.locator('#workspace').waitFor();await go(page,'chats');await page.locator('#primary-action').click();await page.locator('#modal select[name=agentId]').selectOption(String(agent.id));await page.locator('#modal button[type=submit]').click();await page.getByRole('textbox',{name:'消息',exact:true}).waitFor();
    const input=()=>page.getByRole('textbox',{name:'消息',exact:true});
    async function send(text){await input().fill(text);await page.getByRole('button',{name:'发送消息',exact:true}).click();await page.getByRole('button',{name:'发送消息',exact:true}).waitFor();await page.waitForFunction(()=>!document.querySelector('.chat-composer button[type=submit]').disabled);}
    await send('My name is Alice');assert.equal(calls,1,'Unexpected provider count; chat view: '+await page.locator('.chat-messages').innerText());
    await send('What is my name?');assert.equal(received.messages.length,4);assert.equal(received.messages[1].content,'My name is Alice');assert.equal(received.messages[2].role,'assistant');
    await input().fill('unsent draft');await go(page,'agents');await go(page,'chats');assert.equal(await input().inputValue(),'unsent draft');
    await page.getByRole('button',{name:'重命名',exact:true}).click();await page.locator('#modal [name=title]').fill('多轮对话验收');await page.locator('#modal button[type=submit]').click();await page.locator('.chat-heading h2').filter({hasText:'多轮对话验收'}).waitFor();
    await page.reload();await page.locator('#workspace').waitFor();await go(page,'chats');await page.locator('.chat-heading h2').filter({hasText:'多轮对话验收'}).waitFor();assert.equal(await page.locator('.chat-bubble.user').count(),2);
    fail=true;await send('retry this');await page.getByRole('button',{name:'重新发送此消息',exact:true}).click();assert.equal(await input().inputValue(),'retry this');await send('retry this');assert.equal(received.messages.length,6);
    await page.route('**/api/chats/*/messages',async route=>{await route.fetch();await route.abort('failed');});
    const before=calls;await input().fill('transport acknowledgement lost');await page.getByRole('button',{name:'发送消息',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.chat-messages')?.textContent.includes('answer: transport acknowledgement lost'));await page.unrouteAll({behavior:'wait'});assert.equal(calls,before+1);
    await send('<img src=x onerror="window.chatXss=true">');assert.equal(await page.evaluate(()=>window.chatXss),undefined);assert.equal(await page.locator('.chat-bubble img').count(),0);
    const download=page.waitForEvent('download');await page.getByRole('button',{name:'导出',exact:true}).click();assert((await download).suggestedFilename().startsWith('conversation-'));
    fs.mkdirSync(path.join(__dirname,'..','target','verification'),{recursive:true});
    await page.locator('#toast').waitFor({state:'hidden'});await page.screenshot({path:path.join(__dirname,'..','target','verification','chat-desktop.png'),fullPage:true});
    await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(__dirname,'..','target','verification','chat-mobile.png'),fullPage:true});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
    assert.deepEqual(errors,[]);console.log('PASS chat UI: ordered history, reload, drafts, rename, retry, lost acknowledgement, export, XSS text, mobile');
  }finally{
    if(token){const config=await api('/admin/model-settings');await api('/admin/model-settings','PUT',{mode:'DEMO',baseUrl:'',apiKey:'',defaultModel:'demo-model',timeoutSeconds:60,revision:config.revision,clearKey:true});}
    await browser.close();await new Promise(resolve=>provider.close(resolve));
  }
}
run().catch(error=>{console.error(error);process.exitCode=1;});
