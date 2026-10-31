import { headers } from 'next/headers';
import { CartProvider } from '../../components/CartProvider';
import ShopPageClient from './ShopPageClient';

export default async function ShopPage() {
  const h = await headers();
  const host = (h.get('host') || '').toLowerCase().replace(/:\d+$/, '');
  const isShopSubdomain = host === 'shop.iekcorbar.com';

  if (!isShopSubdomain) {
    return (
      <div className="max-w-xl mx-auto py-20 px-6 text-center space-y-4">
        <h1 className="font-serif text-2xl font-bold text-sp-gold uppercase tracking-wide">Shop moved</h1>
        <p className="text-sm text-text-warm/70">
          The shop has moved to its own home. You can find it now at{' '}
          <a href="https://shop.iekcorbar.com" className="text-sp-gold hover:text-white underline font-bold">
            shop.iekcorbar.com
          </a>
          .
        </p>
        <a href="https://shop.iekcorbar.com" className="inline-block mt-2 px-5 py-2 bg-sp-gold hover:bg-white text-black text-xs font-bold uppercase rounded cursor-pointer transition-all">
          Go to the shop
        </a>
      </div>
    );
  }

  return (
    <CartProvider>
      <ShopPageClient />
    </CartProvider>
  );
}
