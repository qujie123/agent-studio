'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{chromium}=require('playwright');
const {go,sections,setTheme,logout}=require('./browser-navigation.cjs');
const base=process.env.APP_URL||'http://127.0.0.1:18080';
async function run(){
  const browser=await chromium.launch({headless:true,executablePath:process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}),page=await browser.newPage({viewport:{width:1440,height:1050},reducedMotion:'reduce'}),errors=[],failed=[];
  page.on('pageerror',error=>errors.push(error.message));page.on('response',response=>{if(response.url().includes('/api/')&&response.status()>=400)failed.push(response.status()+' '+new URL(response.url()).pathname);});
  const ready=name=>page.waitForFunction(name=>state.page===name&&!document.querySelector('#content .loading'),name);
  try{
    const check=await fetch(base+'/api/users/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'demo_admin',password:'DemoOnly_2026!'})});assert.equal(check.status,200);const token=(await check.json()).token;
    const status=await fetch(base+'/api/system/status',{headers:{Authorization:'Bearer '+token}});assert.equal((await status.json()).ephemeral,true,'Workspace checks require isolated demo');
    await page.goto(base);await page.locator('#auth-form [name=username]').fill('demo_admin');await page.locator('#auth-form [name=password]').fill('DemoOnly_2026!');await page.locator('#auth-submit').click();await page.locator('#workspace').waitFor();
    assert.equal(await page.locator('#workspace-nav [data-section]').count(),5,'Only five primary destinations, not fourteen separate pages');
    assert.equal(await page.locator('#workspace-nav [data-page=account]').count(),0,'Profile belongs in the account menu');
    assert.equal(await page.locator('#section-navigation').count(),0,'No duplicate navigation strip');
    const captures=path.join(__dirname,'../target/verification');fs.mkdirSync(captures,{recursive:true});
    for(const width of [1440,390]){
      await page.setViewportSize({width,height:width===390?844:1050});
      for(const [section,names] of Object.entries(sections))for(const name of names){
        await go(page,name);await ready(name);
        assert.equal(await page.locator('#workspace-nav [data-section].active').getAttribute('data-section'),section);
        assert.equal(await page.locator('#workspace-nav [aria-current=page]').count(),1);
        assert.equal(await page.locator('#workspace-nav [aria-current=page]').getAttribute('data-page'),name);
        assert.deepEqual(await page.locator('#workspace-nav .nav-children:not([hidden]) [data-page]').evaluateAll(els=>els.map(el=>el.dataset.page)),section==='overview'?[]:names);
        assert.equal(await page.locator('#workspace-nav [aria-expanded=true]').count(),section==='overview'?0:1);
        assert.equal(await page.locator('#content').getByRole('button',{name:'重新加载',exact:true}).count(),0);
        assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),name+' mobile overflow');
      }
      await go(page,'account');await ready('account');assert.equal(await page.locator('#account-menu').evaluate(el=>el.open),false);
      assert.equal(await page.locator('#workspace-nav [aria-current=page]').count(),0);
      if(width===390)await page.locator('#navigation-toggle').click();
      const work=page.locator('#workspace-nav [data-section=work]');await work.click();assert.equal(await work.getAttribute('aria-expanded'),'true');
      assert.equal(await page.evaluate(()=>state.page),'account','Expanding navigation must not replace an unsaved form');
      await work.click();assert.equal(await work.getAttribute('aria-expanded'),'false');
      if(width===390){await page.keyboard.press('Escape');assert.equal(await page.locator('#navigation-toggle').getAttribute('aria-expanded'),'false');}
      await go(page,'agents');await ready('agents');const card=page.locator('.card').first();
      assert.equal(await card.locator('.card-actions > button').count(),3,'Active agent cards expose chat, task and one management entry');
      await card.getByRole('button',{name:'管理',exact:true}).click();
      for(const label of ['编辑','Skills','知识库','删除'])assert.equal(await page.locator('#modal').getByRole('button',{name:label,exact:true}).isVisible(),true);
      await page.locator('#modal-close').click();await page.screenshot({path:path.join(captures,'workspace-resources-'+width+'.png'),fullPage:true});
    }
    await go(page,'personal');await ready('personal');await page.getByRole('button',{name:'查看调用用量',exact:true}).click();await ready('usage');await page.getByRole('button',{name:'API 接入与余额',exact:true}).click();await ready('personal');
    await setTheme(page,'dark');await page.screenshot({path:path.join(captures,'workspace-models-dark.png'),fullPage:true});
    await page.locator('#account-toggle').focus();await page.keyboard.press('Enter');assert.equal(await page.locator('#account-menu').evaluate(el=>el.open),true);await page.keyboard.press('Escape');assert.equal(await page.locator('#account-menu').evaluate(el=>el.open),false);assert.equal(await page.locator('#account-toggle').evaluate(el=>el===document.activeElement),true);
    await page.locator('#account-toggle').click();await page.locator('#breadcrumb').click();assert.equal(await page.locator('#account-menu').evaluate(el=>el.open),false,'Clicking outside closes the account menu');
    await logout(page);await page.locator('#auth-switch').click();const username='workspace_member_'+Date.now();await page.locator('#auth-form [name=username]').fill(username);await page.locator('#auth-form [name=email]').fill(username+'@example.com');await page.locator('#auth-form [name=password]').fill('workspace-pass-2026');await page.locator('#auth-submit').click();await page.locator('#workspace').waitFor();
    assert.equal(await page.locator('#workspace-nav [data-section]').count(),4);assert.equal(await page.locator('#workspace-nav [data-section=admin]').count(),0);
    const before=await page.evaluate(()=>state.page);await page.evaluate(()=>navigate('settings'));assert.equal(await page.evaluate(()=>state.page),before,'Direct admin navigation must not replace a regular user page');assert.equal(await page.locator('#workspace-nav [data-page=settings]').count(),0);
    await page.evaluate(()=>navigate('missing'));assert.equal(await page.evaluate(()=>state.page),before);
    await go(page,'account');await ready('account');
    assert.deepEqual(errors,[]);assert.deepEqual(failed,[]);console.log('Workspace consolidation passed: five/four primary groups, all 14 pages, desktop/mobile, folding without navigation, account keyboard/outside dismissal, compact agent management, model/usage links and account isolation.');
  }finally{await browser.close();}
}
run().catch(error=>{console.error(error);process.exitCode=1;});
