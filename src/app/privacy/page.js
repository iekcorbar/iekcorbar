import Link from 'next/link';
import { ShieldAlert, Database, Target, Lock, UserX, Cookie, Smartphone, Scale, BarChart3 } from 'lucide-react';

// Full privacy policy on its own URL — the footer's Privacy Policy modal
// (providers.js) is the short version; app stores (Google Play) require a
// public URL, and this version also covers the Android app (push tokens via
// Firebase Cloud Messaging, notification preferences). Keep the two in sync
// when the policy genuinely changes, and bump LAST_UPDATED.
export const metadata = {
  title: 'Privacy Policy — iekcorbar'
};

const LAST_UPDATED = '27 September 2026';

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

export default function PrivacyPage() {
  return (
    <div className="space-y-6 max-w-3xl mx-auto">
      <div className="border-b border-sp-bronze pb-4">
        <h1 className="font-serif text-3xl sm:text-4xl font-bold text-sp-gold uppercase tracking-wider flex items-center gap-3">
          <ShieldAlert className="w-8 h-8 text-sp-copper shrink-0" /> Privacy Policy
        </h1>
        <p className="text-sm text-text-warm/70 mt-2">
          <strong>Iekcorbar</strong> places high value on protecting the personal data of its players. In accordance with the
          General Data Protection Regulation (GDPR), this page explains what data the website and the Iekcorbar Android app use,
          and why.
        </p>
      </div>

      <Section icon={Database} title="1. What data do we collect?">
        <p>When you create an account, we store:</p>
        <ul className="list-disc pl-5 space-y-1">
          <li>Your Minecraft username (IGN)</li>
          <li>Your email address</li>
          <li>Your password (cryptographically secured via hashing — never stored in plain text)</li>
        </ul>
        <p>While you use the site, we also store what you create or do on it yourself: chat messages, journal entries, event and project sign-ups, votes, friendships and your notification history.</p>
      </Section>

      <Section icon={Smartphone} title="2. The Android app">
        <p>The Iekcorbar app shows this same website. On top of the data above, the app only uses:</p>
        <ul className="list-disc pl-5 space-y-1">
          <li><strong>A push notification token</strong> — a random identifier for your device, issued by Google Firebase Cloud Messaging, linked to your account when you log in and removed when you log out.</li>
          <li><strong>Your notification settings</strong> — which kinds of push notifications you want (Profile page).</li>
        </ul>
        <p>
          Push notifications are delivered through Google Firebase Cloud Messaging, which processes the token and the
          notification content (for example the sender and text of a chat message) solely to deliver it to your device.
          The app does not collect your location, contacts or advertising ID, and contains no ads.
        </p>
        <p>You can switch push notifications off entirely or per category on your Profile page, or in your phone's Android settings.</p>
      </Section>

      <Section icon={BarChart3} title="3. Usage statistics">
        <p>
          To see how the website and app are used, we keep simple usage statistics in our own database — no Google Analytics
          or other third-party trackers, and no cookies:
        </p>
        <ul className="list-disc pl-5 space-y-1">
          <li>Which pages are opened, when, and from what kind of device, browser and app version.</li>
          <li>The website that referred you here (for example Discord), if any.</li>
          <li>If you're logged in, this is linked to your account so staff can see who uses the website and app. If you're not logged in, visits are only counted per day using an anonymous code that can't be traced back to you or your IP address.</li>
          <li>How often the Android app is downloaded.</li>
        </ul>
        <p>Only staff can see these statistics. They are never sold or shared, and are deleted automatically after 90 days.</p>
      </Section>

      <Section icon={Target} title="4. Purpose of data processing">
        <p>Your personal data is used exclusively for the operation and security of our services:</p>
        <ul className="list-disc pl-5 space-y-1">
          <li>Verifying and whitelisting your account on our Minecraft server.</li>
          <li>Letting you log in to the website and app, and managing your player profile.</li>
          <li>Sending you the notifications you've chosen to receive.</li>
          <li>Preventing abuse, account theft, and unauthorized registration under someone else's name.</li>
        </ul>
      </Section>

      <Section icon={Lock} title="5. Retention & security">
        <p>
          Passwords are hashed with a per-account random salt and are never stored or logged in plain text. Your account data is
          stored in a database run by our hosting provider, who processes it only on our behalf. We don't sell your data or hand
          it to anyone else. Your data is kept as long as your account is active, or until you delete it (see below).
        </p>
      </Section>

      <Section icon={UserX} title="6. Your rights (access & erasure)">
        <p>
          You have the right at all times to inspect, correct, or completely delete your data (Right to be Forgotten). You can
          delete your own account, whitelist entry, and associated data (journal, friendships, notifications, push settings and
          device tokens) yourself at any time from the Danger Zone on your <Link href="/profile" className="text-sp-gold hover:text-white underline font-bold">Profile page</Link> —
          in the website and in the app. If you'd rather have staff do it for you, contact the server administrators via our
          official Discord server.
        </p>
      </Section>

      <Section icon={Cookie} title="7. Cookies & local storage">
        <p>
          We don't use tracking, advertising, or marketing cookies. We only use what's needed to keep you logged in: a secure
          session cookie and your browser's local storage. The app additionally stores its push notification token on your device.
        </p>
      </Section>

      <Section icon={Scale} title="8. Account eligibility">
        <p>
          Registering requires you to meet the age requirement in our{' '}
          <Link href="/terms" className="text-sp-gold hover:text-white underline font-bold">Terms of Service</Link>.
        </p>
      </Section>

      <p className="text-center text-xs text-text-warm/40">Last updated: {LAST_UPDATED}</p>
    </div>
  );
}
