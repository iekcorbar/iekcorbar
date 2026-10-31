import { headers } from 'next/headers';
import { CartProvider } from '../components/CartProvider';
import HomePageClient from './HomePageClient';
import ShopPageClient from './shop/ShopPageClient';

export default async function Page() {
  const h = await headers();
  const host = (h.get('host') || '').toLowerCase().replace(/:\d+$/, '');
  if (host === 'shop.iekcorbar.com') {
    return (
      <CartProvider>
        <ShopPageClient />
      </CartProvider>
    );
  }
  return <HomePageClient />;
}
