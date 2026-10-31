'use client';

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useUser } from '../providers';
import { useCart } from '../../components/CartProvider';
import {
  ShoppingBag, Coins, Loader2, RefreshCw, Search, Filter, X, CheckCircle2,
  AlertTriangle, Clock, Settings, Package, Power, Cog, Sparkles, Trash2,
  Plus, Minus, ShoppingCart, ChevronDown, Tag
} from 'lucide-react';

const emptyItemForm = { id: null, slug: '', name: '', description: '', price: '', giveCommand: '', imageUrl: '', active: true, currency: 'balance', category: '' };

function StatusBadge({ status }) {
  const map = {
    delivered: { label: 'Delivered', cls: 'bg-emerald-600/20 border-emerald-500/40 text-emerald-400', Icon: CheckCircle2 },
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

function moneyName(c) { return c === 'website' ? 'Gears' : c === 'coins' ? 'Elite coins' : 'balance'; }
function moneyIcon(c) {
  if (c === 'website') return <Cog className="w-4 h-4 text-sp-copper" />;
  if (c === 'coins') return <Sparkles className="w-4 h-4 text-sp-copper" />;
  return <Coins className="w-4 h-4" />;
}

export default function ShopPage() {
  const { user } = useUser();
  const cart = useCart();
  const isStaff = user && (user.role === 'admin' || user.role === 'moderator');

  const [items, setItems] = useState([]);
  const [shopEnabled, setShopEnabled] = useState(true);
  const [loading, setLoading] = useState(true);
  const [balance, setBalance] = useState(null);
  const [coins, setCoins] = useState(null);
  const [balanceLoading, setBalanceLoading] = useState(false);
  const [currency, setCurrency] = useState(null);

  const [purchases, setPurchases] = useState([]);
  const [buyingId, setBuyingId] = useState(null);
  const [purchaseFeedback, setPurchaseFeedback] = useState(null);

  const [search, setSearch] = useState('');
  const [activeCategory, setActiveCategory] = useState('all');
  const [activeCurrency, setActiveCurrency] = useState('all');

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
  const [showCart, setShowCart] = useState(false);
  const [checkoutLoading, setCheckoutLoading] = useState(false);

  const fetchItems = useCallback(async () => {
    try {
      const res = await fetch('/api/shop/items');
      const data = await res.json();
      setItems(data.items || []);
      if (typeof data.enabled === 'boolean') setShopEnabled(data.enabled);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  }, []);

  const fetchBalance = useCallback(async () => {
    if (!user?.mcUsername) return;
    setBalanceLoading(true);
    try {
      const res = await fetch(`/api/player-stats?username=${encodeURIComponent(user.mcUsername)}`);
      const data = await res.json();
      if (res.ok && typeof data.balance === 'number') setBalance(data.balance);
      if (res.ok && typeof data.eliteCoins === 'number') setCoins(data.eliteCoins);
    } catch (err) { console.error(err); }
    finally { setBalanceLoading(false); }
  }, [user]);

  const fetchCurrency = useCallback(async () => {
    if (!user) return;
    try {
      const res = await fetch('/api/currency');
      const data = await res.json();
      if (res.ok && typeof data.currency === 'number') setCurrency(data.currency);
    } catch (err) { console.error(err); }
  }, [user]);

  const fetchPurchases = useCallback(async () => {
    if (!user) return;
    try {
      const res = await fetch('/api/shop/purchases');
      const data = await res.json();
      if (res.ok) setPurchases(data.purchases || []);
    } catch (err) { console.error(err); }
  }, [user]);

  const fetchAllPurchases = useCallback(async () => {
    if (!isStaff) return;
    try {
      const res = await fetch('/api/shop/purchases?all=true');
      const data = await res.json();
      if (res.ok) setAllPurchases(data.purchases || []);
    } catch (err) { console.error(err); }
  }, [isStaff]);

  const fetchSettings = useCallback(async () => {
    if (!isStaff) return;
    try {
      const res = await fetch('/api/shop/settings');
      const data = await res.json();
      if (res.ok) { setTakeCommand(data.settings?.takeCommand || ''); setCoinsTakeCommand(data.settings?.coinsTakeCommand || ''); }
    } catch (err) { console.error(err); }
  }, [isStaff]);

  useEffect(() => { fetchItems(); }, [fetchItems]);
  useEffect(() => { fetchBalance(); fetchCurrency(); fetchPurchases(); }, [fetchBalance, fetchCurrency, fetchPurchases]);
  useEffect(() => { fetchAllPurchases(); fetchSettings(); }, [fetchAllPurchases, fetchSettings]);

  async function handleBuy(item) {
    if (!user) return;
    if (!confirm(`Buy "${item.name}" for ${item.price} ${moneyName(item.currency)}?`)) return;
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
        setPurchaseFeedback({ type: data.status === 'delivered' ? 'success' : 'warn',
          text: data.status === 'delivered' ? `Purchased "${item.name}"!` : `Purchased "${item.name}" — delivered on next join.` });
        if (typeof data.newBalance === 'number') setBalance(data.newBalance);
        if (typeof data.newCoins === 'number') setCoins(data.newCoins);
        fetchCurrency();
        fetchPurchases();
      } else {
        setPurchaseFeedback({ type: 'error', text: data.error || 'Purchase failed.' });
      }
    } catch (err) {
      setPurchaseFeedback({ type: 'error', text: 'Network error. Please try again.' });
    } finally { setBuyingId(null); }
  }

  async function handleCheckout() {
    if (cart.items.length === 0) return;
    setCheckoutLoading(true);
    setPurchaseFeedback(null);
    let ok = 0, fail = 0;
    for (const { product, qty } of cart.items) {
      for (let i = 0; i < qty; i++) {
        try {
          const res = await fetch('/api/shop/purchase', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ itemId: product.id })
          });
          const data = await res.json();
          if (res.ok && data.success) ok++; else { fail++; setPurchaseFeedback({ type: 'error', text: data.error || 'Purchase failed.' }); }
          if (typeof data.newBalance === 'number') setBalance(data.newBalance);
          if (typeof data.newCoins === 'number') setCoins(data.newCoins);
        } catch { fail++; }
      }
    }
    fetchCurrency();
    fetchPurchases();
    setCheckoutLoading(false);
    if (fail === 0) {
      setPurchaseFeedback({ type: 'success', text: `All ${ok} item(s) purchased! Delivered on next join.` });
      cart.clear();
      setShowCart(false);
    } else {
      setPurchaseFeedback({ type: 'warn', text: `${ok} succeeded, ${fail} failed.` });
    }
  }

  async function handleRetry(purchaseId) {
    setRetryingId(purchaseId);
    try {
      const res = await fetch('/api/shop/purchases', { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ purchaseId }) });
      const data = await res.json();
      if (res.ok && data.success) { fetchPurchases(); fetchAllPurchases(); }
      else alert(data.error || 'Retry failed.');
    } catch { alert('Network error.'); }
    finally { setRetryingId(null); }
  }

  function openNewItemForm() {
    setItemForm(emptyItemForm); setFormError(''); setShowItemForm(true);
  }
  function openEditItemForm(item) {
    setItemForm({
      id: item.id, slug: item.slug, name: item.name, description: item.description || '',
      price: String(item.price), giveCommand: item.giveCommand || '', imageUrl: item.imageUrl || '',
      active: item.active, currency: item.currency === 'website' ? 'website' : 'balance', category: item.category || ''
    });
    setFormError(''); setShowItemForm(true);
  }
  async function handleItemSubmit(e) {
    e.preventDefault(); setFormError('');
    const isWebsiteCurrency = itemForm.currency === 'website';
    if (!itemForm.slug.trim() || !itemForm.name.trim() || itemForm.price === '' || (!isWebsiteCurrency && !itemForm.giveCommand.trim())) {
      setFormError(isWebsiteCurrency ? 'Slug, name and price are required.' : 'Slug, name, price and give command are required.');
      return;
    }
    setSavingItem(true);
    try {
      const res = await fetch('/api/shop/items', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: itemForm.id, slug: itemForm.slug.trim(), name: itemForm.name.trim(),
          description: itemForm.description.trim(), price: Number(itemForm.price),
          giveCommand: isWebsiteCurrency ? null : itemForm.giveCommand.trim(),
          imageUrl: itemForm.imageUrl.trim(), active: itemForm.active, currency: itemForm.currency,
          category: itemForm.category.trim()
        })
      });
      const data = await res.json();
      if (res.ok && data.success) { setItems(data.items); setShowItemForm(false); }
      else setFormError(data.error || 'Could not save item.');
    } catch { setFormError('Network error.'); }
    finally { setSavingItem(false); }
  }
  async function handleDeleteItem(id) {
    if (!confirm('Delete this shop item?')) return;
    try {
      const res = await fetch(`/api/shop/items?id=${encodeURIComponent(id)}`, { method: 'DELETE' });
      const data = await res.json();
      if (res.ok && data.success) setItems(data.items);
    } catch (err) { console.error(err); }
  }
  async function handleToggleShop() {
    try {
      const res = await fetch('/api/shop/settings', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled: !shopEnabled }) });
      const data = await res.json();
      if (res.ok && data.success) setShopEnabled(data.settings.enabled);
    } catch (err) { console.error(err); }
  }
  async function handleSaveSettings(e) {
    e.preventDefault(); setSavingSettings(true); setSettingsFeedback('');
    try {
      const res = await fetch('/api/shop/settings', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ takeCommand, coinsTakeCommand }) });
      const data = await res.json();
      setSettingsFeedback(res.ok && data.success ? 'Saved!' : (data.error || 'Could not save.'));
    } catch { setSettingsFeedback('Network error.'); }
    finally { setSavingSettings(false); setTimeout(() => setSettingsFeedback(''), 3000); }
  }

  const categories = useMemo(() => {
    const set = new Set(items.filter(i => i.active).map(i => i.category).filter(Boolean));
    return ['all', ...Array.from(set)];
  }, [items]);

  const filteredItems = useMemo(() => {
    return items.filter(i => {
      if (!i.active && !isStaff) return false;
      if (activeCategory !== 'all' && i.category !== activeCategory) return false;
      if (activeCurrency !== 'all' && i.currency !== activeCurrency) return false;
      if (search && !i.name.toLowerCase().includes(search.toLowerCase()) && !(i.description || '').toLowerCase().includes(search.toLowerCase())) return false;
      return true;
    });
  }, [items, activeCategory, activeCurrency, search, isStaff]);

  const walletFor = (c) => c === 'website' ? currency : c === 'coins' ? coins : balance;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-sp-bronze pb-5">
        <div>
          <h1 className="font-serif text-3xl font-bold text-sp-gold tracking-wide flex items-center gap-2 uppercase">
            <ShoppingBag className="w-8 h-8 text-sp-copper" /> Trading Post
          </h1>
          <p className="text-xs text-text-warm/60 mt-1">Cosmetics, kits and more — spend your earned in-game currencies. No pay-to-win, ever.</p>
        </div>
        <div className="flex items-center gap-2">
          {user?.role === 'admin' && (
            <button onClick={handleToggleShop} disabled={buyingId} className={`flex items-center gap-1.5 px-3 py-2 text-[10px] font-bold uppercase rounded-lg cursor-pointer transition-all border ${shopEnabled ? 'border-emerald-500/40 bg-emerald-600/10 text-emerald-400' : 'border-crimson/40 bg-crimson/10 text-rose-300'}`}>
              <Power className="w-3.5 h-3.5" /> {shopEnabled ? 'Open' : 'Closed'}
            </button>
          )}
          <button onClick={() => setShowCart(true)} className="relative flex items-center gap-1.5 px-3 py-2 bg-sp-gold hover:bg-white text-black text-[10px] font-bold uppercase rounded-lg cursor-pointer transition-all">
            <ShoppingCart className="w-4 h-4" /> Cart
            {cart.count > 0 && <span className="absolute -top-1 -right-1 w-4 h-4 bg-crimson text-white text-[9px] font-bold rounded-full flex items-center justify-center">{cart.count}</span>}
          </button>
        </div>
      </div>

      {!shopEnabled && (
        <div className="bg-crimson/10 border border-crimson/30 rounded-xl p-4 text-center text-rose-300 text-sm font-bold uppercase tracking-wide">
          The shop is currently closed. Browsing is still possible, but purchases are disabled.
        </div>
      )}

      {purchaseFeedback && (
        <div className={`rounded-lg p-3 text-xs flex items-start gap-2 border ${purchaseFeedback.type === 'success' ? 'bg-emerald-600/10 border-emerald-500/30 text-emerald-300' : purchaseFeedback.type === 'warn' ? 'bg-amber-500/10 border-amber-500/30 text-amber-300' : 'bg-crimson/10 border-crimson/30 text-rose-300'}`}>
          <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" /> <span>{purchaseFeedback.text}</span>
        </div>
      )}

      {/* Wallets */}
      {user && (
        <div className="flex flex-wrap gap-3">
          <div className="bg-neutral-900/40 border border-sp-bronze/35 px-4 py-2 rounded-xl flex items-center gap-2">
            <Coins className="w-5 h-5 text-sp-gold" />
            <div><span className="block text-[9px] uppercase font-bold text-text-warm/40">Balance</span><span className="font-serif text-lg font-black text-white">{balanceLoading ? <Loader2 className="w-4 h-4 animate-spin inline" /> : (balance ?? '—')}</span></div>
          </div>
          <div className="bg-neutral-900/40 border border-sp-bronze/35 px-4 py-2 rounded-xl flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-sp-copper" />
            <div><span className="block text-[9px] uppercase font-bold text-text-warm/40">Elite coins</span><span className="font-serif text-lg font-black text-white">{coins ?? '—'}</span></div>
          </div>
          <div className="bg-neutral-900/40 border border-sp-bronze/35 px-4 py-2 rounded-xl flex items-center gap-2">
            <Cog className="w-5 h-5 text-sp-copper" />
            <div><span className="block text-[9px] uppercase font-bold text-text-warm/40">Gears</span><span className="font-serif text-lg font-black text-white">{currency ?? '—'}</span></div>
          </div>
        </div>
      )}

      {/* Search + filters */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-warm/40" />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search products..." className="w-full bg-neutral-900 border border-sp-bronze text-white text-sm rounded-lg pl-9 pr-3 py-2.5" />
        </div>
        <div className="flex gap-2 flex-wrap">
          {categories.map(c => (
            <button key={c} onClick={() => setActiveCategory(c)} className={`px-3 py-2 text-[10px] font-bold uppercase rounded-lg border transition-all ${activeCategory === c ? 'bg-sp-gold text-black border-sp-gold' : 'border-sp-bronze/40 text-text-warm/60 hover:border-sp-gold/40'}`}>
              {c === 'all' ? 'All' : c}
            </button>
          ))}
        </div>
        <select value={activeCurrency} onChange={e => setActiveCurrency(e.target.value)} className="bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2">
          <option value="all">All currencies</option>
          <option value="balance">Balance</option>
          <option value="coins">Elite coins</option>
          <option value="website">Gears</option>
        </select>
      </div>

      {/* Product grid */}
      {loading ? (
        <div className="py-20 text-center text-text-warm/40 italic animate-pulse">Loading the trading post...</div>
      ) : filteredItems.length === 0 ? (
        <div className="bg-neutral-900/10 border border-sp-bronze/20 rounded-xl p-10 text-center text-text-warm/30 italic">No products found.</div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {filteredItems.map(item => {
            const wallet = walletFor(item.currency);
            const cantAfford = user && typeof wallet === 'number' && wallet < item.price;
            return (
              <div key={item.id} className="bg-sp-dark/80 border border-sp-bronze/30 rounded-xl overflow-hidden flex flex-col hover:border-sp-gold/50 transition-all group">
                {item.imageUrl ? (
                  <div className="h-36 bg-neutral-900/60 overflow-hidden">
                    <img src={item.imageUrl} alt={item.name} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" />
                  </div>
                ) : (
                  <div className="h-36 bg-gradient-to-br from-sp-dark to-neutral-900 flex items-center justify-center">
                    <Package className="w-10 h-10 text-sp-bronze/40" />
                  </div>
                )}
                <div className="p-3 flex flex-col flex-1 gap-2">
                  <div className="flex items-start justify-between gap-2">
                    <h3 className="text-sm font-bold text-white leading-tight">{item.name}</h3>
                    {!item.active && <span className="text-[8px] uppercase text-rose-400 font-bold shrink-0">Inactive</span>}
                  </div>
                  {item.category && <span className="text-[9px] uppercase tracking-wider text-sp-copper/70 font-bold">{item.category}</span>}
                  {item.description && <p className="text-[11px] text-text-warm/50 line-clamp-2">{item.description}</p>}
                  <div className="flex items-center justify-between mt-auto pt-2 border-t border-sp-bronze/15">
                    <span className="flex items-center gap-1 font-serif text-sp-gold font-bold text-sm">
                      {moneyIcon(item.currency)} {item.price} <span className="text-[9px] text-text-warm/40 font-normal">{moneyName(item.currency)}</span>
                    </span>
                    <div className="flex gap-1">
                      <button onClick={() => cart.add(item)} disabled={!user || !shopEnabled} className="px-2 py-1.5 bg-sp-bronze/20 hover:bg-sp-gold/20 text-sp-gold text-[9px] font-bold uppercase rounded cursor-pointer transition-all disabled:opacity-40" title="Add to cart">
                        <Plus className="w-3.5 h-3.5" />
                      </button>
                      <button onClick={() => handleBuy(item)} disabled={!user || !shopEnabled || buyingId === item.id || cantAfford} className="px-3 py-1.5 bg-sp-gold hover:bg-white disabled:opacity-50 text-black text-[10px] font-bold uppercase rounded cursor-pointer transition-all">
                        {buyingId === item.id ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : cantAfford ? 'Too expensive' : 'Buy'}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Cart drawer */}
      {showCart && (
        <div className="fixed inset-0 z-50 flex justify-end">
          <div className="absolute inset-0 bg-black/60" onClick={() => setShowCart(false)} />
          <div className="relative w-full max-w-md bg-sp-dark border-l border-sp-bronze p-5 space-y-4 overflow-y-auto">
            <div className="flex items-center justify-between border-b border-sp-bronze/30 pb-3">
              <h2 className="font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2"><ShoppingCart className="w-5 h-5" /> Cart ({cart.count})</h2>
              <button onClick={() => setShowCart(false)} className="p-1 text-text-warm/60 hover:text-white"><X className="w-5 h-5" /></button>
            </div>
            {cart.items.length === 0 ? (
              <p className="text-text-warm/40 italic text-sm text-center py-10">Your cart is empty.</p>
            ) : (
              <>
                <div className="space-y-2">
                  {cart.items.map(({ product, qty }) => (
                    <div key={product.id} className="flex items-center gap-3 bg-neutral-900/40 border border-sp-bronze/20 rounded-lg p-2">
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-bold text-white truncate">{product.name}</p>
                        <p className="text-xs text-sp-gold">{product.price} {moneyName(product.currency)} each</p>
                      </div>
                      <div className="flex items-center gap-1">
                        <button onClick={() => cart.updateQty(product.id, qty - 1)} className="p-1 text-text-warm/60 hover:text-white"><Minus className="w-3.5 h-3.5" /></button>
                        <span className="text-sm font-bold text-white w-6 text-center">{qty}</span>
                        <button onClick={() => cart.updateQty(product.id, qty + 1)} className="p-1 text-text-warm/60 hover:text-white"><Plus className="w-3.5 h-3.5" /></button>
                      </div>
                      <button onClick={() => cart.remove(product.id)} className="p-1 text-rose-400/70 hover:text-rose-400"><Trash2 className="w-3.5 h-3.5" /></button>
                    </div>
                  ))}
                </div>
                <div className="border-t border-sp-bronze/30 pt-3 space-y-2">
                  <div className="flex justify-between text-sm"><span className="text-text-warm/60">Total</span><span className="font-bold text-sp-gold">{cart.total.toFixed(2)}</span></div>
                  <button onClick={handleCheckout} disabled={checkoutLoading || !user || !shopEnabled} className="w-full py-2.5 bg-sp-gold hover:bg-white disabled:opacity-50 text-black text-xs font-bold uppercase rounded-lg cursor-pointer transition-all">
                    {checkoutLoading ? <Loader2 className="w-4 h-4 animate-spin inline" /> : 'Checkout'}
                  </button>
                  <button onClick={cart.clear} className="w-full py-2 text-[10px] uppercase text-text-warm/40 hover:text-rose-400">Clear cart</button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* Admin section */}
      {isStaff && (
        <div className="bg-sp-dark/60 border border-sp-bronze/40 rounded-xl p-5 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="font-serif text-sm text-sp-gold uppercase tracking-wide flex items-center gap-2"><Package className="w-4 h-4" /> Staff: Manage Products</h2>
            <button onClick={openNewItemForm} className="flex items-center gap-1 px-3 py-1.5 bg-sp-gold hover:bg-white text-black text-[10px] font-bold uppercase rounded cursor-pointer transition-all">
              <Plus className="w-3.5 h-3.5" /> New Product
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
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Currency</label>
                  <select value={itemForm.currency} onChange={e => setItemForm(f => ({ ...f, currency: e.target.value }))} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2">
                    <option value="balance">Balance (Vault)</option>
                    <option value="coins">Elite coins</option>
                    <option value="website">Gears (website-only)</option>
                  </select>
                </div>
                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Price</label>
                  <input type="number" min="0" step="0.01" value={itemForm.price} onChange={e => setItemForm(f => ({ ...f, price: e.target.value }))} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2" />
                </div>
                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Category</label>
                  <input value={itemForm.category} onChange={e => setItemForm(f => ({ ...f, category: e.target.value }))} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2" placeholder="kits" />
                </div>
              </div>
              <div>
                <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Image URL</label>
                <input value={itemForm.imageUrl} onChange={e => setItemForm(f => ({ ...f, imageUrl: e.target.value }))} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2" placeholder="https://..." />
              </div>
              {itemForm.currency !== 'website' && (
                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Give command (RCON, must include {'{player}'})</label>
                  <input value={itemForm.giveCommand} onChange={e => setItemForm(f => ({ ...f, giveCommand: e.target.value }))} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2 font-mono" placeholder="give {player} diamond 5" />
                </div>
              )}
              <label className="flex items-center gap-2 text-xs text-text-warm/70 cursor-pointer">
                <input type="checkbox" checked={itemForm.active} onChange={e => setItemForm(f => ({ ...f, active: e.target.checked }))} /> Active (visible in shop)
              </label>
              {formError && <p className="text-xs text-rose-400">{formError}</p>}
              <div className="flex items-center gap-2">
                <button type="submit" disabled={savingItem} className="px-4 py-2 bg-sp-gold hover:bg-white disabled:opacity-50 text-black text-xs font-bold uppercase rounded cursor-pointer transition-all">
                  {savingItem ? 'Saving...' : 'Save Product'}
                </button>
                <button type="button" onClick={() => setShowItemForm(false)} className="px-4 py-2 border border-sp-bronze/40 text-text-warm/60 text-xs uppercase rounded cursor-pointer">Cancel</button>
              </div>
            </form>
          )}

          <div className="space-y-2 max-h-64 overflow-y-auto pr-1">
            {items.map(item => (
              <div key={item.id} className="flex items-center justify-between bg-neutral-950/30 border border-sp-bronze/15 rounded-lg px-3 py-2 text-xs">
                <div className="min-w-0 flex items-center gap-2">
                  <span className="font-bold text-white">{item.name}</span>
                  <span className="text-text-warm/40">{item.price} {moneyName(item.currency)}</span>
                  {item.category && <span className="text-[9px] text-sp-copper/70">[{item.category}]</span>}
                  {!item.active && <span className="text-[9px] uppercase text-rose-400 font-bold">Inactive</span>}
                </div>
                <div className="flex items-center gap-1 shrink-0">
                  <button onClick={() => openEditItemForm(item)} className="p-1.5 rounded hover:bg-sp-gold/10 text-sp-gold/70 hover:text-sp-gold cursor-pointer"><Settings className="w-3.5 h-3.5" /></button>
                  <button onClick={() => handleDeleteItem(item.id)} className="p-1.5 rounded hover:bg-crimson/10 text-rose-400/70 hover:text-rose-400 cursor-pointer"><Trash2 className="w-3.5 h-3.5" /></button>
                </div>
              </div>
            ))}
          </div>

          {user.role === 'admin' && (
            <div className="border-t border-sp-bronze/20 pt-4 space-y-2">
              <h3 className="font-serif text-xs text-sp-gold uppercase tracking-wide flex items-center gap-2"><Settings className="w-3.5 h-3.5" /> Money-Take Commands</h3>
              <form onSubmit={handleSaveSettings} className="space-y-2">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  <input value={takeCommand} onChange={e => setTakeCommand(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2 font-mono" placeholder="cmi money take {player} {amount}" />
                  <input value={coinsTakeCommand} onChange={e => setCoinsTakeCommand(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2 font-mono" placeholder="em money remove {player} {amount}" />
                </div>
                <div className="flex items-center gap-2">
                  <button type="submit" disabled={savingSettings} className="px-3 py-2 bg-sp-gold hover:bg-white disabled:opacity-50 text-black text-[10px] font-bold uppercase rounded cursor-pointer">{savingSettings ? '...' : 'Save'}</button>
                  {settingsFeedback && <p className="text-[10px] text-sp-gold">{settingsFeedback}</p>}
                </div>
              </form>
            </div>
          )}

          <div className="border-t border-sp-bronze/20 pt-4">
            <button onClick={() => setShowStaffPurchases(s => !s)} className="text-[10px] uppercase font-bold text-sp-gold/70 hover:text-sp-gold cursor-pointer">
              {showStaffPurchases ? 'Hide' : 'Show'} All Orders ({allPurchases.length})
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

      {/* My orders */}
      {user && purchases.length > 0 && (
        <div className="space-y-3">
          <h2 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/25 pb-2">My Orders</h2>
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
