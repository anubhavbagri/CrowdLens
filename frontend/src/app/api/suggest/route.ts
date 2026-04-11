import { NextRequest, NextResponse } from 'next/server';

/**
 * Proxies Amazon's unofficial autocomplete endpoint to avoid CORS.
 * Uses Amazon US (completion.amazon.com) — amazon.in returns 502.
 *
 * GET /api/suggest?q=iphone
 * → string[] of product keyword suggestion strings
 */
export async function GET(req: NextRequest) {
  const q = req.nextUrl.searchParams.get('q')?.trim();

  if (!q || q.length < 2) {
    return NextResponse.json([]);
  }

  const url = new URL('https://completion.amazon.com/api/2017/suggestions');
  url.searchParams.set('mid', 'ATVPDKIKX0DER'); // Amazon US marketplace
  url.searchParams.set('alias', 'aps');           // all-products search
  url.searchParams.set('prefix', q);
  url.searchParams.set('limit', '10');
  url.searchParams.set('plain-mid', '1');
  url.searchParams.set('client-info', 'amazon-search-ui');

  try {
    const res = await fetch(url.toString(), {
      headers: {
        'User-Agent':
          'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'application/json, text/javascript, */*; q=0.01',
        'Referer': 'https://www.amazon.com/',
      },
      signal: AbortSignal.timeout(1500),
    });

    if (!res.ok) {
      return NextResponse.json([]);
    }

    const data = await res.json();

    // Keep only keyword suggestions (filter out sponsored/product card entries)
    const suggestions: string[] = (data?.suggestions ?? [])
      .filter((s: { type?: string; value?: string }) => s.type === 'KEYWORD' && s.value)
      .map((s: { value: string }) => s.value)
      .slice(0, 8);

    return NextResponse.json(suggestions);
  } catch {
    return NextResponse.json([]);
  }
}
