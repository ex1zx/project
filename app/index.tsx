import React, { useRef, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ActivityIndicator,
  Platform,
} from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import { Feather } from '@expo/vector-icons';

import HandOverlay from '@/components/HandOverlay';
import PermissionScreen from '@/components/PermissionScreen';
import { useHandDetection } from '@/hooks/useHandDetection';
import { useUsbSerial } from '@/hooks/useUsbSerial';

const FINGER_LABELS: Record<number, string> = {
  0: 'مُغلق',
  1: 'واحد',
  2: 'اثنان',
  3: 'ثلاثة',
  4: 'أربعة',
  5: 'خمسة',
};

const SIGNAL_COLORS: Record<number, string> = {
  0: '#ff4444',
  1: '#ff8800',
  2: '#ffcc00',
  3: '#88ff00',
  4: '#00ff88',
  5: '#00ddff',
};

export default function HomeScreen() {
  const [permission, requestPermission] = useCameraPermissions();
  const cameraRef = useRef<CameraView>(null);
  const insets = useSafeAreaInsets();
  const prevCountRef = useRef<number>(-99);

  const { landmarks, fingerCount, isModelLoaded, isReady } = useHandDetection(cameraRef as any);
  const { isConnected, deviceName, sendSignal, lastSent } = useUsbSerial();

  // Send USB signal whenever finger count changes
  useEffect(() => {
    if (fingerCount !== prevCountRef.current && fingerCount >= 0) {
      prevCountRef.current = fingerCount;
      sendSignal(fingerCount);
    }
  }, [fingerCount, sendSignal]);

  // Loading permission state
  if (!permission) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator color="#00ff88" size="large" />
      </View>
    );
  }

  // Permission not granted
  if (!permission.granted) {
    return (
      <PermissionScreen
        onRequest={requestPermission}
        canAskAgain={permission.canAskAgain}
      />
    );
  }

  const accentColor =
    fingerCount >= 0 && fingerCount <= 5
      ? SIGNAL_COLORS[fingerCount]
      : '#00ff88';

  return (
    <View style={styles.container}>
      <StatusBar style="light" />

      {/* Full-screen Camera */}
      <CameraView
        ref={cameraRef}
        style={StyleSheet.absoluteFill}
        facing="front"
      />

      {/* Hand skeleton overlay */}
      <HandOverlay landmarks={landmarks} />

      {/* Model loading overlay */}
      {!isModelLoaded && (
        <View style={styles.loadingOverlay}>
          <ActivityIndicator color="#00ff88" size="small" />
          <Text style={styles.loadingText}>تحميل نموذج الذكاء...</Text>
        </View>
      )}

      {/* ── Top: Finger Count Badge ── */}
      <View
        style={[
          styles.countCard,
          { top: insets.top + (Platform.OS === 'web' ? 67 : 16) },
        ]}
      >
        <Text style={[styles.countNumber, { color: accentColor }]}>
          {!isModelLoaded
            ? '?'
            : fingerCount < 0
            ? '—'
            : String(fingerCount)}
        </Text>
        <Text style={styles.countLabel}>
          {!isModelLoaded
            ? 'تحميل...'
            : fingerCount < 0
            ? 'لا توجد يد'
            : FINGER_LABELS[fingerCount] ?? String(fingerCount)}
        </Text>
      </View>

      {/* ── Signal pill (shows last sent value) ── */}
      {fingerCount >= 0 && (
        <View
          style={[
            styles.signalPill,
            { top: insets.top + (Platform.OS === 'web' ? 67 : 16) + 8, right: 16 },
            { borderColor: accentColor },
          ]}
        >
          <Text style={[styles.signalText, { color: accentColor }]}>
            ↑ {fingerCount}
          </Text>
        </View>
      )}

      {/* ── Bottom: USB Status ── */}
      <View
        style={[
          styles.usbBar,
          { bottom: insets.bottom + (Platform.OS === 'web' ? 34 : 0) + 20 },
        ]}
      >
        <View
          style={[
            styles.usbDot,
            { backgroundColor: isConnected ? '#00ff88' : '#333' },
          ]}
        />
        <Feather
          name="cpu"
          size={13}
          color={isConnected ? '#00ff88' : '#555'}
        />
        <Text style={[styles.usbText, { color: isConnected ? '#00ff88' : '#555' }]}>
          {isConnected
            ? `${deviceName ?? 'Arduino'} • آخر إشارة: ${lastSent >= 0 ? lastSent : '—'}`
            : 'USB غير متصل'}
        </Text>
      </View>

      {/* ── Scan frame corners ── */}
      <View style={[styles.corner, styles.cornerTL, { top: insets.top + 80, left: 20 }]} />
      <View style={[styles.corner, styles.cornerTR, { top: insets.top + 80, right: 20 }]} />
      <View style={[styles.corner, styles.cornerBL, { bottom: insets.bottom + 80, left: 20 }]} />
      <View style={[styles.corner, styles.cornerBR, { bottom: insets.bottom + 80, right: 20 }]} />
    </View>
  );
}

const CORNER_SIZE = 24;
const CORNER_THICK = 3;
const CORNER_COLOR = '#00ff8866';

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  loading: {
    flex: 1,
    backgroundColor: '#050a05',
    alignItems: 'center',
    justifyContent: 'center',
  },
  loadingOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: '#000000cc',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  loadingText: {
    color: '#00ff88',
    fontSize: 14,
    fontFamily: 'Inter_400Regular',
  },

  // Finger count badge
  countCard: {
    position: 'absolute',
    alignSelf: 'center',
    alignItems: 'center',
    backgroundColor: '#050a05cc',
    borderWidth: 1,
    borderColor: '#1a3a1a',
    borderRadius: 20,
    paddingHorizontal: 28,
    paddingVertical: 12,
    minWidth: 120,
  },
  countNumber: {
    fontSize: 56,
    fontWeight: '700',
    fontFamily: 'Inter_700Bold',
    lineHeight: 64,
    textShadowColor: '#00ff8844',
    textShadowRadius: 20,
    textShadowOffset: { width: 0, height: 0 },
  },
  countLabel: {
    fontSize: 13,
    color: '#558855',
    fontFamily: 'Inter_400Regular',
    marginTop: 2,
  },

  // Signal pill
  signalPill: {
    position: 'absolute',
    backgroundColor: '#050a05cc',
    borderWidth: 1,
    borderRadius: 20,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  signalText: {
    fontSize: 13,
    fontWeight: '700',
    fontFamily: 'Inter_700Bold',
  },

  // USB status bar
  usbBar: {
    position: 'absolute',
    alignSelf: 'center',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: '#050a05cc',
    borderWidth: 1,
    borderColor: '#1a3a1a',
    borderRadius: 20,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  usbDot: {
    width: 7,
    height: 7,
    borderRadius: 4,
  },
  usbText: {
    fontSize: 12,
    fontFamily: 'Inter_400Regular',
  },

  // Scan frame corners
  corner: {
    position: 'absolute',
    width: CORNER_SIZE,
    height: CORNER_SIZE,
  },
  cornerTL: {
    borderTopWidth: CORNER_THICK,
    borderLeftWidth: CORNER_THICK,
    borderTopLeftRadius: 4,
    borderColor: CORNER_COLOR,
  },
  cornerTR: {
    borderTopWidth: CORNER_THICK,
    borderRightWidth: CORNER_THICK,
    borderTopRightRadius: 4,
    borderColor: CORNER_COLOR,
  },
  cornerBL: {
    borderBottomWidth: CORNER_THICK,
    borderLeftWidth: CORNER_THICK,
    borderBottomLeftRadius: 4,
    borderColor: CORNER_COLOR,
  },
  cornerBR: {
    borderBottomWidth: CORNER_THICK,
    borderRightWidth: CORNER_THICK,
    borderBottomRightRadius: 4,
    borderColor: CORNER_COLOR,
  },
});
