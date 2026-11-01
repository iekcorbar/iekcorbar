// Tekst uit een PDF halen (voor de vertaalknop). Alleen tekst-PDF's: een scan
// zonder tekstlaag levert niets op en dat melden we eerlijk.
// Koppen/alinea's worden grof herkend, zodat de vertaalmotor iets met structuur
// krijgt; de structuurcontrole achteraf vergelijkt dan appels met appels.
import { extractText, getDocumentProxy } from 'unpdf';

export async function pdfNaarMd(buf) {
  const pdf = await getDocumentProxy(new Uint8Array(buf));
  const { text } = await extractText(pdf, { mergePages: false });
  const uit = [];
  for (const pagina of text) {
    let cur = '';
    for (const rauw of String(pagina).split('\n')) {
      const l = rauw.trim();
      if (!l) continue;
      if (/^\d+\.\s+\S/.test(l) && l.length < 70) { if (cur) uit.push(cur); cur = ''; uit.push('## ' + l); continue; }
      if (/ pagina \d+$| page \d+$/i.test(l) && /^iekcorbar/i.test(l)) continue;      // kopregel van onze eigen gidsen
      if (/^[-•·]\s/.test(l)) { if (cur) uit.push(cur); cur = ''; uit.push('- ' + l.replace(/^[-•·]\s/, '')); continue; }
      // regel hoort bij de vorige alinea als die lang genoeg was om af te breken
      if (cur && cur.length > 60 && !/[.:!?]$/.test(cur)) cur += ' ' + l;
      else if (cur && cur.length > 100) cur += ' ' + l;
      else { if (cur) uit.push(cur); cur = l; }
    }
    if (cur) uit.push(cur);
  }
  return uit.join('\n\n').trim() + '\n';
}
