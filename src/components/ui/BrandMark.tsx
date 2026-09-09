import { StyleSheet, Text, View } from 'react-native';

import { useAppTheme } from '@/design-system/theme/AppThemeProvider';

export function BrandMark() {
  const { colors } = useAppTheme();

  return (
    <View accessible accessibilityLabel="My Keys" style={styles.container}>
      <View style={[styles.icon, { backgroundColor: colors.accent }]}>
        <Text style={[styles.iconText, { color: colors.accentContrast }]}>K</Text>
      </View>
      <Text style={[styles.wordmark, { color: colors.text }]}>My Keys</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { alignItems: 'center', flexDirection: 'row', gap: 10 },
  icon: { alignItems: 'center', borderRadius: 12, height: 38, justifyContent: 'center', width: 38 },
  iconText: { fontSize: 18, fontWeight: '900' },
  wordmark: { fontSize: 18, fontWeight: '800', letterSpacing: -0.4 },
});
