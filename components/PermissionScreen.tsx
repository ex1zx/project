import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Linking,
  Platform,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Feather } from '@expo/vector-icons';

interface PermissionScreenProps {
  onRequest: () => void;
  canAskAgain: boolean;
}

export default function PermissionScreen({
  onRequest,
  canAskAgain,
}: PermissionScreenProps) {
  const insets = useSafeAreaInsets();

  const openSettings = () => {
    if (Platform.OS !== 'web') {
      try {
        Linking.openSettings();
      } catch {}
    }
  };

  return (
    <View
      style={[
        styles.container,
        { paddingTop: insets.top + 40, paddingBottom: insets.bottom + 40 },
      ]}
    >
      {/* Icon */}
      <View style={styles.iconWrapper}>
        <View style={styles.iconOuter}>
          <Feather name="camera" size={48} color="#00ff88" />
        </View>
      </View>

      <Text style={styles.title}>Project</Text>
      <Text style={styles.subtitle}>نظام التعرف على إيماءات اليد</Text>

      <View style={styles.divider} />

      {/* Permissions list */}
      <View style={styles.permList}>
        <PermRow icon="camera" label="الكاميرا الأمامية" desc="للكشف عن اليد في الوقت الفعلي" />
        <PermRow icon="cpu" label="USB / OTG" desc="لإرسال الأوامر للأردوينو" />
      </View>

      <View style={styles.divider} />

      {canAskAgain ? (
        <TouchableOpacity style={styles.btn} onPress={onRequest} activeOpacity={0.8}>
          <Feather name="unlock" size={18} color="#000" />
          <Text style={styles.btnText}>منح الصلاحيات</Text>
        </TouchableOpacity>
      ) : (
        <>
          <Text style={styles.deniedNote}>
            تم رفض الإذن. افتح الإعدادات لمنح صلاحية الكاميرا.
          </Text>
          <TouchableOpacity style={styles.btn} onPress={openSettings} activeOpacity={0.8}>
            <Feather name="settings" size={18} color="#000" />
            <Text style={styles.btnText}>فتح الإعدادات</Text>
          </TouchableOpacity>
        </>
      )}
    </View>
  );
}

function PermRow({
  icon,
  label,
  desc,
}: {
  icon: string;
  label: string;
  desc: string;
}) {
  return (
    <View style={styles.permRow}>
      <View style={styles.permIcon}>
        <Feather name={icon as any} size={20} color="#00ff88" />
      </View>
      <View style={styles.permText}>
        <Text style={styles.permLabel}>{label}</Text>
        <Text style={styles.permDesc}>{desc}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#050a05',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
  },
  iconWrapper: {
    marginBottom: 24,
  },
  iconOuter: {
    width: 100,
    height: 100,
    borderRadius: 50,
    backgroundColor: '#0d1f0d',
    borderWidth: 1.5,
    borderColor: '#00ff88',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#00ff88',
    shadowOpacity: 0.4,
    shadowRadius: 20,
    shadowOffset: { width: 0, height: 0 },
    elevation: 12,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#00ff88',
    letterSpacing: 3,
    marginBottom: 6,
    textTransform: 'uppercase',
  },
  subtitle: {
    fontSize: 14,
    color: '#558855',
    textAlign: 'center',
    fontFamily: 'Inter_400Regular',
  },
  divider: {
    width: '100%',
    height: 1,
    backgroundColor: '#1a3a1a',
    marginVertical: 28,
  },
  permList: {
    width: '100%',
    gap: 16,
  },
  permRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
  },
  permIcon: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: '#0d1f0d',
    borderWidth: 1,
    borderColor: '#1a3a1a',
    alignItems: 'center',
    justifyContent: 'center',
  },
  permText: {
    flex: 1,
  },
  permLabel: {
    color: '#e0ffe0',
    fontSize: 15,
    fontWeight: '600',
    fontFamily: 'Inter_600SemiBold',
  },
  permDesc: {
    color: '#558855',
    fontSize: 12,
    marginTop: 2,
    fontFamily: 'Inter_400Regular',
  },
  btn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    backgroundColor: '#00ff88',
    borderRadius: 14,
    paddingVertical: 16,
    paddingHorizontal: 36,
    width: '100%',
    shadowColor: '#00ff88',
    shadowOpacity: 0.35,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 4 },
    elevation: 8,
  },
  btnText: {
    color: '#000',
    fontSize: 16,
    fontWeight: '700',
    fontFamily: 'Inter_700Bold',
  },
  deniedNote: {
    color: '#558855',
    fontSize: 13,
    textAlign: 'center',
    marginBottom: 16,
    lineHeight: 20,
    fontFamily: 'Inter_400Regular',
  },
});
