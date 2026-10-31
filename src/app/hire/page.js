import { Hammer, Globe, Server, Sparkles, Wallet, Clock, ExternalLink } from 'lucide-react';

export const metadata = {
  title: 'Hire the builder | iekcorbar',
  description: 'Websites, Minecraft servers and custom tools, made to measure by the person who builds iekcorbar. Prices depend on the scope; nothing is too crazy.',
};

// "Hire me" page (added 2026-11-01). The owner offers websites and servers as a
// spare-time service; the main portfolio lives on vklingeren.nl and this page
// only explains the idea and sends people there.
const PORTFOLIO_URL = 'https://vklingeren.nl';

const CARD = 'bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-3';
const H2 = 'font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2 border-b border-sp-bronze/20 pb-2';
const P = 'text-sm text-text-warm/85 leading-relaxed';

export default function HirePage() {
  return (
    <div className="space-y-6 max-w-3xl mx-auto">
      <div className="border-b border-sp-bronze pb-4">
        <h1 className="font-serif text-3xl font-bold text-sp-gold tracking-wide flex items-center gap-2 uppercase">
          <Hammer className="w-8 h-8 text-sp-copper" /> Hire the builder
        </h1>
        <p className="text-sm text-text-warm/70 mt-2">
          Everything you see on iekcorbar &ndash; the website, the plugins, the models, the tooling &ndash; is
          built by one person. If you want something like it for yourself, you can hire me.
        </p>
      </div>

      <div className={CARD}>
        <h2 className={H2}>
          <Globe className="w-5 h-5 text-sp-copper" /> What I build
        </h2>
        <ul className="list-disc pl-5 space-y-2 text-sm text-text-warm/85 leading-relaxed">
          <li><strong>Websites</strong> &ndash; from a clean landing page to a full community site with accounts, a shop, a wiki or a staff panel.</li>
          <li><strong>Servers</strong> &ndash; Minecraft servers set up, tuned and themed, including custom plugins, resource packs and models.</li>
          <li><strong>Custom tools</strong> &ndash; dashboards, internal apps, bots and integrations that tie your server, site and Discord together.</li>
        </ul>
      </div>

      <div className={CARD}>
        <h2 className={H2}>
          <Sparkles className="w-5 h-5 text-sp-copper" /> Made to measure
        </h2>
        <p className={P}>
          Everything is made <strong>to order</strong> &ndash; no templates, no copy-paste. You tell me
          what you have in mind and we build exactly that. <strong>Nothing is too crazy</strong>: if you
          can describe it, we can usually find a way to make it work.
        </p>
      </div>

      <div className={CARD}>
        <h2 className={H2}>
          <Wallet className="w-5 h-5 text-sp-copper" /> What does it cost?
        </h2>
        <p className={P}>
          There is no fixed price list. <strong>Costs are open to discussion</strong> and depend on the
          size and complexity of the project &ndash; a small site is a different job from a whole server
          network. Tell me what you need, and we agree on something that is fair for both sides before any
          work starts.
        </p>
      </div>

      <div className={CARD}>
        <h2 className={H2}>
          <Clock className="w-5 h-5 text-sp-copper" /> A spare-time thing
        </h2>
        <p className={P}>
          This is something I do in my <strong>free time</strong>, next to running iekcorbar &ndash; it is
          not a full-time agency. That means personal attention and a lot of care, but also that
          timelines are agreed together and that I may not be able to start right away. I would rather
          tell you honestly what is realistic than promise too much.
        </p>
      </div>

      <div className="text-center pt-2 space-y-3">
        <a
          href={PORTFOLIO_URL}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center gap-2 px-8 py-4 rounded-xl bg-sp-gold hover:bg-white text-black font-serif font-bold uppercase tracking-wider text-sm transition-all shadow-lg shadow-sp-gold/20"
        >
          <Server className="w-4 h-4" /> See my work &amp; get in touch <ExternalLink className="w-4 h-4" />
        </a>
        <p className="text-xs text-text-warm/50">Opens vklingeren.nl in a new tab.</p>
      </div>
    </div>
  );
}
