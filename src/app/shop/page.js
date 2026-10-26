'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { useUser } from '../providers';
import {
  ShoppingBag, Coins, Loader2, RefreshCw, Plus, Trash2, Edit, X,
  CheckCircle2, AlertTriangle, Clock, Settings, Package, Power, Cog, Sparkles
} from 'lucide-react';

const emptyItemForm = { id: null, slug: '', name: '', description: '', price: '', giveCommand: '', imageUrl: '', active: true, currency: 'balance' };

function StatusBadge({ status }) {
  const map = {
    delivered: { label: 'Delivered', cls: 'bg-emerald-600/20 border-emerald-500/40 text-emerald-400', Icon: CheckCircle2 },
    // Covers two different situations with one status: paid but the give
    // command wasn't attempted/succeeded yet — either you were offline (it
    // delivers itself automatically on your next join, see
    // MySQLSync.processQueuedShopPurchases) or something else went wrong
    // while you were online (needs the Retry Delivery button below). Kept
    // deliberately neutral rather than claiming either one.
    queued: { label: 'Queued', cls: 'bg-amber-500/15 border-amber-500/40 text-amber-300', Icon: Clock },
    failed: { label: 'Failed', cls: 'bg-crimson/15 border-crimson/40 text-rose-300', Icon: AlertTriangle }
  };
  const m = map[status] || map.failed;
  const Icon = m.Icon;
  return (
    <span className={`inline-flex items-center gap-1 text-[9px] px-2 py-0.5 rounded-md font-bold uppercase tracking-wider border ${m.cls}`}>
      <Icon className="w-3 h-3" /> {m.label}
    </span>
  );
}

export default function ShopPage() {
  const { user } = useUser();
  const isStaff = user && (user.role === 'admin' || user.role === 'moderator');

  const [items, setItems] = useState([]);
  const [shopEnabled, setShopEnabled] = useState(true);
  const [loading, setLoading] = useState(true);
  const [balance, setBalance] = useState(null);
  const [coins, setCoins] = useState(null);
  const [balanceLoading, setBalanceLoading] = useState(false);
  const [togglingShop, setTogglingShop] = useState(false);
  // Website-only "Gears" currency (see api/currency) — entirely separate
  // from the real in-game Vault `balance` above, doesn't need mcUsername or
  // RCON at all to read.
  const [currency, setCurrency] = useState(null);

  const [purchases, setPurchases] = useState([]);
  const [buyingId, setBuyingId] = useState(null);
  const [purchaseFeedback, setPurchaseFeedback] = useState(null);

  const [itemForm, setItemForm] = useState(emptyItemForm);
  const [showItemForm, setShowItemForm] = useState(false);
  const [savingItem, setSavingItem] = useState(false);
  const [formError, setFormError] = useState('');

  const [takeCommand, setTakeCommand] = useState('');
  const [coinsTakeCommand, setCoinsTakeCommand] = useState('');
  const [savingSettings, setSavingSettings] = useState(false);
  const [settingsFeedback, setSettingsFeedback] = useState('');

  const [allPurchases, setAllPurchases] = useState([]);
  const [showStaffPurchases, setShowStaffPurchases] = useState(false);
  const [retryingId, setRetryingId] = useState(null);

  const fetchItems = useCallback(async () => {
    try {
      const res = await fetch('/api/shop/items');
      const data = await res.json();
      setItems(data.items || []);
      if (typeof data.enabled === 'boolean') setShopEnabled(data.enabled);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchBalance = useCallback(async () => {
    if (!user?.mcUsername) return;
    setBalanceLoading(true);
    try {
      const res = await fetch(`/api/player-stats?username=${encodeURIComponent(user.mcUsername)}`);
      const data = await res.json();
      if (res.ok && typeof data.balance === 'number') setBalance(data.balance);
      if (res.ok && typeof data.eliteCoins === 'number') setCoins(data.eliteCoins);
    } catch (err) {
      console.error(err);
    } finally {
      setBalanceLoading(false);
    }
  }, [user]);

  const fetchCurrency = useCallback(async () => {
    if (!user) return;
    try {
      const res = await fetch('/api/currency');
      const data = await res.json();
      if (res.ok && typeof data.currency === 'number') setCurrency(data.currency);
    } catch (err) {
      console.error(err);
    }
  }, [user]);

  const fetchPurchases = useCallback(async () => {
    if (!user) return;
    try {
      const res = await fetch('/api/shop/purchases');
      const data = await res.json();
      if (res.ok) setPurchases(data.purchases || []);
    } catch (err) {
      console.error(err);
    }
  }, [user]);

  const fetchAllPurchases = useCallback(async () => {
    if (!isStaff) return;
    try {
      const res = await fetch('/api/shop/purchases?all=true');
      const data = await res.json();
      if (res.ok) setAllPurchases(data.purchases || []);
    } catch (err) {
      console.error(err);
    }
  }, [isStaff]);

  const fetchSettings = useCallback(async () => {
    if (!isStaff) return;
    try {
      const res = await fetch('/api/shop/settings');
      const data = await res.json();
      if (res.ok) { setTakeCommand(data.settings?.takeCommand || ''); setCoinsTakeCommand(data.settings?.coinsTakeCommand || ''); }
    } catch (err) {
      console.error(err);
    }
  }, [isStaff]);

  useEffect(() => { fetchItems(); }, [fetchItems]);
  useEffect(() => { fetchBalance(); fetchCurrency(); fetchPurchases(); }, [fetchBalance, fetchCurrency, fetchPurchases]);
  useEffect(() => { fetchAllPurchases(); fetchSettings(); }, [fetchAllPurchases, fetchSettings]);

  async function handleBuy(item) {
    if (!user) return;
    const isWebsiteCurrency = item.currency === 'website';
    const moneyLabel = item.currency === 'website' ? ' Gears' : item.currency === 'coins' ? ' Elite coins' : '';
    if (!confirm(`Buy "${item.name}" for ${item.price}${moneyLabel}?`)) return;
    setBuyingId(item.id);
    setPurchaseFeedback(null);
    try {
      const res = await fetch('/api/shop/purchase', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ itemId: item.id })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        let feedbackText;
        if (isWebsiteCurrency) {
          // No RCON involved at all for these — nothing to report from the
          // server, so the message is purely about the website-side result.
          feedbackText = `Purchased "${item.name}" with your Gears!`;
        } else if (data.status === 'delivered') {
          feedbackText = `Purchased "${item.name}"! Server replied: "${data.giveMessage}"`;
        } else if (data.willAutoDeliverOnJoin) {
          // Not a failure — you just weren't online. No action needed here;
          // IekcorbarSiteSync delivers it automatically the moment you join.
          feedbackText = `Purchased "${item.name}"! You'll receive it in-game automatically the next time you join the server.`;
        } else {
          feedbackText = `Payment taken, but delivery failed and has been queued — use "Retry Delivery" below. Server replied: "${data.giveMessage}"`;
        }
        setPurchaseFeedback({ type: isWebsiteCurrency || data.status === 'delivered' || data.willAutoDeliverOnJoin ? 'success' : 'warn', text: feedbackText });
        if (typeof data.newBalance === 'number') setBalance(data.newBalance);
        if (typeof data.newCoins === 'number') setCoins(data.newCoins);
        fetchCurrency();
        fetchPurchases();
      } else {
        setPurchaseFeedback({ type: 'error', text: data.error || 'Purchase failed.' });
      }
    } catch (err) {
      setPurchaseFeedback({ type: 'error', text: 'Network error. Please try again.' });
    } finally {
      setBuyingId(null);
    }
  }

  async function handleRetry(purchaseId) {
    setRetryingId(purchaseId);
    try {
      const res = await fetch('/api/shop/purchases', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ purchaseId })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        fetchPurchases();
        fetchAllPurchases();
      } else {
        alert(data.error || 'Retry failed.');
      }
    } catch (err) {
      alert('Network error. Please try again.');
    } finally {
      setRetryingId(null);
    }
  }

  function openNewItemForm() {
    setItemForm(emptyItemForm);
    setFormError('');
    setShowItemForm(true);
  }

  function openEditItemForm(item) {
    setItemForm({
      id: item.id, slug: item.slug, name: item.name, description: item.description || '',
      price: String(item.price), giveCommand: item.giveCommand || '', imageUrl: item.imageUrl || '', active: item.active,
      currency: item.currency === 'website' ? 'website' : 'balance'
    });
    setFormError('');
    setShowItemForm(true);
  }

  async function handleItemSubmit(e) {
    e.preventDefault();
    setFormError('');
    const isWebsiteCurrency = itemForm.currency === 'website';
    if (!itemForm.slug.trim() || !itemForm.name.trim() || itemForm.price === '' || (!isWebsiteCurrency && !itemForm.giveCommand.trim())) {
      setFormError(isWebsiteCurrency ? 'Slug, name and price are required.' : 'Slug, name, price and give command are required.');
      return;
    }
    setSavingItem(true);
    try {
      const res = await fetch('/api/shop/items', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: itemForm.id, slug: itemForm.slug.trim(), name: itemForm.name.trim(),
          description: itemForm.description.trim(), price: Number(itemForm.price),
          giveCommand: isWebsiteCurrency ? null : itemForm.giveCommand.trim(), imageUrl: itemForm.imageUrl.trim(), active: itemForm.active,
          currency: itemForm.currency
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setItems(data.items);
        setShowItemForm(false);
      } else {
        setFormError(data.error || 'Could not save item.');
      }
    } catch (err) {
      setFormError('Network error. Please try again.');
    } finally {
      setSavingItem(false);
    }
  }

  async function handleDeleteItem(id) {
    if (!confirm('Delete this shop item?')) return;
    try {
      const res = await fetch(`/api/shop/items?id=${encodeURIComponent(id)}`, { method: 'DELETE' });
      const data = await res.json();
      if (res.ok && data.success) setItems(data.items);
    } catch (err) {
      console.error(err);
    }
  }

  async function handleToggleShop() {
    setTogglingShop(true);
    try {
      const res = await fetch('/api/shop/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled: !shopEnabled })
      });
      const data = await res.json();
      if (res.ok && data.success) setShopEnabled(data.settings.enabled);
      else alert(data.error || 'Could not toggle the shop.');
    } catch (err) {
      alert('Network error. Please try again.');
    } finally {
      setTogglingShop(false);
    }
  }

  async function handleSaveSettings(e) {
    e.preventDefault();
    setSavingSettings(true);
    setSettingsFeedback('');
    try {
      const res = await fetch('/api/shop/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ takeCommand, coinsTakeCommand })
      });
      const data = await res.json();
      setSettingsFeedback(res.ok && data.success ? 'Saved!' : (data.error || 'Could not save.'));
    } catch (err) {
      setSettingsFeedback('Network error.');
    } finally {
      setSavingSettings(false);
      setTimeout(() => setSettingsFeedback(''), 3000);
    }
  }

  return (
    <div className="space-y-8">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-sp-bronze pb-5">
        <div>
          <h1 className="font-serif text-3xl font-bold text-sp-gold tracking-wide flex items-center gap-2 uppercase">
            <ShoppingBag className="w-8 h-8 text-sp-copper" /> Steampunk Trading Post
          </h1>
          <p className="text-xs text-text-warm/60 mt-1">Spend your in-game balance on items delivered straight to you — no real money involved.</p>
        </div>

        {user?.role === 'admin' && (
          <button
            onClick={handleToggleShop}
            disabled={togglingShop}
            className={`flex items-center gap-1.5 px-3 py-2 text-[10px] font-bold uppercase rounded-lg cursor-pointer transition-all disabled:opacity-50 border ${
              shopEnabled ? 'border-emerald-500/40 bg-emerald-600/10 text-emerald-400 hover:bg-emerald-600/20' : 'border-crimson/40 bg-crimson/10 text-rose-300 hover:bg-crimson/20'
            }`}
            title="Only visible to admins"
          >
            <Power className="w-3.5 h-3.5" /> Shop is {shopEnabled ? 'Open' : 'Closed'} — click to {shopEnabled ? 'close' : 'open'}
          </button>
        )}

        {user && (
          <div className="flex flex-wrap gap-3">
            <div className="bg-neutral-900/40 border border-sp-bronze/35 px-5 py-3 rounded-xl flex items-center gap-3">
              <Coins className="w-6 h-6 text-sp-gold" />
              <div>
                <span className="block text-[9px] uppercase font-bold text-text-warm/40">Your Balance</span>
                <span className="font-serif text-xl font-black text-white">
                  {balanceLoading ? <Loader2 className="w-4 h-4 animate-spin inline" /> : (balance ?? '—')}
                </span>
              </div>
              <button onClick={fetchBalance} className="p-1.5 text-text-warm/40 hover:text-sp-gold cursor-pointer" title="Refresh balance">
                <RefreshCw className="w-3.5 h-3.5" />
              </button>
            </div>
            <div className="bg-neutral-900/40 border border-sp-bronze/35 px-5 py-3 rounded-xl flex items-center gap-3">
              <Sparkles className="w-6 h-6 text-sp-copper" />
              <div>
                <span className="block text-[9px] uppercase font-bold text-text-warm/40">Your Elite coins</span>
                <span className="font-serif text-xl font-black text-white">{coins ?? '—'}</span>
              </div>
            </div>
            <div className="bg-neutral-900/40 border border-sp-bronze/35 px-5 py-3 rounded-xl flex items-center gap-3">
              <Cog className="w-6 h-6 text-sp-copper" />
              <div>
                <span className="block text-[9px] uppercase font-bold text-text-warm/40">Your Gears</span>
                <span className="font-serif text-xl font-black text-white">{currency ?? '—'}</span>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* How It Works — new players won't know the flow otherwise */}
      <div className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-5">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs text-text-warm/70">
          <div className="flex gap-3 items-start">
            <div className="w-7 h-7 rounded-full bg-sp-gold/15 border border-sp-gold/30 flex items-center justify-center text-sp-gold font-bold text-xs shrink-0">1</div>
            <div><span className="block text-white font-semibold text-sm">Three currencies</span>Most items spend your normal in-game balance (play on the server to earn it). Elite-coin items spend the coins you earn fighting EliteMobs monsters — those need you online. A few are website-only — those spend Gears instead, earned from achievements.</div>
          </div>
          <div className="flex gap-3 items-start">
            <div className="w-7 h-7 rounded-full bg-sp-gold/15 border border-sp-gold/30 flex items-center justify-center text-sp-gold font-bold text-xs shrink-0">2</div>
            <div><span className="block text-white font-semibold text-sm">Buy anytime</span>Balance and Elite-coin items still deliver automatically the next time you join if you&apos;re offline (paying in coins does need you online). Gears items need the Minecraft server for nothing at all.</div>
          </div>
          <div className="flex gap-3 items-start">
            <div className="w-7 h-7 rounded-full bg-sp-gold/15 border border-sp-gold/30 flex items-center justify-center text-sp-gold font-bold text-xs shrink-0">3</div>
            <div><span className="block text-white font-semibold text-sm">Check your delivery</span>"Your Purchases" below always shows the real status — check there if anything looks off.</div>
          </div>
        </div>
      </div>

      {!shopEnabled && (
        <div className="bg-crimson/10 border border-crimson/30 rounded-xl p-4 text-center text-rose-300 text-sm font-bold uppercase tracking-wide">
          The shop is currently closed. Browsing is still possible, but purchases are disabled.
        </div>
      )}

      {!user && (
        <div className="bg-neutral-900/20 border border-sp-bronze/20 rounded-xl p-6 text-center text-text-warm/50 text-sm">
          Log in and link your Minecraft account to purchase items.
        </div>
      )}

      {purchaseFeedback && (
        <div className={`rounded-lg p-3 text-xs flex items-start gap-2 border ${
          purchaseFeedback.type === 'success' ? 'bg-emerald-600/10 border-emerald-500/30 text-emerald-300' :
          purchaseFeedback.type === 'warn' ? 'bg-amber-500/10 border-amber-500/30 text-amber-300' :
          'bg-crimson/10 border-crimson/30 text-rose-300'
        }`}>
          <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" /> <span>{purchaseFeedback.text}</span>
        </div>
      )}

      {isStaff && (
        <div className="bg-sp-dark/60 border border-sp-bronze/40 rounded-xl p-5 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="font-serif text-sm text-sp-gold uppercase tracking-wide flex items-center gap-2">
              <Package className="w-4 h-4" /> Staff: Manage Items
            </h2>
            <button onClick={openNewItemForm} className="flex items-center gap-1 px-3 py-1.5 bg-sp-gold hover:bg-white text-black text-[10px] font-bold uppercase rounded cursor-pointer transition-all">
              <Plus className="w-3.5 h-3.5" /> New Item
            </button>
          </div>

          {showItemForm && (
            <form onSubmit={handleItemSubmit} className="bg-neutral-950/40 border border-sp-bronze/20 rounded-lg p-4 space-y-3">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Slug</label>
                  <input value={itemForm.slug} onChange={e => setItemForm(f => ({ ...f, slug: e.target.value }))} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2" placeholder="diamond-pack" />
                </div>
                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Name</label>
                  <input value={itemForm.name} onChange={e => setItemForm(f => ({ ...f, name: e.target.value }))} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2" placeholder="Diamond Pack" />
                </div>
              </div>
              <div>
                <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Description</label>
                <textarea value={itemForm.description} onChange={e => setItemForm(f => ({ ...f, description: e.target.value }))} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2" rows={2} />
              </div>
              <div>
                <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Currency</label>
                <select value={itemForm.currency} onChange={e => setItemForm(f => ({ ...f, currency: e.target.value }))} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2">
                  <option value="balance">In-game balance (spends real Vault money via RCON, delivered in-game)</option>
                  <option value="coins">Elite coins (spends EliteMobs' coins via RCON - buyer must be online, delivered in-game)</option>
                  <option value="website">Website Gears (no Minecraft server needed at all — website-only item)</option>
                </select>
                <p className="text-[9px] text-text-warm/40 mt-1">
                  {itemForm.currency === 'website'
                    ? "This item needs the MC server for nothing — no online check, no RCON, no in-game delivery. Good for purely cosmetic website effects."
                    : 'Spends real in-game balance and needs the give command below to actually deliver something.'}
                </p>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Price ({itemForm.currency === 'website' ? 'Gears' : 'balance'})</label>
                  <input type="number" min="0" step="0.01" value={itemForm.price} onChange={e => setItemForm(f => ({ ...f, price: e.target.value }))} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2" />
                </div>
                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Image URL (optional)</label>
                  <input value={itemForm.imageUrl} onChange={e => setItemForm(f => ({ ...f, imageUrl: e.target.value }))} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2" />
                </div>
              </div>
              {itemForm.currency !== 'website' && (
                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Give command (RCON, must include {'{player}'})</label>
                  <input value={itemForm.giveCommand} onChange={e => setItemForm(f => ({ ...f, giveCommand: e.target.value }))} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2 font-mono" placeholder="give {player} diamond 5" />
                  <p className="text-[9px] text-text-warm/40 mt-1">Confirmed working on this server: plain vanilla <code>give {'{player}'} &lt;item&gt; &lt;amount&gt;</code>. For kits/other commands, verify in-game first. Several commands can be chained with <code>;;</code> (e.g. <code>kit mypack {'{player}'};;relics random {'{player}'} epic</code>) — they run in order, online and on the queued join-delivery.</p>
                </div>
              )}
              <label className="flex items-center gap-2 text-xs text-text-warm/70 cursor-pointer">
                <input type="checkbox" checked={itemForm.active} onChange={e => setItemForm(f => ({ ...f, active: e.target.checked }))} /> Active (visible in shop)
              </label>
              {formError && <p className="text-xs text-rose-400">{formError}</p>}
              <div className="flex items-center gap-2">
                <button type="submit" disabled={savingItem} className="px-4 py-2 bg-sp-gold hover:bg-white disabled:opacity-50 text-black text-xs font-bold uppercase rounded cursor-pointer transition-all">
                  {savingItem ? 'Saving...' : 'Save Item'}
                </button>
                <button type="button" onClick={() => setShowItemForm(false)} className="px-4 py-2 border border-sp-bronze/40 text-text-warm/60 text-xs uppercase rounded cursor-pointer">Cancel</button>
              </div>
            </form>
          )}

          <div className="space-y-2">
            {items.map(item => (
              <div key={item.id} className="flex items-center justify-between bg-neutral-950/30 border border-sp-bronze/15 rounded-lg px-3 py-2 text-xs">
                <div className="min-w-0">
                  <span className="font-bold text-white">{item.name}</span>
                  <span className="text-text-warm/40 ml-2">{item.price} · {item.giveCommand}</span>
                  {!item.active && <span className="ml-2 text-[9px] uppercase text-rose-400 font-bold">Inactive</span>}
                </div>
                <div className="flex items-center gap-1 shrink-0">
                  <button onClick={() => openEditItemForm(item)} className="p-1.5 rounded hover:bg-sp-gold/10 text-sp-gold/70 hover:text-sp-gold cursor-pointer"><Edit className="w-3.5 h-3.5" /></button>
                  <button onClick={() => handleDeleteItem(item.id)} className="p-1.5 rounded hover:bg-crimson/10 text-rose-400/70 hover:text-rose-400 cursor-pointer"><Trash2 className="w-3.5 h-3.5" /></button>
                </div>
              </div>
            ))}
          </div>

          {user.role === 'admin' && (
            <div className="border-t border-sp-bronze/20 pt-4 space-y-2">
              <h3 className="font-serif text-xs text-sp-gold uppercase tracking-wide flex items-center gap-2"><Settings className="w-3.5 h-3.5" /> Take-Money Command</h3>
              <p className="text-[9px] text-text-warm/40">
                This isn&apos;t confirmed against your server&apos;s real economy plugin — adjust it here (no redeploy needed) if purchases report the balance not changing. Must include <code>{'{player}'}</code> and <code>{'{amount}'}</code>.
              </p>
              <form onSubmit={handleSaveSettings} className="flex items-center gap-2">
                <input value={takeCommand} onChange={e => setTakeCommand(e.target.value)} className="flex-1 bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2 font-mono" placeholder="Vault: cmi money take {player} {amount}" />
                <button type="submit" disabled={savingSettings} className="px-3 py-2 bg-sp-gold hover:bg-white disabled:opacity-50 text-black text-[10px] font-bold uppercase rounded cursor-pointer">
                  {savingSettings ? '...' : 'Save'}
                </button>
              </form>
              <div className="flex items-center gap-2">
                <input value={coinsTakeCommand} onChange={e => setCoinsTakeCommand(e.target.value)} className="flex-1 bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2 font-mono" placeholder="Coins: elitecoins take {player} {amount}" />
                <button onClick={() => fetch('/api/shop/settings', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ coinsTakeCommand }) }).then(async r => { const d = await r.json(); setSettingsFeedback(r.ok && d.success ? 'Saved!' : (d.error || 'Could not save.')); setTimeout(() => setSettingsFeedback(''), 3000); })} disabled={savingSettings} className="px-3 py-2 bg-sp-gold/80 hover:bg-white disabled:opacity-50 text-black text-[10px] font-bold uppercase rounded cursor-pointer">
                  Save coins
                </button>
              </div>
              {settingsFeedback && <p className="text-[10px] text-sp-gold">{settingsFeedback}</p>}
            </div>
          )}

          <div className="border-t border-sp-bronze/20 pt-4">
            <button onClick={() => setShowStaffPurchases(s => !s)} className="text-[10px] uppercase font-bold text-sp-gold/70 hover:text-sp-gold cursor-pointer">
              {showStaffPurchases ? 'Hide' : 'Show'} All Purchases ({allPurchases.length})
            </button>
            {showStaffPurchases && (
              <div className="mt-3 space-y-1.5 max-h-64 overflow-y-auto pr-1">
                {allPurchases.map(p => (
                  <div key={p.id} className="flex items-center justify-between bg-neutral-950/30 border border-sp-bronze/10 rounded-lg px-3 py-2 text-[10px]">
                    <span className="text-white font-bold">{p.username}</span>
                    <span className="text-text-warm/50 flex-1 px-2 truncate">{p.itemName} · {p.pricePaid}</span>
                    <StatusBadge status={p.status} />
                    {p.status === 'queued' && (
                      <button onClick={() => handleRetry(p.id)} disabled={retryingId === p.id} className="ml-2 px-2 py-1 border border-sp-gold/40 text-sp-gold text-[9px] uppercase rounded hover:bg-sp-gold/10 cursor-pointer disabled:opacity-50">
                        {retryingId === p.id ? '...' : 'Retry'}
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {loading ? (
        <div className="py-20 text-center text-text-warm/40 italic animate-pulse">Loading the trading post...</div>
      ) : items.length === 0 ? (
        <div className="bg-neutral-900/10 border border-sp-bronze/20 rounded-xl p-10 text-center text-text-warm/30 italic">
          The shop is empty right now — check back later.
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
          {items.map(item => {
            const isWebsiteCurrency = item.currency === 'website';
            const affordBalance = isWebsiteCurrency ? currency : balance;
            const cantAfford = user && typeof affordBalance === 'number' && affordBalance < item.price;
            return (
            <div key={item.id} className="bg-sp-dark/80 border border-sp-bronze/30 rounded-xl p-4 flex flex-col gap-3 hover:border-sp-gold/40 transition-all">
              {item.imageUrl && (
                <img src={item.imageUrl} alt={item.name} className="w-full h-28 object-cover rounded-lg border border-sp-bronze/20" />
              )}
              <div>
                <h3 className="text-sm font-bold text-white flex items-center gap-1.5">
                  {item.name}
                  {isWebsiteCurrency && (
                    <span className="text-[8px] px-1.5 py-0.5 bg-sp-copper/15 border border-sp-copper/40 text-sp-copper rounded font-bold uppercase tracking-wide">Website Only</span>
                  )}
                </h3>
                {item.description && <p className="text-[11px] text-text-warm/50 mt-1">{item.description}</p>}
              </div>
              <div className="flex items-center justify-between mt-auto pt-2 border-t border-sp-bronze/15">
                <span className="flex items-center gap-1 font-serif text-sp-gold font-bold text-sm">
                  {isWebsiteCurrency ? <Cog className="w-4 h-4 text-sp-copper" /> : <Coins className="w-4 h-4" />} {item.price}
                </span>
                <button
                  onClick={() => handleBuy(item)}
                  disabled={!user || !shopEnabled || buyingId === item.id || cantAfford}
                  title={cantAfford ? `Not enough ${isWebsiteCurrency ? 'Gears' : 'balance'}` : undefined}
                  className="px-3 py-1.5 bg-sp-gold hover:bg-white disabled:opacity-50 text-black text-[10px] font-bold uppercase rounded cursor-pointer transition-all"
                >
                  {buyingId === item.id ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : !shopEnabled ? 'Closed' : cantAfford ? 'Too Expensive' : 'Buy'}
                </button>
              </div>
            </div>
            );
          })}
        </div>
      )}

      {user && purchases.length > 0 && (
        <div className="space-y-3">
          <h2 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/25 pb-2">Your Purchases</h2>
          <div className="space-y-2">
            {purchases.map(p => (
              <div key={p.id} className="flex items-center justify-between bg-neutral-900/30 border border-sp-bronze/20 rounded-lg px-3 py-2.5 text-xs">
                <div>
                  <span className="font-bold text-white">{p.itemName}</span>
                  <span className="text-text-warm/40 ml-2">{p.pricePaid}</span>
                </div>
                <div className="flex items-center gap-2">
                  <StatusBadge status={p.status} />
                  {p.status === 'queued' && (
                    <button onClick={() => handleRetry(p.id)} disabled={retryingId === p.id} className="px-2 py-1 border border-sp-gold/40 text-sp-gold text-[9px] uppercase rounded hover:bg-sp-gold/10 cursor-pointer disabled:opacity-50">
                      {retryingId === p.id ? '...' : 'Retry Delivery'}
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
