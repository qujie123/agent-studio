'use strict';
const {go,agentAction,logout}=require('./browser-navigation.cjs');
const {chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const base=process.env.APP_URL||'http://127.0.0.1:18080';
async function run(){
  const browser=await chromium.launch({headless:true,executablePath:process.env.EDGE_PATH||'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'});
  const page=await browser.newPage({viewport:{width:1440,height:1050}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  async function login(username,password){await page.locator('#auth-form [name=username]').fill(username);await page.locator('#auth-form [name=password]').fill(password);await page.locator('#auth-submit').click();await page.locator('#workspace').waitFor();}
  async function api(route,method='GET',data){const token=await page.evaluate(()=>sessionStorage.getItem('agent-token'));const response=await page.request.fetch(base+'/api'+route,{method,headers:{Authorization:'Bearer '+token},data});assert(response.ok(),route+' '+response.status());return response.status()===204?null:response.json();}
  try{
    await page.goto(base);await login('demo_admin','DemoOnly_2026!');assert.equal((await api('/system/status')).ephemeral,true,'Only run against an isolated demo instance');
    assert(await page.evaluate(async()=>{modal('旧弹窗');$('#modal').close();modal('新弹窗');const epoch=state.dialogEpoch;await new Promise(r=>setTimeout(r,50));const current=epoch===state.dialogEpoch;$('#modal').close();return current;}),'A delayed close event must not invalidate the new dialog');
    const suffix=Date.now(),username='upgrade_browser_'+suffix,password='UpgradeBrowser_2026!';
    await api('/admin/users','POST',{username,email:username+'@example.com',displayName:'Upgrade member',password,role:'USER'});
    await go(page,'users');await page.getByLabel('搜索成员').fill('admin');await page.evaluate(()=>refresh());await page.getByLabel('筛选状态').selectOption('disabled');await page.waitForTimeout(350);assert.equal(await page.evaluate(()=>state.memberFilters.enabled),false,'A detached search debounce must not reset the current filters');
    await go(page,'tasks');await page.getByLabel('任务范围').selectOption('all');
    await logout(page);await login(username,password);await go(page,'tasks');
    await page.getByLabel('任务范围').waitFor();assert.equal(await page.getByLabel('任务范围').inputValue(),'mine');assert.equal(await page.locator('#content').getByText('没有权限进行此操作',{exact:true}).count(),0);
    await logout(page);await login('demo_admin','DemoOnly_2026!');await go(page,'account');
    let release,started;const gate=new Promise(r=>release=r),ready=new Promise(r=>started=r);
    await page.route('**/api/users/me',async route=>{if(route.request().method()!=='PUT'){await route.continue();return;}const response=await route.fetch();started();await gate;await route.fulfill({response});});
    await page.getByLabel('显示名称',{exact:true}).fill('Delayed admin update');await page.getByRole('button',{name:'保存个人资料',exact:true}).click();await ready;
    await logout(page);await login(username,password);release();await page.unrouteAll({behavior:'wait'});
    assert.equal(await page.evaluate(()=>state.user.username),username);assert.equal(await page.locator('#account-name').textContent(),'Upgrade member');
    await logout(page);await login('demo_admin','DemoOnly_2026!');
    const agent=await api('/agents','POST',{name:'Upgrade browser agent '+suffix,description:'',systemPrompt:'prompt',modelName:'demo-model',status:'ACTIVE'});
    for(let i=0;i<45;i++)await api('/tasks','POST',{agentId:agent.id,input:'paged task '+i});
    await go(page,'tasks');await page.locator('tbody tr').first().waitFor();assert.equal(await page.locator('tbody tr').count(),20);
    const first=await page.locator('tbody tr td:first-child').allTextContents();await page.locator('#content .pager').getByRole('button',{name:'下一页',exact:true}).click();
    await page.waitForFunction(first=>{const cell=document.querySelector('tbody tr td');return cell&&!first.includes(cell.textContent);},first);
    assert.equal(await page.locator('tbody tr').count(),20);const second=await page.locator('tbody tr td:first-child').allTextContents();assert(!second.some(id=>first.includes(id)));
    const session=await api('/chats','POST',{agentId:agent.id});for(let i=0;i<24;i++)await api('/chats/'+session.id+'/messages','POST',{requestId:crypto.randomUUID(),input:'history marker '+i});
    await go(page,'chats');await page.locator('.chat-bubble.user').first().waitFor();assert.equal(await page.locator('.chat-bubble.user').count(),20);
    await page.locator('.chat-panel .pager').getByRole('button',{name:'下一页',exact:true}).click();await page.waitForFunction(()=>document.querySelectorAll('.chat-bubble.user').length===4);
    const downloading=page.waitForEvent('download');await page.getByRole('button',{name:'导出',exact:true}).click();const download=await downloading;
    const exported=fs.readFileSync(await download.path(),'utf8');assert.equal((exported.match(/## 你\n/g)||[]).length,24);assert(exported.includes('history marker 0'));assert(exported.includes('history marker 23'));
    await go(page,'agents');const card=page.locator('.card').filter({has:page.getByRole('heading',{name:agent.name,exact:true})});await agentAction(page,card,'Skills');await page.getByRole('button',{name:'保存绑定',exact:true}).waitFor();
    const latest=await api('/agents/'+agent.id);await api('/agents/'+agent.id,'PUT',{...latest,description:'Changed in another window'});
    await page.getByRole('button',{name:'保存绑定',exact:true}).click();await page.locator('#modal .error').getByText('智能体或技能绑定已在其他窗口修改，请刷新后重新编辑',{exact:true}).waitFor();assert.equal((await api('/agents/'+agent.id)).description,'Changed in another window');
    await page.locator('#modal-close').click();await page.setViewportSize({width:390,height:844});await go(page,'chats');await page.locator('.chat-panel').waitFor();
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));fs.mkdirSync(path.join(__dirname,'../target/verification'),{recursive:true});await page.screenshot({path:path.join(__dirname,'../target/verification/upgrade-chat-mobile.png'),fullPage:true});
    assert.deepEqual(errors,[]);console.log('Upgrade browser passed: account isolation, stale profile response, task/history pagination, complete export, binding conflict and mobile layout.');
  }finally{await browser.close();}
}
run().catch(error=>{console.error(error);process.exitCode=1;});
