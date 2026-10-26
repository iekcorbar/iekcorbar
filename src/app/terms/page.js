import { FileText, ShieldAlert, Dice5, UserX, Scale, MessageSquare } from 'lucide-react';

// Static Terms of Service — unlike /rules (in-game conduct, staff-editable
// via Settings > Rules Manager), this is a legal document that shouldn't
// change often or casually, so it's plain hardcoded content rather than a
// new staff CMS feature. A drafted starting point, not legal advice — see
// the notice at the top of the page itself, and have it reviewed by
// someone qualified before treating it as final, especially the wagering
// clause given the real-money-adjacent mechanics it's meant to cover.
export const metadata = {
  title: 'Terms of Service — iekcorbar'
};

function Section({ icon: Icon, title, children }) {
  return (
    <div className="bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-3">
      <h2 className="font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2 border-b border-sp-bronze/20 pb-2">
        <Icon className="w-5 h-5 text-sp-copper shrink-0" /> {title}
      </h2>
      <div className="space-y-2 text-sm text-text-warm/85 leading-relaxed">
        {children}
      </div>
    </div>
  );
}

export default function TermsPage() {
  return (
    <div className="space-y-6 max-w-3xl mx-auto">
      <div className="border-b border-sp-bronze pb-4">
        <h1 className="font-serif text-3xl font-bold text-sp-gold tracking-wide flex items-center gap-2 uppercase">
          <FileText className="w-8 h-8 text-sp-copper" /> Terms of Service
        </h1>
        <p className="text-xs text-text-warm/60 mt-1" suppressHydrationWarning>Last updated: 7 September 2026.</p>
      </div>

      <div className="bg-amber-500/10 border border-amber-500/40 text-amber-200 text-xs p-4 rounded-lg leading-relaxed">
        This is a good-faith draft written to cover the site's actual features accurately, not a document reviewed by a lawyer. Treat it as a starting point — get it checked by someone qualified before relying on it, particularly the wagering disclaimer in section 3.
      </div>

      <Section icon={ShieldAlert} title="1. Acceptance & Age Requirement">
        <p>By creating an account or otherwise using iekcorbar's website or Minecraft server, you agree to these terms and to the in-game <a href="/rules" className="text-sp-gold hover:text-white underline">Server Rules</a>.</p>
        <p>
          <strong>You must be at least 18 years old to register an account or play on this server.</strong> This is a condition of use, not something the registration process technically verifies — by registering, you're confirming it's true. Staff may remove any account found to violate this.
        </p>
      </Section>

      <Section icon={FileText} title="2. Accounts">
        <p>Registration links a real Minecraft account (whitelisted via in-game verification) to a website login. You're responsible for keeping your password secure and for anything done from your account. One account per person — alternate accounts used to bypass a ban, farm rewards, or vote multiple times are against the rules and may be removed.</p>
        <p>You may request deletion of your account and associated data at any time — see the Privacy Policy (GDPR) link in the site footer for how.</p>
      </Section>

      <Section icon={Dice5} title="3. Virtual Currency, Items & Chance-Based Features">
        <p>The website and server include currencies and items that exist only inside this game (in-game Vault balance, website "Gears," brewing results, achievement rewards, and similar). Some features involve randomness — for example brewing quality outcomes, or in-game mob/boss loot.</p>
        <p><strong>None of this currency or these items can be bought with real money, sold for real money, or exchanged for anything of real-world value, on this website or anywhere we operate.</strong> They exist purely for entertainment within the game, are earned only by playing, have no cash value, and cannot be cashed out. Nothing on iekcorbar is a game of chance played for real-world stakes, and nothing here should be understood as gambling.</p>
        <p>We can change, rebalance, or remove any in-game currency, item, or chance-based feature at any time without compensation, since none of it has real-world value to compensate.</p>
      </Section>

      <Section icon={UserX} title="4. Staff Authority & Enforcement">
        <p>Staff (moderators and admins) enforce the <a href="/rules" className="text-sp-gold hover:text-white underline">Server Rules</a> and may warn, kick, temporarily ban, permanently ban, or delete accounts that violate them or these terms, at their reasonable discretion. Staff decisions can be appealed via our Discord server, but the final call rests with staff.</p>
      </Section>

      <Section icon={ShieldAlert} title="5. Service Availability & Liability">
        <p>iekcorbar is run on a best-effort basis. We don't guarantee the website or Minecraft server will be available at any given time, free of bugs, or that any data (including in-game progress, journal entries, or achievements) will never be lost — see our <a href="/wiki" className="text-sp-gold hover:text-white underline">Wiki</a> for how features actually work. Use it at your own risk; we're not liable for lost progress, downtime, or indirect damages arising from using the site or server.</p>
        <p>iekcorbar is an independent community project. It is not affiliated with, endorsed by, or sponsored by Mojang Studios or Microsoft.</p>
      </Section>

      <Section icon={Scale} title="6. Changes to These Terms">
        <p>We may update these terms as the site changes. Continuing to use iekcorbar after an update means you accept the revised terms. Significant changes will be announced via the <a href="/updates" className="text-sp-gold hover:text-white underline">Updates</a> page or Discord.</p>
      </Section>

      <Section icon={MessageSquare} title="7. Governing Law & Contact">
        <p>These terms are governed by the laws of the Netherlands. Questions about these terms, or about your account, can be raised with staff via our official Discord server.</p>
      </Section>
    </div>
  );
}
