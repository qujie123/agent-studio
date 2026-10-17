const {chromium} = require('playwright');
const {go,agentAction}=require('./browser-navigation.cjs');
const http = require('node:http');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const base = process.env.APP_URL || 'http://127.0.0.1:18080';
const artifacts = path.join(__dirname,'..','target','verification');
async function run(){
  let received;
  const provider=http.createServer((req,res)=>{let data='';req.on('data',chunk=>data+=chunk);req.on('end',()=>{received=JSON.parse(data);res.setHeader('Content-Type','application/json');res.end(JSON.stringify({choices:[{message:{content:'Adapter result: '+(received.messages[0].content.includes('browser skill marker')?'skill applied':'connection OK')},finish_reason:'stop'}]}));});});
  await new Promise(resolve=>provider.listen(0,'127.0.0.1',resolve));
  const browser=await chromium.launch({headless:true,executablePath:process.env.EDGE_PATH||'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'});
  const context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage();
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  const suffix=Date.now(),skillName='Browser Skill '+suffix,agentName='Skilled Agent '+suffix;
  let token;
  try{
    await page.goto(base);
    await page.locator('#auth-form [name=username]').fill('demo_admin');await page.locator('#auth-form [name=password]').fill('DemoOnly_2026!');await page.locator('#auth-submit').click();
    await page.getByRole('heading',{name:'工作台'}).waitFor();token=await page.evaluate(()=>sessionStorage.getItem('agent-token'));
    await go(page,'settings');
    await page.locator('[name=mode]').selectOption('API');
    await page.locator('[name=baseUrl]').fill('http://127.0.0.1:'+provider.address().port+'/v1');
    await page.locator('[name=apiKey]').fill('browser-test-key');await page.locator('[name=defaultModel]').fill('qa-model');
    await page.getByRole('button',{name:'保存配置',exact:true}).click();
    await page.locator('[name=apiKey]').filter({visible:true}).waitFor();
    await page.waitForFunction(()=>document.querySelector('[name=apiKey]').value==='');
    page.once('dialog',dialog=>dialog.accept());await page.getByRole('button',{name:'测试已保存配置'}).click();
    await page.getByText('连接成功，模型已返回有效文本').waitFor();
    const settings=await page.request.get(base+'/api/admin/model-settings',{headers:{Authorization:'Bearer '+token}});
    const settingsText=await settings.text();assert(!settingsText.includes('browser-test-key'));assert(!settingsText.includes('encryptedKey'));
    await page.locator('#toast').waitFor({state:'hidden'});fs.mkdirSync(artifacts,{recursive:true});await page.screenshot({path:path.join(artifacts,'model-settings.png'),fullPage:true});
    await page.route('**/api/agents',async route=>{await new Promise(resolve=>setTimeout(resolve,500));await route.continue();});
    await go(page,'agents');
    await go(page,'skills');await page.getByRole('button',{name:'导入技能文件',exact:true}).waitFor();
    await page.unrouteAll({behavior:'wait'});await page.locator('#primary-action').click();
    await page.locator('#modal [name=name]').fill(skillName);await page.locator('#modal [name=description]').fill('浏览器技能验收');await page.locator('#modal [name=instructions]').fill('browser skill marker: always explain how to run the code.');await page.locator('#modal button[type=submit]').click();
    await page.getByRole('heading',{name:skillName,exact:true}).waitFor();
    const chooserPromise=page.waitForEvent('filechooser');await page.getByRole('button',{name:'导入技能文件',exact:true}).click();
    const chooser=await chooserPromise;const importedName='Imported Skill '+suffix;
    await chooser.setFiles({name:importedName+'.md',mimeType:'text/markdown',buffer:Buffer.from('Imported reusable instructions')});
    await page.locator('#modal [name=name]').waitFor();await page.locator('#modal button[type=submit]').click();
    const importedCard=page.locator('article.card').filter({has:page.getByRole('heading',{name:importedName,exact:true})});
    await importedCard.getByRole('button',{name:'停用',exact:true}).click();await importedCard.locator('.badge.STOPPED').waitFor();
    await importedCard.getByRole('button',{name:'启用',exact:true}).click();await importedCard.locator('.badge.ACTIVE').waitFor();
    const exportPromise=page.waitForEvent('download');await importedCard.getByRole('button',{name:'导出',exact:true}).click();assert((await exportPromise).suggestedFilename().endsWith('.md'));
    await go(page,'agents');await page.locator('#primary-action').click();
    await page.locator('#modal [name=name]').fill(agentName);await page.locator('#modal [name=systemPrompt]').fill('Follow attached skills');
    assert.equal(await page.locator('#modal [name=modelName]').inputValue(),'qa-model');
    await page.locator('#modal [name=status]').selectOption('ACTIVE');await page.locator('#modal button[type=submit]').click();
    const card=page.locator('article.card').filter({has:page.getByRole('heading',{name:agentName,exact:true})});await agentAction(page,card,'Skills');
    await page.getByLabel('选择技能').selectOption({label:skillName});await page.getByRole('button',{name:'添加技能',exact:true}).click();await page.getByRole('button',{name:'保存绑定',exact:true}).click();
    await card.getByText('1 Skills',{exact:false}).waitFor();await card.getByRole('button',{name:'新建任务'}).click();
    await page.locator('#modal [name=input]').fill('Use my skill');await page.locator('#modal button[type=submit]').click();
    await page.locator('tbody tr').filter({hasText:'Use my skill'}).first().getByRole('button',{name:'查看详情'}).click();
    await page.locator('#modal .badge.SUCCEEDED').waitFor({timeout:15000});await page.getByText('Adapter result: skill applied',{exact:true}).waitFor();
    await page.locator('#modal').getByText(skillName+' · v1',{exact:true}).waitFor();
    const downloadPromise=page.waitForEvent('download');await page.getByRole('button',{name:'下载回答文本',exact:true}).click();const download=await downloadPromise;assert(download.suggestedFilename().endsWith('.md'));
    await page.locator('#modal-close').click();await go(page,'skills');await page.getByRole('heading',{name:skillName,exact:true}).waitFor();
    await page.locator('#toast').waitFor({state:'hidden'});await page.screenshot({path:path.join(artifacts,'skills.png'),fullPage:true});
    assert.deepEqual(errors,[]);console.log('Feature browser test passed: encrypted settings, saved connection test, skill create/bind, API execution with skill, snapshot, download.');
  }finally{
    if(token){const headers={Authorization:'Bearer '+token};const current=await page.request.get(base+'/api/admin/model-settings',{headers}).then(r=>r.json());await page.request.put(base+'/api/admin/model-settings',{headers,data:{mode:'DEMO',baseUrl:'',apiKey:'',defaultModel:'demo-model',timeoutSeconds:60,clearKey:true,revision:current.revision}});}
    await browser.close();await new Promise(resolve=>provider.close(resolve));
  }
}
run().catch(error=>{console.error(error);process.exitCode=1;});
