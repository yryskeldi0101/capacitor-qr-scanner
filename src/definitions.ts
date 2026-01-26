export interface QrCodeScannerPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
