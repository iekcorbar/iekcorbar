import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { apiFetch, ApiError } from '@/lib/api';
import { registerForPushNotificationsAsync, unregisterPushToken } from '@/lib/push-notifications';

// Mirrors the website's user shape (src/app/providers.js `useUser()`), minus the
// password (the API already strips that). No id field — everything here (and on
// the website) identifies users by email; see the note in db.js on device_tokens.
export type IekcorbarUser = {
  email: string;
  role: string;
  mcUsername?: string;
  customSkinUrl?: string;
};

const STORAGE_KEY = 'iekcorbar_user';
const PUSH_TOKEN_KEY = 'iekcorbar_push_token';

type AuthContextValue = {
  user: IekcorbarUser | null;
  loading: boolean;
  error: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<IekcorbarUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY)
      .then(raw => { if (raw) setUser(JSON.parse(raw)); })
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    setError(null);
    try {
      const res = await apiFetch<{ success: boolean; user: IekcorbarUser }>('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password })
      });
      await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(res.user));
      setUser(res.user);

      // Best-effort — a failed push registration shouldn't block login.
      try {
        const token = await registerForPushNotificationsAsync(res.user.email);
        if (token) await AsyncStorage.setItem(PUSH_TOKEN_KEY, token);
      } catch (pushErr) {
        console.warn('Push registration failed:', pushErr);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Inloggen mislukt');
      throw err;
    }
  }, []);

  const logout = useCallback(async () => {
    const token = await AsyncStorage.getItem(PUSH_TOKEN_KEY);
    if (token) {
      try { await unregisterPushToken(token); } catch { /* best-effort */ }
      await AsyncStorage.removeItem(PUSH_TOKEN_KEY);
    }
    await AsyncStorage.removeItem(STORAGE_KEY);
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, error, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
