'use client';

import React, { useState, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { KeyRound, CheckCircle2, XCircle } from 'lucide-react';

const MIN_PASSWORD_LENGTH = 8;

// Landing page for the link mailed by api/auth/forgot-password's 'request'
// action. A client component (not the server-component pattern used by
// newsletter/unsubscribe) because setting a new password needs a real form,
// not a one-click GET — the token is only ever consumed via the POST below.
function ResetPasswordForm() {
  const searchParams = useSearchParams();
  const token = searchParams.get('token');

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  if (!token) {
    return (
      <div className="max-w-md mx-auto py-24 px-4 text-center space-y-4">
        <XCircle className="w-10 h-10 text-rose-400 mx-auto" />
        <h1 className="font-serif text-xl text-rose-300 uppercase tracking-wide">Link Not Valid</h1>
        <p className="text-sm text-text-warm/70 leading-relaxed">
          This page needs a reset link from the "Forgot password?" email. Request a new one from the login screen.
        </p>
      </div>
    );
  }

  if (done) {
    return (
      <div className="max-w-md mx-auto py-24 px-4 text-center space-y-4">
        <CheckCircle2 className="w-10 h-10 text-green-400 mx-auto" />
        <h1 className="font-serif text-xl text-sp-gold uppercase tracking-wide">Password Reset</h1>
        <p className="text-sm text-text-warm/70 leading-relaxed">
          Your password has been changed. You've been logged out everywhere else for safety — log in again with your new password.
        </p>
        <Link href="/" className="inline-block mt-2 text-xs text-sp-copper hover:text-sp-gold hover:underline">
          Back to iekcorbar
        </Link>
      </div>
    );
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      setError(`Password must be at least ${MIN_PASSWORD_LENGTH} characters`);
      return;
    }
    if (newPassword !== confirmPassword) {
      setError("Passwords don't match");
      return;
    }

    setSubmitting(true);
    try {
      const res = await fetch('/api/auth/forgot-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'reset', token, newPassword })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setDone(true);
      } else {
        setError(data.error || 'Something went wrong. Try requesting a new link.');
      }
    } catch {
      setError('Network error — please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="max-w-md mx-auto py-16 px-4">
      <div className="bg-sp-dark border-2 border-sp-gold p-6 rounded-xl steampunk-border shadow-2xl">
        <h1 className="font-serif text-2xl text-sp-gold mb-1 flex items-center gap-2 uppercase tracking-wide">
          <KeyRound className="w-6 h-6 text-sp-copper" /> Reset Password
        </h1>
        <p className="text-xs text-text-warm/60 mb-6">Choose a new password for your account.</p>

        {error && (
          <div className="mb-4 bg-crimson/20 border border-crimson text-rose-200 text-sm px-3 py-2 rounded-lg">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold uppercase text-sp-gold/80 mb-1">New Password</label>
            <input
              type="password"
              required
              minLength={MIN_PASSWORD_LENGTH}
              value={newPassword}
              onChange={e => setNewPassword(e.target.value)}
              className="w-full bg-neutral-900 border border-sp-bronze text-white rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
              placeholder="••••••••"
              autoFocus
            />
          </div>
          <div>
            <label className="block text-xs font-semibold uppercase text-sp-gold/80 mb-1">Confirm New Password</label>
            <input
              type="password"
              required
              minLength={MIN_PASSWORD_LENGTH}
              value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              className="w-full bg-neutral-900 border border-sp-bronze text-white rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
              placeholder="••••••••"
            />
          </div>
          <button
            type="submit"
            disabled={submitting}
            className="w-full py-2.5 rounded-lg text-sm font-semibold uppercase tracking-wider text-black bg-sp-gold hover:bg-white hover:shadow-lg transition-all cursor-pointer font-serif border border-transparent shadow-md disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {submitting ? 'Saving...' : 'Set New Password'}
          </button>
        </form>
      </div>
    </div>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={
      <div className="max-w-md mx-auto py-24 px-4 text-center">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-sp-gold mx-auto"></div>
      </div>
    }>
      <ResetPasswordForm />
    </Suspense>
  );
}
