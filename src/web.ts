import { WebPlugin } from '@capacitor/core';

import type { QrCodeScannerPlugin } from './definitions';

export class QrCodeScannerWeb extends WebPlugin implements QrCodeScannerPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
