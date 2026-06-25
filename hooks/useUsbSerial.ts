import { useState, useEffect, useRef, useCallback } from 'react';
import { Platform } from 'react-native';

export interface UsbSerialState {
  isConnected: boolean;
  deviceName: string | null;
  sendSignal: (fingerCount: number) => Promise<void>;
  lastSent: number;
}

// USB serial is only available in a native build (APK/IPA).
// This hook provides a stub in Expo Go that logs signals to the console,
// and exposes the full interface so the real implementation can be swapped in
// when building a standalone APK with react-native-usb-serial installed.
//
// To enable real USB in your APK build:
//   1. Run: pnpm --filter @workspace/project add react-native-usb-serial
//   2. Replace the stub below with actual UsbSerial calls.
//   3. Build with: eas build --platform android --profile preview

export function useUsbSerial(): UsbSerialState {
  const [isConnected] = useState(false);
  const [deviceName] = useState<string | null>(null);
  const [lastSent, setLastSent] = useState<number>(-1);
  const lastCountRef = useRef<number>(-1);

  const sendSignal = useCallback(async (fingerCount: number) => {
    if (fingerCount === lastCountRef.current) return;
    if (fingerCount < 0 || fingerCount > 5) return;

    lastCountRef.current = fingerCount;
    setLastSent(fingerCount);

    // In a native build, replace this with:
    // await UsbSerial.write(`${fingerCount}\n`);
    if (__DEV__) {
      console.log(`[USB stub] Signal: ${fingerCount}`);
    }
  }, []);

  return { isConnected, deviceName, sendSignal, lastSent };
}
