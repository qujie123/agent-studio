'use strict';
const {go,setTheme}=require('./browser-navigation.cjs');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const staticRoot=path.join(__dirname,'../src/main/resources/static');
// Check platform assets only, not colors inside user-delivered projects.
for(const file of fs.readdirSync(staticRoot).filter(name=>/\.(css|svg)$/.test(name))){
  for(const match of fs.readFileSync(path.join(staticRoot,file),'utf8').matchAll(/#[\da-f]{3,8}\b/gi)){
    const hex=match[0].slice(1),rgb=hex.length<=4?[...hex.slice(0,3)].map(c=>parseInt(c+c,16)):[0,2,4].map(i=>parseInt(hex.slice(i,i+2),16));
    assert(rgb[0]===rgb[1]&&rgb[1]===rgb[2],`${file}: non-monochrome platform color ${match[0]}`);
  }
}
const {chromium}=require('playwright');
const base=process.env.APP_URL||'http://127.0.0.1:18080';
const pages=['overview','agents','chats','tasks','skills','knowledge','workflows','skillRuns','personal','usage','account','users','settings','audit'];
function rgb(color){const match=color.match(/^rgba?\(([^)]+)\)$/);return match?match[1].split(',').map(Number):null;}
function luminance(color){const channels=color.slice(0,3).map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4);return .2126*channels[0]+.7152*channels[1]+.0722*channels[2];}
function contrast(foreground,background){const a=luminance(foreground),b=luminance(background);return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);}
async function inspect(page){
  const styles=await page.evaluate(()=>[...document.querySelectorAll('body *')].filter(el=>el.getBoundingClientRect().width&&el.getBoundingClientRect().height).map(el=>{const s=getComputedStyle(el);return {tag:el.tagName,colors:[s.color,s.backgroundColor,s.borderTopColor,s.outlineColor,s.accentColor],image:s.backgroundImage};}));
  for(const style of styles)for(const color of [...style.colors,...(style.image.match(/rgba?\([^)]+\)/g)||[])]){const value=rgb(color);if(value)assert(value[0]===value[1]&&value[1]===value[2],`${style.tag}: rendered platform color ${color}`);}
  const pairs=await page.evaluate(()=>['html','nav button.active','.primary','.stat strong','.balance-value small'].flatMap(selector=>[...document.querySelectorAll(selector)].filter(el=>el.getBoundingClientRect().width&&el.getBoundingClientRect().height).map(el=>{let current=el,background;while(current){const s=getComputedStyle(current);if(s.backgroundImage!=='none')return [];if(s.backgroundColor!=='rgba(0, 0, 0, 0)'){background=s.backgroundColor;break;}current=current.parentElement;}return {selector,foreground:getComputedStyle(el).color,background};})).flat());
  for(const pair of pairs){const foreground=rgb(pair.foreground),background=rgb(pair.background);if(foreground&&background)assert(contrast(foreground,background)>=4.5,`${pair.selector}: insufficient text contrast ${pair.foreground} on ${pair.background}`);}
}
async function run(){
  const login=await fetch(base+'/api/users/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'demo_admin',password:'DemoOnly_2026!'})});assert.equal(login.status,200);
  const token=(await login.json()).token,status=await fetch(base+'/api/system/status',{headers:{Authorization:'Bearer '+token}});assert.equal((await status.json()).ephemeral,true,'Theme tests require an isolated demo');
  const browser=await chromium.launch({headless:true,executablePath:process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}),context=await browser.newContext({viewport:{width:1440,height:1050},colorScheme:'light',reducedMotion:'reduce'}),page=await context.newPage(),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  try{
    await page.goto(base);await inspect(page);await page.locator('#auth-form [name=username]').fill('demo_admin');await page.locator('#auth-form [name=password]').fill('DemoOnly_2026!');await page.locator('#auth-submit').click();await page.locator('#workspace').waitFor();
    const captures=path.join(__dirname,'../target/verification');fs.mkdirSync(captures,{recursive:true});
    for(const theme of ['light','dark']){
      await setTheme(page,theme);assert.equal(await page.locator('html').getAttribute('data-theme'),theme);
      for(const name of pages){await go(page,name);await page.waitForFunction(name=>state.page===name&&!document.querySelector('#content .loading'),name);await inspect(page);assert.equal(await page.locator('#content').getByRole('button',{name:'重新加载',exact:true}).count(),0,name+' failed to load');}
      await go(page,'agents');await page.waitForFunction(()=>state.page==='agents'&&document.querySelector('.cards .card'));await page.locator('#primary-action').click();await page.locator('#modal [name=name]').focus();await inspect(page);await page.locator('#modal-close').click();
      await go(page,'overview');await page.locator('.stats').waitFor();await page.screenshot({path:path.join(captures,`monochrome-${theme}.png`),fullPage:true});
      await page.setViewportSize({width:390,height:844});for(const name of pages){await go(page,name);await page.waitForFunction(name=>state.page===name&&!document.querySelector('#content .loading'),name);await inspect(page);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),`${theme}/${name}: mobile overflow`);}
      await go(page,'overview');await page.locator('.stats').waitFor();await page.screenshot({path:path.join(captures,`monochrome-${theme}-mobile.png`),fullPage:true});await page.setViewportSize({width:1440,height:1050});
    }
    await page.reload();await page.locator('#workspace').waitFor();assert.equal(await page.getByLabel('界面主题').inputValue(),'dark');
    await setTheme(page,'system');await page.emulateMedia({colorScheme:'light'});await page.waitForFunction(()=>document.documentElement.dataset.theme==='light');await inspect(page);await page.emulateMedia({colorScheme:'dark'});await page.waitForFunction(()=>document.documentElement.dataset.theme==='dark');await inspect(page);
    const darkContext=await browser.newContext({colorScheme:'dark',reducedMotion:'reduce'}),loginPage=await darkContext.newPage();loginPage.on('pageerror',error=>errors.push(error.message));await loginPage.goto(base);await loginPage.locator('#auth-form').waitFor({state:'visible'});await inspect(loginPage);await loginPage.screenshot({path:path.join(captures,'monochrome-login-dark.png'),fullPage:true});
    assert.deepEqual(errors,[]);console.log('Monochrome theme passed: platform CSS/favicon, login, 14 pages in light/dark desktop/mobile, form focus, key text contrast, saved preference and system switching.');
  }finally{await browser.close();}
}
run().catch(error=>{console.error(error);process.exitCode=1;});
