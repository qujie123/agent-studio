'use strict';
const {go}=require('./browser-navigation.cjs');
// Read-only page inspection; login updates only the account's last-login timestamp.
const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs');
async function run(){
  assert(process.env.LOCAL_VERIFY_USERNAME&&process.env.LOCAL_VERIFY_PASSWORD,'Supply local verification credentials through process environment variables');
  const browser=await chromium.launch({headless:true,executablePath:process.env.EDGE_PATH||'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'});
  const page=await browser.newPage({viewport:{width:1440,height:1050}}),errors=[],failed=[];
  page.on('pageerror',error=>errors.push(error.message));page.on('response',response=>{if(response.url().includes('/api/')&&response.status()>=400)failed.push(response.status()+' '+new URL(response.url()).pathname);});
  try{
    await page.goto(process.env.APP_URL||'http://127.0.0.1:8081');await page.locator('#auth-form [name=username]').fill(process.env.LOCAL_VERIFY_USERNAME);await page.locator('#auth-form [name=password]').fill(process.env.LOCAL_VERIFY_PASSWORD);await page.locator('#auth-submit').click();await page.locator('#workspace').waitFor();
    assert.equal(await page.evaluate(()=>state.system.ephemeral),false);assert.equal(await page.evaluate(()=>state.system.version),'1.7');
    if(process.env.LOCAL_VERIFY_MODELS_CLEARED==='true'){
      for(const endpoint of ['/admin/model-settings','/me/model-settings']){
        const config=await page.evaluate(path=>api(path),endpoint);assert.equal(config.hasKey,false);assert.equal(config.baseUrl,'');assert.notEqual(config.mode,'API');
      }
      console.log('Local model reset verified: no saved keys, addresses or active API mode.');
    }
    for(const name of ['overview','agents','chats','tasks','skills','knowledge','workflows','skillRuns','personal','usage','account','users','settings','audit']){
      await go(page,name);await page.waitForFunction(name=>state.page===name&&!document.querySelector('#content .loading'),name);assert.equal(await page.locator('#content').getByRole('button',{name:'重新加载',exact:true}).count(),0,name+' failed to load');
    }
    await go(page,'overview');await page.locator('.stats').waitFor();fs.mkdirSync(path.join(__dirname,'../target/verification'),{recursive:true});await page.screenshot({path:path.join(__dirname,'../target/verification/local-workspace-v1.7.png'),fullPage:true});
    assert.deepEqual(errors,[]);assert.deepEqual(failed,[]);console.log('Local v1.7 verified: original account login, 14 read-only pages, no JavaScript errors or failed API responses.');
  }finally{await browser.close();}
}
run().catch(error=>{console.error(error);process.exitCode=1;});
