import { StyleSheet, Text, View } from 'react-native';

import { useAppTheme } from '@/design-system/theme/AppThemeProvider';

export function StatusPill({ label }: { label: string }) {
  const { colors } = useAppTheme();

  return (
    <View style={[styles.pill, { backgroundColor: colors.successSoft }]}>
      <View style={[styles.dot, { backgroundColor: colors.success }]} />
      <Text style={[styles.label, { color: colors.success }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  pill: {
    alignItems: 'center',
    borderRadius: 99,
    flexDirection: 'row',
    gap: 7,
    paddingHorizontal: 11,
    paddingVertical: 7,
  },
  dot: { borderRadius: 4, height: 7, width: 7 },
  label: { fontSize: 12, fontWeight: '800' },
});
