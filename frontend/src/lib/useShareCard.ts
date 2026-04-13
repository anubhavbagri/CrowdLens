import { useState, useCallback } from 'react';
import { toPng, toBlob } from 'html-to-image';

/**
 * Shared share/download logic for the verdict card.
 * Used by both VerdictCard inline buttons and the SharePrompt toast.
 */
export function useShareCard(query: string) {
  const [sharing, setSharing] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [copied, setCopied] = useState(false);

  const slug = query.replace(/\s+/g, '-').toLowerCase();

  const getCardEl = useCallback(() => {
    const el = document.getElementById('share-card-visual');
    if (!el) throw new Error('Card element not found');
    return el;
  }, []);

  const handleShare = useCallback(async () => {
    setSharing(true);
    try {
      const blob = await toBlob(getCardEl(), { pixelRatio: 2 });
      if (!blob) return;
      const file = new File([blob], `crowdlens-${slug}.png`, { type: 'image/png' });
      if (navigator.canShare?.({ files: [file] })) {
        await navigator.share({ files: [file], title: `CrowdLens: ${query}` });
      } else {
        await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })])
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      }
    } catch (e) {
      console.error('Share failed', e);
    } finally {
      setSharing(false);
    }
  }, [getCardEl, slug, query]);

  const handleDownload = useCallback(async () => {
    setDownloading(true);
    try {
      const dataUrl = await toPng(getCardEl(), { pixelRatio: 2 });
      const link = document.createElement('a');
      link.download = `crowdlens-${slug}.png`;
      link.href = dataUrl;
      link.click();
    } catch (e) {
      console.error('Download failed', e);
    } finally {
      setDownloading(false);
    }
  }, [getCardEl, slug]);

  return { handleShare, handleDownload, sharing, downloading, copied };
}
