'use strict';
const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs');
const {go}=require('./browser-navigation.cjs');
async function run(){
  assert(process.env.INSTALLER_TEST_URL&&process.env.INSTALLER_TEST_PASSWORD,'Supply installer test credentials through process environment');
  const browser=await chromium.launch({headless:true,executablePath:process.env.EDGE_PATH||'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'});
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[],failed=[];
  page.on('pageerror',error=>errors.push(error.message));page.on('response',response=>{if(response.url().includes('/api/')&&response.status()>=400)failed.push(response.status()+' '+new URL(response.url()).pathname);});
  try{
    await page.goto(process.env.INSTALLER_TEST_URL);await page.locator('#auth-form [name=username]').fill('local_admin');await page.locator('#auth-form [name=password]').fill(process.env.INSTALLER_TEST_PASSWORD);await page.locator('#auth-submit').click();await page.locator('#workspace').waitFor();
    assert.equal(await page.evaluate(()=>state.system.ephemeral),false);
    for(const name of ['overview','agents','chats','tasks','skills','knowledge','workflows','skillRuns','personal','usage','account','users','settings','audit']){
      await go(page,name);await page.waitForFunction(name=>state.page===name&&!document.querySelector('#content .loading'),name);assert.equal(await page.locator('#content').getByRole('button',{name:'重新加载',exact:true}).count(),0,name+' failed');
    }
    await go(page,'overview');await page.locator('.stats').waitFor();fs.mkdirSync(path.join(__dirname,'../target/verification'),{recursive:true});await page.screenshot({path:path.join(__dirname,'../target/verification/windows-installer-workspace.png'),fullPage:true});
    assert.deepEqual(errors,[]);assert.deepEqual(failed,[]);console.log('PASS: installed application login and 14 pages, no JavaScript errors or failed API responses.');
  }finally{await browser.close();}
}
run().catch(error=>{console.error(error);process.exitCode=1;});
