const {chromium}=require('playwright');
const {go,logout}=require('./browser-navigation.cjs');
const http=require('node:http'),assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs');
const base=process.env.APP_URL||'http://127.0.0.1:18080';
const artifacts=path.join(__dirname,'..','target','verification');
async function run(){
  let receivedKey,calls=0;
  const provider=http.createServer((req,res)=>{req.resume();req.on('end',()=>{
    receivedKey=req.headers.authorization;calls++;res.setHeader('Content-Type','application/json');
    res.end(JSON.stringify({choices:[{message:{content:'Private model reply'},finish_reason:'stop'}],usage:{prompt_tokens:17,completion_tokens:9}}));
  });});
  await new Promise(resolve=>provider.listen(0,'127.0.0.1',resolve));
  const browser=await chromium.launch({headless:true,executablePath:process.env.EDGE_PATH||'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'});
  const context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage(),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  const username='managed_'+Date.now(),password='ManagedAccount_2026!',newPassword='UpdatedAccount_2026!';
  async function login(name,pass){
    await page.locator('#auth-form [name=username]').fill(name);await page.locator('#auth-form [name=password]').fill(pass);await page.locator('#auth-submit').click();await page.locator('#workspace').waitFor();
  }
  try{
    fs.mkdirSync(artifacts,{recursive:true});await page.goto(base);await login('demo_admin','DemoOnly_2026!');
    await go(page,'users');await page.locator('#primary-action').click();
    await page.locator('#modal [name=username]').fill(username);await page.locator('#modal [name=displayName]').fill('产品设计协作者');
    await page.locator('#modal [name=email]').fill(username+'@example.com');await page.locator('#modal [name=password]').fill(password);await page.locator('#modal button[type=submit]').click();
    const row=page.locator('tbody tr').filter({hasText:username});await row.waitFor();await page.getByLabel('搜索成员').fill(username);await page.waitForFunction(()=>document.querySelectorAll('tbody tr').length===1);assert.equal(await page.locator('tbody tr').count(),1);
    await row.getByRole('button',{name:'编辑',exact:true}).click();await page.locator('#modal [name=displayName]').fill('测试协作者');await page.locator('#modal button[type=submit]').click();
    await row.getByText('测试协作者',{exact:true}).waitFor();await page.getByLabel('搜索成员').fill(username);await row.getByRole('button',{name:'详情',exact:true}).click();await page.getByText('拥有资源：',{exact:false}).waitFor();
    page.once('dialog',dialog=>dialog.accept());await page.getByRole('button',{name:'停用账号',exact:true}).click();
    await row.locator('.badge.STOPPED').waitFor();await page.getByLabel('筛选状态').selectOption('disabled');
    await row.getByRole('button',{name:'详情',exact:true}).click();page.once('dialog',dialog=>dialog.accept());await page.getByRole('button',{name:'启用账号',exact:true}).click();
    await row.waitFor({state:'hidden'});await page.getByLabel('筛选状态').selectOption('');await row.locator('.badge.ACTIVE').waitFor();await page.getByLabel('搜索成员').fill(username);
    await page.getByLabel('搜索成员').fill('');await page.screenshot({path:path.join(artifacts,'members-desktop.png'),fullPage:true});
    const adminToken=await page.evaluate(()=>sessionStorage.getItem('agent-token'));
    await logout(page);await login(username,password);assert.equal(await page.locator('nav [data-page=users]').isVisible(),false);
    await go(page,'personal');await page.getByText('正在使用平台默认配置',{exact:false}).waitFor();
    await page.locator('[name=mode]').selectOption('API');await page.locator('[name=baseUrl]').fill('http://127.0.0.1:'+provider.address().port+'/v1');
    await page.locator('[name=apiKey]').fill('personal-browser-key');await page.locator('[name=defaultModel]').fill('personal-browser-model');await page.getByRole('button',{name:'保存配置',exact:true}).click();
    await page.waitForFunction(()=>document.querySelector('[name=apiKey]')?.value==='');
    await page.getByRole('button',{name:'查询余额 / 额度',exact:true}).click();await page.getByText('暂不支持查询',{exact:true}).waitFor();assert.equal(calls,0);
    const token=await page.evaluate(()=>sessionStorage.getItem('agent-token')),headers={Authorization:'Bearer '+token};
    const agents=await page.request.get(base+'/api/agents',{headers}).then(r=>r.json());
    const chat=await page.request.post(base+'/api/chats',{headers,data:{agentId:agents.find(a=>a.status==='ACTIVE').id}}).then(r=>r.json());
    const reply=await page.request.post(base+'/api/chats/'+chat.id+'/messages',{headers,data:{requestId:crypto.randomUUID(),input:'Use my personal configuration'}});
    assert.equal(reply.status(),200);assert.equal((await reply.json()).output,'Private model reply');assert.equal(receivedKey,'Bearer personal-browser-key');
    await page.route('**/api/me/model-settings/balance',route=>route.fulfill({json:{status:'OK',provider:'DeepSeek',kind:'ACCOUNT_BALANCE',message:'测试数据：账户余额',balances:[{currency:'CNY',total:12.5,granted:2.5,toppedUp:10}],available:true,checkedAt:new Date().toISOString(),consoleUrl:'https://platform.deepseek.com/'}}));
    await page.getByRole('button',{name:'查询余额 / 额度',exact:true}).click();await page.getByText('12.5',{exact:true}).waitFor();
    await page.locator('#toast').waitFor({state:'hidden'});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(artifacts,'personal-model-desktop.png'),fullPage:true});
    await page.unroute('**/api/me/model-settings/balance');
    await page.route('**/api/me/model-settings/balance',route=>route.fulfill({json:{status:'OK',provider:'OpenRouter',kind:'KEY_LIMIT',message:'测试数据：Key 额度，不是钱包余额',balances:[],used:3.5,remaining:null,checkedAt:new Date().toISOString(),consoleUrl:''}}));
    await page.getByRole('button',{name:'查询余额 / 额度',exact:true}).click();await page.getByText('未设上限',{exact:true}).waitFor();
    await page.unroute('**/api/me/model-settings/balance');
    await go(page,'usage');await page.getByText('personal-browser-model',{exact:true}).waitFor();
    assert.equal(await page.locator('.stat').filter({hasText:'输入 Token'}).locator('strong').textContent(),'17');
    assert.equal(await page.locator('.stat').filter({hasText:'输出 Token'}).locator('strong').textContent(),'9');
    await page.screenshot({path:path.join(artifacts,'usage-desktop.png'),fullPage:true});
    await page.setViewportSize({width:390,height:844});
    for(const destination of ['personal','usage','account']){
      await go(page,destination);await page.locator('#content .loading').waitFor({state:'hidden'});
      assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'page overflow: '+destination);
      await page.screenshot({path:path.join(artifacts,destination+'-mobile.png'),fullPage:true});
    }
    await page.locator('[name=displayName]').fill('我的新名称');await page.getByRole('button',{name:'保存个人资料',exact:true}).click();await page.locator('#account-name').getByText('我的新名称').waitFor();
    await page.getByRole('button',{name:'修改我的密码',exact:true}).click();await page.locator('#modal [name=currentPassword]').fill(password);await page.locator('#modal [name=newPassword]').fill(newPassword);await page.locator('#modal button[type=submit]').click();
    await page.locator('#auth').waitFor();assert.equal((await page.request.get(base+'/api/users/me',{headers})).status(),401);await login(username,newPassword);
    await go(page,'personal');page.once('dialog',dialog=>dialog.accept());await page.getByRole('button',{name:'恢复平台默认',exact:true}).click();await page.getByText('正在使用平台默认配置',{exact:false}).waitFor();
    const balanceCalls=calls;assert.equal((await page.request.post(base+'/api/me/model-settings/balance',{headers:{Authorization:'Bearer '+await page.evaluate(()=>sessionStorage.getItem('agent-token'))}})).status(),400);assert.equal(calls,balanceCalls);
    assert.equal((await page.request.get(base+'/api/admin/users',{headers:{Authorization:'Bearer '+adminToken}})).status(),200);
    assert.deepEqual(errors,[]);console.log('Accounts browser passed: create/edit/filter/enable, personal key, usage, balance states, password revocation, inheritance and mobile layout.');
  }finally{await browser.close();await new Promise(resolve=>provider.close(resolve));}
}
run().catch(error=>{console.error(error);process.exitCode=1;});
