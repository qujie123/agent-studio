'use strict';
const sections={overview:['overview'],work:['chats','tasks','workflows','skillRuns'],resources:['agents','skills','knowledge'],models:['personal','usage'],admin:['users','settings','audit']};
async function go(page,name){
  if(name==='account'){
    if(!await page.locator('#account-menu').evaluate(el=>el.open))await page.locator('#account-toggle').click();
    await page.locator('#account-settings').click();return;
  }
  const section=Object.keys(sections).find(key=>sections[key].includes(name));
  if(!section)throw new Error('Unknown page: '+name);
  const toggle=page.locator('#navigation-toggle');
  if(await toggle.isVisible()&&await toggle.getAttribute('aria-expanded')!=='true')await toggle.click();
  const target=page.locator('#workspace-nav [data-page='+name+']');
  if(name==='overview'||!await target.isVisible())await page.locator('#workspace-nav [data-section='+section+']').click();
  if(name!=='overview')await target.click();
}
async function setTheme(page,name){
  if(!await page.locator('#account-menu').evaluate(el=>el.open))await page.locator('#account-toggle').click();
  await page.getByLabel('界面主题').selectOption(name);
  await page.locator('#account-toggle').click();
}
async function agentAction(page,card,name){
  await card.getByRole('button',{name:'管理',exact:true}).click();
  await page.locator('#modal').getByRole('button',{name,exact:true}).click();
}
async function logout(page){
  if(!await page.locator('#account-menu').evaluate(el=>el.open))await page.locator('#account-toggle').click();
  await page.locator('#logout').click();
}
module.exports={go,sections,setTheme,agentAction,logout};
