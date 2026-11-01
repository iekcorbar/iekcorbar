/* Zwartwit mapontwerpen Iekcorbar: zuiver zwart op wit, arcering in plaats van kleur.
   Werkt in de browser (window.DESIGNS) en in Node (module.exports). */
(function(){
const INK='#000', W='#fff';
const DOTS='url(#bw-dots)', FINE='url(#bw-fine)', HATCH='url(#bw-hatch)', DENSE='url(#bw-dense)';

const DEFS=`<defs>
<pattern id="bw-dots" width="5" height="5" patternUnits="userSpaceOnUse"><rect width="5" height="5" fill="#fff"/><circle cx="2.5" cy="2.5" r="1" fill="#000"/></pattern>
<pattern id="bw-fine" width="4" height="4" patternUnits="userSpaceOnUse"><rect width="4" height="4" fill="#fff"/><circle cx="2" cy="2" r=".55" fill="#000"/></pattern>
<pattern id="bw-hatch" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="6" height="6" fill="#fff"/><line x1="0" y1="0" x2="0" y2="6" stroke="#000" stroke-width="1.2"/></pattern>
<pattern id="bw-dense" width="3.5" height="3.5" patternUnits="userSpaceOnUse" patternTransform="rotate(-45)"><rect width="3.5" height="3.5" fill="#fff"/><line x1="0" y1="0" x2="0" y2="3.5" stroke="#000" stroke-width="1.5"/></pattern>
</defs>`;

function gearPath(cx,cy,r,n,h,hole){
  const step=2*Math.PI/n, ri=r-h; let d='';
  for(let i=0;i<n;i++){
    const a=i*step;
    [[ri,a-step*.27],[r,a-step*.15],[r,a+step*.15],[ri,a+step*.27]].forEach((p,j)=>{
      const x=cx+p[0]*Math.cos(p[1]), y=cy+p[0]*Math.sin(p[1]);
      d+=(i===0&&j===0?'M':'L')+x.toFixed(1)+' '+y.toFixed(1);
    });
  }
  d+='Z';
  d+=`M${cx+hole} ${cy}A${hole} ${hole} 0 1 0 ${cx-hole} ${cy}A${hole} ${hole} 0 1 0 ${cx+hole} ${cy}Z`;
  return d;
}
function gear(cx,cy,r,n,fill,stroke,sw=2,spokes=true){
  const h=r*.16, hole=r*.18;
  let s=`<path d="${gearPath(cx,cy,r,n,h,hole)}" fill="${fill}" stroke="${stroke}" stroke-width="${sw}" fill-rule="evenodd" stroke-linejoin="round"/>`;
  s+=`<circle cx="${cx}" cy="${cy}" r="${r*.62}" fill="none" stroke="${stroke}" stroke-width="${sw*.7}"/>`;
  if(spokes) for(let i=0;i<6;i++){
    const a=i*Math.PI/3;
    s+=`<line x1="${cx+hole*1.3*Math.cos(a)}" y1="${cy+hole*1.3*Math.sin(a)}" x2="${cx+r*.62*Math.cos(a)}" y2="${cy+r*.62*Math.sin(a)}" stroke="${stroke}" stroke-width="${sw*.7}"/>`;
  }
  return s;
}
function flower(cx,cy,s,rot=0){
  let g=`<g transform="translate(${cx} ${cy}) rotate(${rot}) scale(${s})">`;
  for(let i=0;i<5;i++) g+=`<g transform="rotate(${i*72})"><path d="M0 0C-7 -6 -8 -17 -3 -21L0 -17L3 -21C8 -17 7 -6 0 0Z" fill="${W}" stroke="${INK}" stroke-width="1.2" stroke-linejoin="round"/></g>`;
  g+=`<circle r="3.4" fill="${INK}"/>`;
  for(let i=0;i<5;i++){const a=i*72*Math.PI/180+.6;g+=`<circle cx="${6.5*Math.cos(a)}" cy="${6.5*Math.sin(a)}" r="1" fill="${INK}"/>`}
  return g+'</g>';
}
function base(inner){
  return `<svg viewBox="0 0 300 400" role="img" xmlns="http://www.w3.org/2000/svg">${DEFS}
  <rect x="2" y="2" width="296" height="396" rx="8" fill="${W}" stroke="${INK}" stroke-width="2"/>
  ${inner}</svg>`;
}

function cover1(){
  let s='';
  s+=gear(150,170,98,22,DOTS,INK,2.5);
  s+=gear(238,282,46,14,HATCH,INK,2.2);
  s+=gear(52,300,34,12,DENSE,INK,2);
  s+=`<path d="M300 40C250 48 215 70 180 112" fill="none" stroke="${INK}" stroke-width="5" stroke-linecap="round"/>`;
  s+=`<path d="M232 62C228 44 214 34 198 30" fill="none" stroke="${INK}" stroke-width="3" stroke-linecap="round"/>`;
  s+=`<path d="M205 92C196 80 182 76 168 78" fill="none" stroke="${INK}" stroke-width="3" stroke-linecap="round"/>`;
  [[262,48,.9,10],[232,60,.8,40],[198,30,.85,70],[214,88,.9,20],[168,78,.75,55],[183,112,.8,5],[285,70,.6,30]].forEach(f=>{s+=flower(f[0],f[1],f[2],f[3])});
  [[120,60,20],[96,118,-30],[60,86,50],[36,150,10],[84,36,-10]].forEach(p=>{
    s+=`<path transform="translate(${p[0]} ${p[1]}) rotate(${p[2]})" d="M0 0C-5 -4 -5 -11 -2 -14L0 -11L2 -14C5 -11 5 -4 0 0Z" fill="${W}" stroke="${INK}" stroke-width="1.2"/>`;
  });
  s+=`<rect x="20" y="318" width="260" height="62" rx="3" fill="${INK}"/>`;
  s+=`<text x="150" y="350" text-anchor="middle" font-family="Cinzel,serif" font-weight="700" font-size="30" letter-spacing="5" fill="${W}">IEKCORBAR</text>`;
  s+=`<text x="150" y="369" text-anchor="middle" font-family="IBM Plex Sans,sans-serif" font-size="10.5" letter-spacing="3.5" fill="${W}">OFFLINE DOCUMENTATIE</text>`;
  return base(s);
}

function cover2(){
  /* blauwdruk als lijntekening: wit vlak, zwarte lijnen, geen donkere vulling */
  let s=`<rect x="16" y="16" width="268" height="368" rx="3" fill="${W}" stroke="${INK}" stroke-width="3"/><rect x="22" y="22" width="256" height="356" fill="none" stroke="${INK}" stroke-width=".8"/>`;
  for(let x=32;x<=268;x+=16)s+=`<line x1="${x}" y1="22" x2="${x}" y2="378" stroke="${INK}" stroke-width=".3" opacity=".35"/>`;
  for(let y=38;y<=364;y+=16)s+=`<line x1="22" y1="${y}" x2="278" y2="${y}" stroke="${INK}" stroke-width=".3" opacity=".35"/>`;
  s+=gear(115,150,62,18,W,INK,1.8)+gear(205,214,38,11,W,INK,1.6);
  s+=`<line x1="115" y1="60" x2="115" y2="240" stroke="${INK}" stroke-width=".9" stroke-dasharray="8 3 2 3"/>`;
  s+=`<line x1="40" y1="150" x2="250" y2="150" stroke="${INK}" stroke-width=".9" stroke-dasharray="8 3 2 3"/>`;
  s+=`<g stroke="${INK}" stroke-width="1"><line x1="53" y1="256" x2="177" y2="256"/><line x1="53" y1="250" x2="53" y2="262"/><line x1="177" y1="250" x2="177" y2="262"/></g>`;
  s+=`<text x="115" y="274" text-anchor="middle" font-family="IBM Plex Sans,sans-serif" font-size="9" fill="${INK}">Ø 124 = 31 blokken</text>`;
  s+=`<text x="205" y="268" text-anchor="middle" font-family="IBM Plex Sans,sans-serif" font-size="8" fill="${INK}">ratio 2 : 1</text>`;
  s+=`<g transform="translate(222 62) rotate(-14)"><rect x="-44" y="-14" width="88" height="28" fill="${W}" stroke="${INK}" stroke-width="2.4"/><rect x="-40" y="-10" width="80" height="20" fill="none" stroke="${INK}" stroke-width=".8"/><text y="5" text-anchor="middle" font-family="Cinzel,serif" font-weight="700" font-size="13" letter-spacing="2" fill="${INK}">ARCHIEF</text></g>`;
  s+=`<g stroke="${INK}" stroke-width="1.2" fill="none"><rect x="28" y="296" width="244" height="76"/><line x1="28" y1="322" x2="272" y2="322"/><line x1="170" y1="322" x2="170" y2="372"/><line x1="170" y1="347" x2="272" y2="347"/></g>`;
  s+=`<text x="150" y="316" text-anchor="middle" font-family="Cinzel,serif" font-weight="700" font-size="17" letter-spacing="3" fill="${INK}">IEKCORBAR</text>`;
  const t=(x,y,txt,sz=8)=>`<text x="${x}" y="${y}" font-family="IBM Plex Sans,sans-serif" font-size="${sz}" fill="${INK}">${txt}</text>`;
  s+=t(34,338,'Titel')+t(34,352,'Offline documentatie',10)+t(34,366,'Server en plugins',8);
  s+=t(176,338,'Versie')+t(212,338,'v. ____',10)+t(176,364,'Datum',8)+t(212,364,'___ / ___ / ____',8);
  return base(s);
}

function cover3(){
  let s='';
  s+=`<circle cx="150" cy="128" r="70" fill="${DENSE}" stroke="${INK}" stroke-width="3"/>`;
  s+=`<g fill="${INK}"><path d="M52 112Q150 126 248 112L254 98Q150 114 46 98Z"/><rect x="62" y="124" width="176" height="11"/><rect x="76" y="112" width="14" height="150"/><rect x="210" y="112" width="14" height="150"/><rect x="141" y="112" width="18" height="12"/></g>`;
  s+=`<rect x="66" y="258" width="26" height="6" fill="${INK}"/><rect x="208" y="258" width="26" height="6" fill="${INK}"/>`;
  s+=`<rect x="20" y="262" width="260" height="22" rx="4" fill="${HATCH}" stroke="${INK}" stroke-width="2.2"/>`;
  [50,110,170,230].forEach(x=>{s+=`<rect x="${x-5}" y="259" width="10" height="28" fill="${W}" stroke="${INK}" stroke-width="1.8"/>`});
  [[78,252],[198,252]].forEach(p=>{
    s+=`<path d="M${p[0]} ${p[1]}C${p[0]-14} ${p[1]-8} ${p[0]+14} ${p[1]-16} ${p[0]} ${p[1]-24}" fill="none" stroke="${INK}" stroke-width="2" stroke-linecap="round" stroke-dasharray="1 5"/>`;
  });
  s+=`<text x="150" y="318" text-anchor="middle" font-family="Noto Serif JP,serif" font-weight="600" font-size="22" letter-spacing="6" fill="${INK}">オフライン資料</text>`;
  s+=`<text x="150" y="346" text-anchor="middle" font-family="Cinzel,serif" font-weight="700" font-size="26" letter-spacing="5" fill="${INK}">IEKCORBAR</text>`;
  s+=`<line x1="60" y1="358" x2="240" y2="358" stroke="${INK}" stroke-width="1.5"/>`;
  s+=`<text x="150" y="375" text-anchor="middle" font-family="IBM Plex Sans,sans-serif" font-size="10" letter-spacing="3.5" fill="${INK}">DOCUMENTATIE</text>`;
  s+=flower(34,36,.9,10)+flower(268,372,.7,40);
  return base(s);
}

function cover4(){
  let s='';
  s+=`<text x="26" y="118" font-family="Cinzel,serif" font-weight="700" font-size="64" fill="${INK}" letter-spacing="2">OFF</text>`;
  s+=`<text x="26" y="180" font-family="Cinzel,serif" font-weight="700" font-size="64" fill="${INK}" letter-spacing="2">LINE</text>`;
  s+=`<line x1="26" y1="198" x2="274" y2="198" stroke="${INK}" stroke-width="3"/>`;
  s+=`<text x="26" y="224" font-family="IBM Plex Sans,sans-serif" font-weight="500" font-size="14" letter-spacing="4" fill="${INK}">DOCUMENTATIE</text>`;
  s+=gear(228,86,44,14,DOTS,INK,2.2)+gear(262,150,22,9,HATCH,INK,1.8,false);
  s+=`<rect x="26" y="262" width="248" height="108" rx="6" fill="${W}" stroke="${INK}" stroke-width="3"/><rect x="31" y="267" width="238" height="98" rx="3" fill="none" stroke="${INK}" stroke-width=".8"/>`;
  [[40,276],[260,276],[40,356],[260,356]].forEach(p=>{s+=`<circle cx="${p[0]}" cy="${p[1]}" r="4.5" fill="${W}" stroke="${INK}" stroke-width="1.4"/><line x1="${p[0]-2.6}" y1="${p[1]-2.6}" x2="${p[0]+2.6}" y2="${p[1]+2.6}" stroke="${INK}" stroke-width="1"/>`});
  s+=`<text x="150" y="304" text-anchor="middle" font-family="Cinzel,serif" font-weight="700" font-size="24" letter-spacing="5" fill="${INK}">IEKCORBAR</text>`;
  s+=`<line x1="64" y1="316" x2="236" y2="316" stroke="${INK}" stroke-width="1"/>`;
  s+=`<text x="64" y="338" font-family="IBM Plex Sans,sans-serif" font-size="9.5" fill="${INK}">Deel:</text><line x1="92" y1="339" x2="236" y2="339" stroke="${INK}" stroke-width=".8"/>`;
  s+=`<text x="64" y="356" font-family="IBM Plex Sans,sans-serif" font-size="9.5" fill="${INK}">Datum:</text><line x1="100" y1="357" x2="236" y2="357" stroke="${INK}" stroke-width=".8"/>`;
  return base(s);
}

function sheet(){
  const names=['Steamworks','Kitchen','Sakagura','Kura','Zukan','Wildlife','Relics','Satchel','Gouw','Server','Website','Quests'];
  let s=`<svg viewBox="0 0 900 330" role="img" xmlns="http://www.w3.org/2000/svg">${DEFS}<rect width="900" height="330" fill="${W}"/>`;
  s+=`<g transform="translate(24 24)"><rect width="852" height="62" rx="6" fill="${INK}"/><rect x="6" y="6" width="840" height="50" rx="4" fill="none" stroke="${W}" stroke-width="1.2"/>`;
  s+=`<text x="426" y="40" text-anchor="middle" font-family="Cinzel,serif" font-weight="700" font-size="28" letter-spacing="8" fill="${W}">IEKCORBAR · OFFLINE DOCUMENTATIE</text>`;
  s+=gear(44,31,18,10,W,W,1.2,false)+gear(808,31,18,10,W,W,1.2,false)+`</g>`;
  names.forEach((n,i)=>{
    const col=i%4,row=Math.floor(i/4),x=24+col*216,y=112+row*68;
    s+=`<g transform="translate(${x} ${y})"><path d="M0 8Q0 0 8 0H168L192 28L168 56H8Q0 56 0 48Z" fill="${W}" stroke="${INK}" stroke-width="2.4"/>`;
    s+=`<circle cx="28" cy="28" r="15" fill="${DOTS}" stroke="${INK}" stroke-width="1.5"/>`;
    s+=`<path d="${gearPath(28,28,11,8,3,3.5)}" fill="${INK}" fill-rule="evenodd"/>`;
    s+=`<text x="52" y="34" font-family="Cinzel,serif" font-weight="700" font-size="17" letter-spacing="1.5" fill="${INK}">${n.toUpperCase()}</text></g>`;
  });
  return s+`</svg>`;
}

const api={
  helpers:{gear,gearPath,flower},
  covers:[
    {file:'1-tandwiel-en-sakura',title:'Tandwiel en sakura',note:'Tandwielen met stipjes en arcering, een bloeiende tak en een zwarte titelplaat.',svg:cover1},
    {file:'2-blauwdruk',title:'Blauwdruk',note:'Lijntekening met titelblok en stempel. Gebruikt bijna geen toner.',svg:cover2},
    {file:'3-torii-en-stoom',title:'Torii en stoom',note:'Zon met schuine arcering, poort en stoompijp.',svg:cover3},
    {file:'4-naamplaat',title:'Naamplaat',note:'Grote letters en een plaatje met invulvelden voor deel en datum.',svg:cover4}
  ],
  sheet:{file:'5-stickervel',svg:sheet}
};
if(typeof module!=='undefined')module.exports=api; else window.DESIGNS=api;
})();
