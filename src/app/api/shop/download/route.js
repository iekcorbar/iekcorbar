import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import fs from 'fs';
import path from 'path';

// v2 (plugin sales): serves a purchased download file securely.
// For now this is a stub that validates the purchase and returns a placeholder
// response. Full file streaming + token-gated access will be implemented when
// PayPal integration lands.
export async function GET(request) {
  const sessionUser = await getSessionUser(request);
  if (!sessionUser) return NextResponse.json({ error: 'Not authenticated' }, { status: 401 });

  const { searchParams } = new URL(request.url);
  const purchaseId = searchParams.get('purchaseId');
  if (!purchaseId) return NextResponse.json({ error: 'Missing purchaseId' }, { status: 400 });

  const purchases = await db.getShopPurchasesByUsername(sessionUser.mcUsername || sessionUser.email);
  const purchase = (purchases || []).find(p => p.id === purchaseId);
  if (!purchase) return NextResponse.json({ error: 'Purchase not found' }, { status: 404 });

  // Find the product to get filePath
  const items = await db.getShopItems();
  const item = items.find(i => i.id === purchase.itemId);
  if (!item || item.productType !== 'download') {
    return NextResponse.json({ error: 'This purchase is not a downloadable product' }, { status: 400 });
  }

  // v2 TODO: stream the file from private-uploads/plugins/<filePath>
  // with a one-time/expiring token check. For now, return metadata.
  return NextResponse.json({
    success: true,
    placeholder: true,
    message: 'Download delivery will be available once PayPal integration is complete (v2).',
    item: { name: item.name, version: item.version, filePath: item.filePath }
  });
}
