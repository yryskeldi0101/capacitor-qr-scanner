import { registerPlugin } from '@capacitor/core';

import type { QrCodeScannerPlugin } from './definitions';

const QrCodeScanner = registerPlugin<QrCodeScannerPlugin>('QrCodeScanner', {
  web: () => import('./web').then((m) => new m.QrCodeScannerWeb()),
});

export * from './definitions';
export { QrCodeScanner };
