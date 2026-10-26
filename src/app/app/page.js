import Link from 'next/link';
import { headers } from 'next/headers';
import ShareButton from '@/components/ShareButton';
import { Smartphone, Download, Bell, CalendarCheck, MessageSquare, SlidersHorizontal, ShieldCheck, Apple, CheckCircle2, Share, SquarePlus, Monitor } from 'lucide-react';

// Download page for the Android app (a native wrapper around this site with
// push notifications — separate Gradle project, see CLAUDE.md). The APK lives
// in public/downloads/ and is served straight by Apache (Document Root is
// public/). When releasing a new app version: replace public/downloads/iekcorbar.apk
// and bump APP_VERSION / APP_SIZE below.
// Share preview (Discord, WhatsApp, Facebook, X, ...). Absolute URLs because
// the root layout has no metadataBase. Image: public/og/iekcorbar-app.jpg
// (1200x630, kept well under WhatsApp's ~300 KB preview limit).
const SHARE_TITLE = 'Iekcorbar in your pocket — the Iekcorbar app';
const SHARE_DESCRIPTION = 'The steampunk & sakura Minecraft server on your phone: chat notifications, event reminders with one-tap sign up, all news and a daily tip. Free for Android & iPhone.';
const SHARE_IMAGE = { url: 'https://iekcorbar.com/og/iekcorbar-app.jpg', width: 1200, height: 630, alt: 'Iekcorbar in your pocket — the Iekcorbar app for Android & iPhone' };

export const metadata = {
  title: 'Iekcorbar App — Android & iPhone',
  description: SHARE_DESCRIPTION,
  alternates: { canonical: 'https://iekcorbar.com/app' },
  openGraph: {
    type: 'website',
    url: 'https://iekcorbar.com/app',
    siteName: 'Iekcorbar',
    title: SHARE_TITLE,
    description: SHARE_DESCRIPTION,
    images: [SHARE_IMAGE]
  },
  twitter: {
    card: 'summary_large_image',
    title: SHARE_TITLE,
    description: SHARE_DESCRIPTION,
    images: [SHARE_IMAGE.url]
  }
};

const APP_VERSION = '1.2.1';
const APP_SIZE = '0.8 MB';
// Goes through /api/app/download so downloads are counted (Staff Panel → Analytics).
const APK_URL = '/api/app/download';

const FEATURES = [
  { icon: MessageSquare, title: 'Chat notifications', text: "See who's messaging you, even when the app is closed." },
  { icon: CalendarCheck, title: 'Never miss an event', text: 'Get notified about new events and projects — sign up straight from the notification.' },
  { icon: Bell, title: 'Everything from the bell', text: 'Friend requests, news, wiki updates, polls and more, right on your phone.' },
  { icon: SlidersHorizontal, title: 'You stay in control', text: 'Choose which notifications you get, or turn them all off, on your Profile page.' },
];

const STEPS = [
  'Tap "Download for Android" on your Android phone.',
  'Open the downloaded file (iekcorbar.apk) from your notifications or Downloads folder.',
  'If Android asks, allow your browser to "install unknown apps" — this is needed for apps that don\'t come from Google Play.',
  'Tap Install, then Open.',
  'Allow notifications when the app asks, and log in with your Iekcorbar account.',
];

export default async function AppDownloadPage() {
  const ua = (await headers()).get('user-agent') || '';
  const inApp = ua.includes('IekcorbarApp/');
  const isIphone = /iPhone|iPad|iPod/.test(ua);

  return (
    <div className="space-y-6 max-w-3xl mx-auto">
      <div className="border-b border-sp-bronze pb-4">
        <h1 className="font-serif text-3xl sm:text-4xl font-bold text-sp-gold uppercase tracking-wider flex items-center gap-3">
          <Smartphone className="w-8 h-8 text-sp-copper shrink-0" /> Iekcorbar App
        </h1>
        <p className="text-sm text-text-warm/70 mt-2">
          The whole Iekcorbar website in your pocket — with push notifications, so you never miss a message, event or project.
        </p>
        <div className="mt-3">
          <ShareButton
            url="/app"
            title="Iekcorbar in your pocket"
            text="Get the Iekcorbar app: chat notifications, event reminders and a daily tip — Android & iPhone."
            label="Share this page"
          />
        </div>
      </div>

      {isIphone && !inApp && (
        <a href="#iphone" className="block bg-sp-gold/10 border border-sp-gold/60 rounded-xl p-4 text-sm text-text-warm/90 hover:bg-sp-gold/15 transition-all">
          <Apple className="inline w-4 h-4 -mt-0.5 text-sp-gold" /> <strong className="text-sp-gold">On iPhone?</strong> No download needed — add Iekcorbar to your home screen in 4 steps. <span className="underline font-bold text-sp-gold">Show me how</span>
        </a>
      )}

      {inApp ? (
        <div className="bg-emerald-950/30 border border-emerald-500/40 rounded-xl p-5 flex items-center gap-3 text-sm text-emerald-200">
          <CheckCircle2 className="w-6 h-6 text-emerald-400 shrink-0" />
          You're already using the Iekcorbar app. Manage your notifications on your{' '}
          <Link href="/profile" className="underline font-bold text-emerald-100">Profile page</Link>.
        </div>
      ) : (
        <div className="bg-neutral-900/40 border-2 border-sp-gold/60 rounded-xl p-6 flex flex-col sm:flex-row items-center gap-5">
          <img src="/app-icons/icon-192.png" alt="Iekcorbar app icon" className="w-20 h-20 rounded-2xl border border-sp-gold/40 shrink-0" />
          <div className="flex-1 text-center sm:text-left space-y-1">
            <p className="font-serif text-xl text-white font-bold">Iekcorbar for Android</p>
            <p className="text-xs text-text-warm/60">Version {APP_VERSION} · {APP_SIZE} · Android 8.0 or newer</p>
          </div>
          <a
            href={APK_URL}
            download="iekcorbar.apk"
            className="flex items-center justify-center gap-2 px-5 py-3 rounded-lg bg-sp-gold hover:bg-white text-black font-bold uppercase tracking-wider text-sm transition-all shadow-md w-full sm:w-auto"
          >
            <Download className="w-4 h-4" /> Download for Android
          </a>
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {FEATURES.map(({ icon: Icon, title, text }) => (
          <div key={title} className="bg-neutral-900/30 border border-sp-bronze/40 rounded-xl p-4 space-y-1">
            <p className="flex items-center gap-2 font-semibold text-sp-gold text-sm">
              <Icon className="w-4 h-4 text-sp-copper shrink-0" /> {title}
            </p>
            <p className="text-xs text-text-warm/75 leading-relaxed">{text}</p>
          </div>
        ))}
      </div>

      {!inApp && (
        <div className="bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-3">
          <h2 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2">How to install</h2>
          <ol className="list-decimal pl-5 space-y-1.5 text-sm text-text-warm/85 leading-relaxed">
            {STEPS.map(step => <li key={step}>{step}</li>)}
          </ol>
          <p className="text-xs text-text-warm/50 leading-relaxed">
            Updating? Just download and install the new version over the old one — you stay logged in.
            The app isn't on Google Play yet; it's coming there soon.
          </p>
        </div>
      )}

      {!inApp && (
        <div id="iphone" className="bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-3">
          <h2 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
            <Apple className="w-5 h-5 text-sp-copper" /> On iPhone
          </h2>
          <p className="text-sm text-text-warm/85 leading-relaxed">
            Add Iekcorbar to your home screen — it opens full-screen like an app and gets the same push notifications
            (iOS 16.4 or newer).
          </p>
          <ol className="space-y-2 text-sm text-text-warm/85">
            <li className="flex gap-3"><span className="font-bold text-sp-gold w-4 shrink-0">1</span><span>Open <strong>iekcorbar.com</strong> in <strong>Safari</strong>.</span></li>
            <li className="flex gap-3"><span className="font-bold text-sp-gold w-4 shrink-0">2</span><span>Tap <Share className="inline w-4 h-4 -mt-0.5 text-sp-copper" /> <strong>Share</strong>, then <SquarePlus className="inline w-4 h-4 -mt-0.5 text-sp-copper" /> <strong>Add to Home Screen</strong>, then <strong>Add</strong>.</span></li>
            <li className="flex gap-3"><span className="font-bold text-sp-gold w-4 shrink-0">3</span><span>Open <strong>Iekcorbar</strong> from your home screen and log in.</span></li>
            <li className="flex gap-3"><span className="font-bold text-sp-gold w-4 shrink-0">4</span><span>Go to your <Link href="/profile" className="text-sp-gold hover:text-white underline font-bold">Profile</Link>, tap <strong>Enable notifications</strong> and choose <strong>Allow</strong>.</span></li>
          </ol>
          <p className="text-xs text-text-warm/50">A full App Store version may follow later.</p>
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs text-text-warm/70">
        <div className="bg-neutral-900/30 border border-sp-bronze/30 rounded-xl p-4 flex gap-3">
          <ShieldCheck className="w-5 h-5 text-sp-copper shrink-0" />
          <p>
            No ads, no tracking. Read what the app does with your data in our{' '}
            <Link href="/privacy" className="text-sp-gold hover:text-white underline font-bold">Privacy Policy</Link>.
          </p>
        </div>
        <div className="bg-neutral-900/30 border border-sp-bronze/30 rounded-xl p-4 flex gap-3">
          <Monitor className="w-5 h-5 text-sp-copper shrink-0" />
          <p>
            On a computer? Turn on notifications for your browser on your{' '}
            <Link href="/profile" className="text-sp-gold hover:text-white underline font-bold">Profile page</Link>.
          </p>
        </div>
      </div>
    </div>
  );
}
