'use strict';
const themeMedia=matchMedia('(prefers-color-scheme: dark)');
let themeChoice='system';try{const saved=localStorage.getItem('agent-theme');if(['system','light','dark'].includes(saved))themeChoice=saved;}catch{}
function applyTheme(){document.documentElement.dataset.theme=themeChoice==='system'?(themeMedia.matches?'dark':'light'):themeChoice;}
const themeSelector=select([['system','跟随系统'],['light','白色主题'],['dark','黑色主题']],themeChoice,value=>{themeChoice=value;try{localStorage.setItem('agent-theme',value);}catch{}applyTheme();});
themeSelector.id='theme-selector';themeSelector.setAttribute('aria-label','界面主题');$('#theme-control').append(themeSelector);themeMedia.addEventListener('change',applyTheme);applyTheme();
