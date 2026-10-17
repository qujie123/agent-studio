const c=document.getElementById('heart'),x=c.getContext('2d');let play=true,t=0;
document.getElementById('play').onclick=e=>{play=!play;e.target.textContent=play?'暂停动画':'继续动画'};
function draw(){if(play)t+=.03;x.clearRect(0,0,800,440);for(let i=0;i<260;i++){const a=i/260*Math.PI*2,s=10+Math.sin(t)*.7;x.fillStyle='hsl('+(320+i/10)+',90%,70%)';x.beginPath();x.arc(400+16*Math.pow(Math.sin(a),3)*s,220-(13*Math.cos(a)-5*Math.cos(2*a)-2*Math.cos(3*a)-Math.cos(4*a))*s,2.8,0,7);x.fill()}requestAnimationFrame(draw)}draw();
