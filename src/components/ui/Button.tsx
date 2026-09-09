import { Pressable, StyleSheet, Text } from 'react-native';

import { useAppTheme } from '@/design-system/theme/AppThemeProvider';

type ButtonProps = {
  label: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary';
  disabled?: boolean;
};

export function Button({ label, onPress, variant = 'primary', disabled = false }: ButtonProps) {
  const { colors } = useAppTheme();
  const isPrimary = variant === 'primary';

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        {
          backgroundColor: isPrimary
            ? pressed
              ? colors.accentPressed
              : colors.accent
            : pressed
              ? colors.surfacePressed
              : colors.surface,
          borderColor: isPrimary ? colors.accent : colors.border,
          opacity: disabled ? 0.55 : 1,
        },
      ]}
    >
      <Text style={[styles.label, { color: isPrimary ? colors.accentContrast : colors.text }]}>
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    alignItems: 'center',
    borderRadius: 16,
    borderWidth: 1,
    justifyContent: 'center',
    minHeight: 52,
    paddingHorizontal: 22,
  },
  label: { fontSize: 16, fontWeight: '700' },
});
