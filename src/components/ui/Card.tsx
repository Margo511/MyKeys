import { PropsWithChildren } from 'react';
import { StyleSheet, View } from 'react-native';

import { useAppTheme } from '@/design-system/theme/AppThemeProvider';

export function Card({ children }: PropsWithChildren) {
  const { colors } = useAppTheme();

  return (
    <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.border }]}>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  card: { borderRadius: 22, borderWidth: 1, padding: 20 },
});
