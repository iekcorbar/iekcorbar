/* Bouwt print.html: drie A4-pagina's (voorblad, rugetiketten, tabbladen) van ontwerp 2.
   Alle maten in millimeters (viewBox 210 x 297). */
const fs=require('fs'), path=require('path');
const {gear,gearPath}=require('./designs.js').helpers;
const INK='#000', W='#fff';
const F_D="font-family=\"Cinzel,Georgia,serif\"", F_B="font-family=\"IBM Plex Sans,Arial,sans-serif\"";

const DEFS=`<defs><pattern id="d" width="1.1" height="1.1" patternUnits="userSpaceOnUse"><rect width="1.1" height="1.1" fill="#fff"/><circle cx=".55" cy=".55" r=".2" fill="#000"/></pattern></defs>`;

/* ---------- pagina 1: voorblad ---------- */
function cover(){
  const D=300, H=424.3; // ontwerp in 300-eenheden, geschaald naar 210 mm
  let s=`<rect x="16" y="16" width="268" height="392.3" rx="3" fill="${W}" stroke="${INK}" stroke-width="3"/><rect x="22" y="22" width="256" height="380.3" fill="none" stroke="${INK}" stroke-width=".8"/>`;
  for(let x=32;x<=268;x+=16)s+=`<line x1="${x}" y1="22" x2="${x}" y2="402.3" stroke="${INK}" stroke-width=".3" opacity=".35"/>`;
  for(let y=38;y<=390;y+=16)s+=`<line x1="22" y1="${y}" x2="278" y2="${y}" stroke="${INK}" stroke-width=".3" opacity=".35"/>`;
  s+=gear(115,170,68,20,W,INK,1.8)+gear(212,246,40,12,W,INK,1.6);
  s+=`<line x1="115" y1="70" x2="115" y2="262" stroke="${INK}" stroke-width=".9" stroke-dasharray="8 3 2 3"/>`;
  s+=`<line x1="40" y1="170" x2="262" y2="170" stroke="${INK}" stroke-width=".9" stroke-dasharray="8 3 2 3"/>`;
  s+=`<g stroke="${INK}" stroke-width="1"><line x1="47" y1="282" x2="183" y2="282"/><line x1="47" y1="276" x2="47" y2="288"/><line x1="183" y1="276" x2="183" y2="288"/></g>`;
  s+=`<text x="115" y="300" text-anchor="middle" ${F_B} font-size="9.5" fill="${INK}">Ø 136 = 34 blokken</text>`;
  s+=`<text x="212" y="296" text-anchor="middle" ${F_B} font-size="8.5" fill="${INK}">ratio 20 : 12</text>`;
  s+=`<g transform="translate(214 100) rotate(-14)"><rect x="-48" y="-16" width="96" height="32" fill="${W}" stroke="${INK}" stroke-width="2.6"/><rect x="-44" y="-12" width="88" height="24" fill="none" stroke="${INK}" stroke-width=".8"/><text y="5.5" text-anchor="middle" ${F_D} font-weight="700" font-size="15" letter-spacing="2.4" fill="${INK}">ARCHIEF</text></g>`;
  s+=`<text x="34" y="48" text-anchor="start" ${F_B} font-size="8" letter-spacing="3" fill="${INK}">BLAUWDRUK · SERVER EN PLUGINS</text>`;
  s+=`<g stroke="${INK}" stroke-width="1.2" fill="none"><rect x="28" y="318" width="244" height="76"/><line x1="28" y1="344" x2="272" y2="344"/><line x1="170" y1="344" x2="170" y2="394"/><line x1="170" y1="369" x2="272" y2="369"/></g>`;
  s+=`<text x="150" y="338" text-anchor="middle" ${F_D} font-weight="700" font-size="18" letter-spacing="3.4" fill="${INK}">IEKCORBAR</text>`;
  const t=(x,y,txt,sz=8)=>`<text x="${x}" y="${y}" ${F_B} font-size="${sz}" fill="${INK}">${txt}</text>`;
  s+=t(34,360,'Titel')+t(34,374,'Offline documentatie',10.5)+t(34,388,'Server en plugins',8);
  s+=t(176,360,'Versie')+t(212,360,'v. ____',10)+t(176,386,'Datum',8)+t(212,386,'___ / ___ / ____',8);
  const k=210/300;
  return `<svg viewBox="0 0 210 297" width="210mm" height="297mm" xmlns="http://www.w3.org/2000/svg">${DEFS}<g transform="scale(${k})">${s}</g></svg>`;
}

/* ---------- pagina 2: rugetiketten ---------- */
function spines(){
  const w=45, h=270, gap=8, x0=(210-(3*w+2*gap))/2, y0=13.5;
  let s='';
  for(let i=0;i<3;i++){
    const x=x0+i*(w+gap);
    s+=`<g transform="translate(${x} ${y0})">`;
    s+=`<rect width="${w}" height="${h}" rx="2.5" fill="${W}" stroke="${INK}" stroke-width=".7" stroke-dasharray="2.2 1.4"/>`;
    s+=`<rect x="2.2" y="2.2" width="${w-4.4}" height="${h-4.4}" rx="1.5" fill="none" stroke="${INK}" stroke-width=".9"/><rect x="3.4" y="3.4" width="${w-6.8}" height="${h-6.8}" rx="1" fill="none" stroke="${INK}" stroke-width=".25"/>`;
    s+=gear(w/2,22,11,12,W,INK,.55)+gear(w/2,h-22,11,12,W,INK,.55);
    s+=`<g transform="translate(${w/2} 118) rotate(-90)"><text text-anchor="middle" y="3.6" ${F_D} font-weight="700" font-size="12" letter-spacing="2.4" fill="${INK}">IEKCORBAR</text><line x1="-42" y1="8" x2="42" y2="8" stroke="${INK}" stroke-width=".4"/><text text-anchor="middle" y="14" ${F_B} font-size="4.2" letter-spacing="1.4" fill="${INK}">OFFLINE DOCUMENTATIE</text></g>`;
    s+=`<g transform="translate(${w/2} 205) rotate(-90)"><text x="-27" y="1.4" ${F_B} font-size="4" fill="${INK}">Deel:</text><line x1="-17" y1="2" x2="27" y2="2" stroke="${INK}" stroke-width=".35"/></g>`;
    s+='</g>';
  }
  return `<svg viewBox="0 0 210 297" width="210mm" height="297mm" xmlns="http://www.w3.org/2000/svg">${DEFS}${s}</svg>`;
}

/* ---------- pagina 3: tabbladen ---------- */
function tabs(){
  const names=['Steamworks','Kitchen','Sakagura','Kura','Zukan','Wildlife','Relics','Satchel','Gouw','Server','Website','Quests'];
  const list=[...names,...names]; // elk tabblad twee keer: voor- en achterkant van het tab
  const w=60, h=22, cols=3, x0=15, y0=(297-8*h)/2;
  let s='';
  list.forEach((n,i)=>{
    const x=x0+(i%cols)*w, y=y0+Math.floor(i/cols)*h;
    s+=`<g transform="translate(${x} ${y})">`;
    s+=`<rect width="${w}" height="${h}" fill="${W}" stroke="${INK}" stroke-width=".25" stroke-dasharray="1.6 1.2"/>`;
    s+=`<path d="M2 3.5Q2 2 3.5 2H${w-12}L${w-3} ${h/2}L${w-12} ${h-2}H3.5Q2 ${h-2} 2 ${h-3.5}Z" fill="${W}" stroke="${INK}" stroke-width=".7"/>`;
    s+=`<path d="M3.4 4Q3.4 3.4 4 3.4H${w-11.4}L${w-5} ${h/2}L${w-11.4} ${h-3.4}H4Q3.4 ${h-3.4} 3.4 ${h-4}Z" fill="none" stroke="${INK}" stroke-width=".25"/>`;
    s+=`<circle cx="10" cy="${h/2}" r="5" fill="url(#d)" stroke="${INK}" stroke-width=".5"/>`;
    s+=`<path d="${gearPath(10,h/2,3.7,8,1.1,1.2)}" fill="${INK}" fill-rule="evenodd"/>`;
    s+=`<text x="18" y="${h/2+2}" ${F_D} font-weight="700" font-size="4" letter-spacing=".3" fill="${INK}">${n.toUpperCase()}</text>`;
    s+='</g>';
  });
  return `<svg viewBox="0 0 210 297" width="210mm" height="297mm" xmlns="http://www.w3.org/2000/svg">${DEFS}${s}</svg>`;
}

const html=`<!DOCTYPE html><html lang="nl"><head><meta charset="utf-8"><title>Iekcorbar map blauwdruk A4</title>
<link href="https://fonts.googleapis.com/css2?family=Cinzel:wght@500;700&family=IBM+Plex+Sans:wght@400;500&display=swap" rel="stylesheet">
<style>@page{size:A4;margin:0}html,body{margin:0;background:#fff}.p{width:210mm;height:297mm;overflow:hidden;page-break-after:always;break-after:page}.p:last-child{page-break-after:auto;break-after:auto}svg{display:block}</style></head>
<body><div class="p">${cover()}</div><div class="p">${spines()}</div><div class="p">${tabs()}</div></body></html>`;

const out=process.argv[2];
fs.mkdirSync(out,{recursive:true});
fs.writeFileSync(path.join(out,'print.html'),html);
console.log('ok',path.join(out,'print.html'));
