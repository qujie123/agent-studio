'use strict';
// Deliberately a safe subset: build DOM nodes, never insert provider-supplied HTML.
function markdownInline(parent,text){
  const tokens=/(\*\*[^*\n]+\*\*|`[^`\n]+`|\[[^\]\n]+\]\([^\s)]+\))/g;let position=0;
  for(const match of text.matchAll(tokens)){
    parent.append(document.createTextNode(text.slice(position,match.index)));const value=match[0];
    if(value.startsWith('**'))parent.append(node('strong',value.slice(2,-2)));
    else if(value.startsWith('`'))parent.append(node('code',value.slice(1,-1)));
    else{const link=value.match(/^\[([^\]]+)\]\((.+)\)$/);let url;
      try{url=new URL(link[2]);}catch{}
      if(url&&['http:','https:'].includes(url.protocol)&&!url.username&&!url.password){const a=node('a',link[1]);a.href=url.href;a.target='_blank';a.rel='noopener noreferrer';parent.append(a);}else parent.append(document.createTextNode(value));
    }position=match.index+value.length;
  }parent.append(document.createTextNode(text.slice(position)));
}
function renderMarkdown(text){
  const root=node('div',undefined,'markdown-body'),lines=text.replaceAll('\r\n','\n').split('\n');
  if(text.length>120000||lines.length>2000){root.append(node('pre',text,'markdown-plain'));return root;}
  const cells=line=>line.trim().replace(/^\||\|$/g,'').split('|').map(v=>v.trim());
  const divider=line=>line.includes('|')&&cells(line).every(v=>/^:?-{3,}:?$/.test(v));
  for(let i=0;i<lines.length;){
    const line=lines[i],fence=line.match(/^\s*(`{3,}|~{3,})([^\r\n]*)\s*$/);
    if(fence){const code=[],heading=i>0?lines[i-1].match(/^#{1,6}\s+`?([^`\s]+\.[A-Za-z0-9]+)`?\s*$/):null;i++;while(i<lines.length&&!new RegExp('^\\s*'+fence[1][0]+'{'+fence[1].length+',}\\s*$').test(lines[i]))code.push(lines[i++]);if(i<lines.length)i++;
      const info=heading&&!fence[2].includes('.')?fence[2]+' '+heading[1]:fence[2];const block=node('section',undefined,'code-block'),bar=node('div',undefined,'code-toolbar'),value=code.join('\n');bar.append(node('span',fence[2]||'代码'),button('复制代码',()=>navigator.clipboard.writeText(value).then(()=>toast('代码已复制'))));if(fence[2].trim()!=='agent-project')bar.append(button('下载代码',()=>downloadBlob(codeFilename(info),new Blob([value],{type:'text/plain;charset=utf-8'}))));const pre=node('pre'),content=node('code',value);pre.append(content);block.append(bar,pre);root.append(block);continue;
    }
    if(!line.trim()){i++;continue;}
    const heading=line.match(/^(#{1,6})\s+(.+)$/);if(heading){const el=node('h'+heading[1].length);markdownInline(el,heading[2]);root.append(el);i++;continue;}
    if(/^\s*([-*_])(?:\s*\1){2,}\s*$/.test(line)){root.append(node('hr'));i++;continue;}
    if(i+1<lines.length&&line.includes('|')&&divider(lines[i+1])){
      const wrap=node('div',undefined,'table-wrap'),table=node('table'),head=node('thead'),row=node('tr');for(const cell of cells(line).slice(0,20)){const th=node('th');markdownInline(th,cell);row.append(th);}head.append(row);const body=node('tbody');i+=2;
      while(i<lines.length&&lines[i].trim()&&lines[i].includes('|')){const tr=node('tr');for(const cell of cells(lines[i++]).slice(0,20)){const td=node('td');markdownInline(td,cell);tr.append(td);}body.append(tr);}table.append(head,body);wrap.append(table);root.append(wrap);continue;
    }
    const bullet=line.match(/^\s*(?:[-*+] |\d+[.)] )(.+)$/);if(bullet){const ordered=/^\s*\d/.test(line),list=node(ordered?'ol':'ul');
      while(i<lines.length){const item=lines[i].match(ordered?/^\s*\d+[.)] (.+)$/:/^\s*[-*+] (.+)$/);if(!item)break;const li=node('li');markdownInline(li,item[1]);list.append(li);i++;}root.append(list);continue;
    }
    if(/^>\s?/.test(line)){const quote=node('blockquote');while(i<lines.length&&/^>\s?/.test(lines[i])){const p=node('p');markdownInline(p,lines[i++].replace(/^>\s?/,''));quote.append(p);}root.append(quote);continue;}
    const p=node('p');markdownInline(p,line);root.append(p);i++;
  }return root;
}
