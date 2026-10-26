'use client';
import React, { useMemo, useState } from 'react';
import Link from 'next/link';
import { Cog, BookOpen, Wrench, CheckCircle2, AlertTriangle, Copy, Check, ListOrdered, Lightbulb, Code2 } from 'lucide-react';
import { INSTRUCTIONS, CONDITIONS, EXAMPLES, compile, explain, toolProblems } from '@/lib/karakuri';

const TOOLS = [
  { id: 'gripper', label: 'Gripper Head' },
  { id: 'placer', label: 'Placer Head' },
  { id: 'drill', label: 'Drill Head' },
];

function Section({ id, icon: Icon, title, children }) {
  return (
    <section id={id} className="scroll-mt-24 bg-neutral-900/60 border-2 border-sp-bronze/40 rounded-2xl p-6 sm:p-8 steampunk-glass space-y-4">
      <h2 className="flex items-center gap-3 font-serif text-2xl text-sp-gold font-bold uppercase tracking-wide">
        <Icon className="w-6 h-6 text-sp-copper" /> {title}
      </h2>
      {children}
    </section>
  );
}

function CodeBlock({ code }) {
  const [copied, setCopied] = useState(false);
  const copy = () => {
    navigator.clipboard?.writeText(code).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    }).catch(() => {});
  };
  return (
    <div className="relative">
      <pre className="bg-black/70 border border-sp-bronze/30 rounded-xl p-4 text-sm font-mono text-emerald-200 overflow-x-auto whitespace-pre">{code}</pre>
      <button onClick={copy} className="absolute top-2 right-2 inline-flex items-center gap-1 px-2 py-1 text-[11px] rounded-md bg-sp-charcoal/80 border border-sp-bronze/40 text-text-warm/70 hover:text-sp-gold">
        {copied ? <Check className="w-3 h-3" /> : <Copy className="w-3 h-3" />} {copied ? 'Copied' : 'Copy'}
      </button>
    </div>
  );
}

function Checker() {
  const [code, setCode] = useState(EXAMPLES[1].code);
  const [points, setPoints] = useState(3);
  const [tool, setTool] = useState('gripper');

  const result = useMemo(() => {
    try {
      const { ops } = compile(code, points);
      return { ops, warnings: toolProblems(ops, tool) };
    } catch (e) {
      return { error: e.message, line: e.line };
    }
  }, [code, points, tool]);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-4 text-sm">
        <label className="flex items-center gap-2 text-text-warm/80">
          Taught points
          <select value={points} onChange={e => setPoints(Number(e.target.value))}
            className="bg-black/60 border border-sp-bronze/40 rounded-md px-2 py-1 text-sp-gold">
            {[1, 2, 3, 4, 5, 6, 7, 8].map(n => <option key={n} value={n}>{n}</option>)}
          </select>
        </label>
        <label className="flex items-center gap-2 text-text-warm/80">
          Tool head
          <select value={tool} onChange={e => setTool(e.target.value)}
            className="bg-black/60 border border-sp-bronze/40 rounded-md px-2 py-1 text-sp-gold">
            {TOOLS.map(t => <option key={t.id} value={t.id}>{t.label}</option>)}
          </select>
        </label>
      </div>
      <textarea value={code} onChange={e => setCode(e.target.value)} spellCheck={false} rows={12}
        aria-label="Karakuri Arm program"
        className="w-full bg-black/70 border border-sp-bronze/40 rounded-xl p-4 font-mono text-sm text-emerald-200 focus:outline-none focus:border-sp-gold" />
      {result.error ? (
        <div className="flex items-start gap-2 rounded-xl border border-red-500/40 bg-red-950/30 p-3 text-sm text-red-200">
          <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
          <span>The arm would refuse this program: <b>{result.error}</b></span>
        </div>
      ) : (
        <div className="space-y-3">
          <div className="flex items-start gap-2 rounded-xl border border-emerald-500/40 bg-emerald-950/30 p-3 text-sm text-emerald-200">
            <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" />
            <span>Program OK - {result.ops.length} instruction{result.ops.length === 1 ? '' : 's'}. Write it in a Book and Quill and right-click the arm with it.</span>
          </div>
          {result.warnings.length > 0 && (
            <div className="rounded-xl border border-amber-500/40 bg-amber-950/30 p-3 text-sm text-amber-200 space-y-1">
              {result.warnings.map(w => <div key={w}>⚠ {w} - the arm will show this and skip the step.</div>)}
            </div>
          )}
          {result.ops.length > 0 && (
            <div className="rounded-xl border border-sp-bronze/30 bg-black/40 p-4">
              <p className="text-[11px] uppercase tracking-widest text-sp-gold/70 mb-2">What the arm will do</p>
              <ol className="space-y-1 text-sm text-text-warm/80 font-mono">
                {result.ops.filter(o => o.code !== 'END').map((o, i) => (
                  <li key={i} style={{ paddingLeft: `${o.depth * 1.25}rem` }}>
                    <span className="text-text-warm/30 mr-2">{String(o.line).padStart(2, ' ')}</span>{explain(o)}
                  </li>
                ))}
                <li className="text-text-warm/40 italic">...then it starts again from the top.</li>
              </ol>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default function SteamworksGuidePage() {
  return (
    <div className="space-y-8 pb-12">
      <title>Karakuri Arm Guide — Iekcorbar</title>

      <section className="relative rounded-2xl overflow-hidden border-2 border-sp-gold p-10 text-center shadow-xl shadow-sp-gold/5 bg-black">
        <div className="absolute inset-0 bg-gradient-to-br from-amber-950/30 via-black to-sp-charcoal/40" />
        <div className="relative z-10 space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-sp-copper/40 bg-amber-900/20 text-sp-gold text-xs font-semibold uppercase tracking-wider">
            <Cog className="w-3.5 h-3.5 animate-spin-gear-cw" /> Steamworks
          </div>
          <h1 className="font-serif text-4xl sm:text-5xl font-black text-sp-gold tracking-widest uppercase steampunk-glow">
            Karakuri Arm Guide
          </h1>
          <p className="text-text-warm/70 max-w-2xl mx-auto text-sm">
            The Karakuri Arm is a programmable machine arm. Teach it points, give it a tool head, and write a short program in a book -
            it sorts chests, feeds machines, plants trees and more. New to Steamworks itself? Read{' '}
            <Link href="/steamworks" className="text-sp-gold underline">Steamworks, explained</Link> first; everything in reference form is in the{' '}
            <Link href="/wiki" className="text-sp-gold underline">wiki</Link>.
          </p>
          <nav className="flex flex-wrap justify-center gap-2 pt-2 text-xs">
            {[['#start', 'Quick start'], ['#instructions', 'Instructions'], ['#examples', 'Examples'], ['#try', 'Try it'], ['#tips', 'Tips']].map(([h, l]) => (
              <a key={h} href={h} className="px-3 py-1 rounded-full border border-sp-bronze/40 text-text-warm/70 hover:text-sp-gold hover:border-sp-gold">{l}</a>
            ))}
          </nav>
        </div>
      </section>

      <Section id="start" icon={Wrench} title="Quick start">
        <ol className="list-decimal pl-6 space-y-2 text-sm text-text-warm/80">
          <li><b>Place the arm</b> and power it <b>from below</b> with a shaft (hand crank, water wheel...). The faster it turns, the quicker it works.</li>
          <li><b>Give it a tool head</b>: right-click the arm holding a Gripper, Placer or Drill Head. Clicking with another head swaps them.</li>
          <li><b>Teach points</b>: sneak + right-click the arm with an empty hand. Now right-click blocks within 4 blocks - they become point 1, 2, 3... (up to 8). Click a point again to remove it. Sneak + right-click the arm when you are done.</li>
          <li><b>Program it</b>: write a program in a <b>Book and Quill</b> (one instruction per line) and right-click the arm with it. If something is wrong, the arm tells you the line. You can test your program below first.</li>
          <li>No program? The arm runs a default: a gripper moves items from point 1 to the other points, a drill head breaks all points, a placer takes from point 1 and places on the others.</li>
          <li><b>Share a program</b>: right-click a programmed arm with a blank <b>Punch Card</b> to copy it onto the card, then right-click another arm with the card.</li>
        </ol>
        <p className="text-xs text-text-warm/50">Right-click the arm with an empty hand to see its tool, points, the line it is on and what it holds. If it holds something, that click also hands it to you.</p>
      </Section>

      <Section id="instructions" icon={ListOrdered} title="Instructions">
        <p className="text-sm text-text-warm/70">One instruction per line. Upper or lower case both work. Anything after <code className="text-emerald-300">#</code> is a comment. <code className="text-emerald-300">p</code> is a point number. When the program reaches the end it starts again from the top.</p>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-[11px] uppercase tracking-widest text-sp-gold/70 border-b border-sp-bronze/30">
                <th className="py-2 pr-4">Instruction</th><th className="py-2 pr-4">Tool head</th><th className="py-2">What it does</th>
              </tr>
            </thead>
            <tbody>
              {INSTRUCTIONS.map(i => (
                <tr key={i.name} className="border-b border-sp-bronze/10 align-top">
                  <td className="py-2 pr-4 font-mono text-emerald-300 whitespace-nowrap">{i.syntax}</td>
                  <td className="py-2 pr-4 text-text-warm/50 whitespace-nowrap">{i.tool}</td>
                  <td className="py-2 text-text-warm/80">{i.text}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div>
          <p className="text-sm text-sp-gold font-semibold mb-1">IF conditions</p>
          <ul className="grid sm:grid-cols-2 gap-1 text-sm text-text-warm/80">
            {CONDITIONS.map(c => <li key={c.name}><code className="text-emerald-300">IF {c.name}</code> - {c.text}</li>)}
          </ul>
        </div>
        <p className="text-xs text-text-warm/50">Every LOOP, REPEAT and IF needs its own END. You can put blocks inside each other. A program can have up to 512 instructions.</p>
      </Section>

      <Section id="examples" icon={Code2} title="Examples">
        <div className="grid lg:grid-cols-2 gap-5">
          {EXAMPLES.map(ex => (
            <div key={ex.title} className="space-y-2">
              <p className="font-serif text-lg text-sp-gold">{ex.title}</p>
              <p className="text-xs text-text-warm/60"><b>{TOOLS.find(t => t.id === ex.tool)?.label}</b>, {ex.points} points. {ex.setup}</p>
              <CodeBlock code={ex.code} />
            </div>
          ))}
        </div>
      </Section>

      <Section id="try" icon={BookOpen} title="Try it">
        <p className="text-sm text-text-warm/70">Type or paste a program. This check uses exactly the same rules as the arm in the game, so a program that passes here will load.</p>
        <Checker />
      </Section>

      <Section id="tips" icon={Lightbulb} title="Tips and common errors">
        <ul className="list-disc pl-6 space-y-2 text-sm text-text-warm/80">
          <li><b>&quot;point 3 is not taught yet&quot;</b> - teach the arm more points first, then load the book again.</li>
          <li><b>&quot;LOOP has no END&quot;</b> - every LOOP, REPEAT and IF needs its own END line.</li>
          <li><b>Nothing happens</b> - is the arm turning? It needs rotation from below, and a jammed (overloaded) network stops it too. Right-click it to see what it is doing.</li>
          <li><b>&quot;not allowed to break at point 2&quot;</b> - the arm only breaks and places where the player who placed it may build.</li>
          <li>Points remember the block <i>position</i>, not the block: if you swap the chest at point 2 for a barrel, point 2 is now the barrel.</li>
          <li>Removing a point renumbers the ones after it - check your program afterwards.</li>
          <li>Use <code className="text-emerald-300">WAIT</code> in loops that only check things (IF ...) so the arm does not keep spinning in place.</li>
        </ul>
        <p className="text-xs text-text-warm/50">In the game: <code className="text-emerald-300">/sw help</code> links back to this page.</p>
      </Section>
    </div>
  );
}
