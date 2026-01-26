import { WebPlugin } from '@capacitor/core';

import type {
  QrCodeScannerPlugin,
  PermissionStatus,
  ReadBarcodesFromImageOptions,
  ReadBarcodesFromImageResult,
  ScanOptions,
  ScanResult,
  IsSupportedResult,
  IsTorchAvailableResult,
  IsTorchEnabledResult,
  SetZoomRatioOptions,
  GetZoomRatioResult,
  GetMinZoomRatioResult,
  GetMaxZoomRatioResult,
  IsGoogleBarcodeScannerModuleAvailableResult,
  StartScanOptions,
} from './definitions';

export class QrCodeScannerWeb extends WebPlugin implements QrCodeScannerPlugin {
  async startScan(_options?: StartScanOptions): Promise<void> {
    throw this.unavailable('Camera preview not available on web');
  }

  async stopScan(): Promise<void> {
    // no-op
  }

  async pauseScan(): Promise<void> {
    // no-op
  }

  async resumeScan(): Promise<void> {
    // no-op
  }

  async readBarcodesFromImage(_options: ReadBarcodesFromImageOptions): Promise<ReadBarcodesFromImageResult> {
    throw this.unavailable('readBarcodesFromImage not supported on web');
  }

  async scan(_options?: ScanOptions): Promise<ScanResult> {
    throw this.unavailable('scan not supported on web');
  }

  async isSupported(): Promise<IsSupportedResult> {
    return { supported: false };
  }

  async enableTorch(): Promise<void> {
    // no-op
  }

  async disableTorch(): Promise<void> {
    // no-op
  }

  async toggleTorch(): Promise<void> {
    // no-op
  }

  async isTorchEnabled(): Promise<IsTorchEnabledResult> {
    return { enabled: false };
  }

  async isTorchAvailable(): Promise<IsTorchAvailableResult> {
    return { available: false };
  }

  async setZoomRatio(_options: SetZoomRatioOptions): Promise<void> {
    // no-op
  }

  async getZoomRatio(): Promise<GetZoomRatioResult> {
    return { zoomRatio: 1 };
  }

  async getMinZoomRatio(): Promise<GetMinZoomRatioResult> {
    return { zoomRatio: 1 };
  }

  async getMaxZoomRatio(): Promise<GetMaxZoomRatioResult> {
    return { zoomRatio: 1 };
  }

  async openSettings(): Promise<void> {
    // no-op
  }

  async isGoogleBarcodeScannerModuleAvailable(): Promise<IsGoogleBarcodeScannerModuleAvailableResult> {
    return { available: false };
  }

  async installGoogleBarcodeScannerModule(): Promise<void> {
    // no-op
  }

  async checkPermissions(): Promise<PermissionStatus> {
    // web fallback: treat as denied
    return { camera: 'denied' };
  }

  async requestPermissions(): Promise<PermissionStatus> {
    // web fallback: treat as denied
    return { camera: 'denied' };
  }
}
