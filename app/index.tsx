import { router } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import { BrandMark } from '@/components/ui/BrandMark';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Screen } from '@/components/ui/Screen';
import { StatusPill } from '@/components/ui/StatusPill';
import { useAppTheme } from '@/design-system/theme/AppThemeProvider';

const foundations = [
  ['Expo SDK 57', 'React Native 0.86 · TypeScript estricto'],
  ['Navegación', 'Expo Router con rutas tipadas'],
  ['Datos remotos', 'Supabase local + TanStack Query'],
] as const;

export default function FoundationScreen() {
  const { colors } = useAppTheme();

  return (
    <Screen>
      <View style={styles.hero}>
        <View style={styles.brandRow}>
          <BrandMark />
          <StatusPill label="Fase 1" />
        </View>
        <Text accessibilityRole="header" style={[styles.eyebrow, { color: colors.accent }]}>
          TU VAULT, BAJO TU CONTROL
        </Text>
        <Text style={[styles.title, { color: colors.text }]}>
          La base segura ya está en marcha.
        </Text>
        <Text style={[styles.subtitle, { color: colors.textMuted }]}>
          Arquitectura móvil preparada para crecer sin mezclar interfaz, dominio e infraestructura.
        </Text>
      </View>

      <Card>
        <Text style={[styles.cardTitle, { color: colors.text }]}>Cimientos del proyecto</Text>
        <View style={styles.foundationList}>
          {foundations.map(([title, description], index) => (
            <View key={title} style={styles.foundationRow}>
              <View style={[styles.step, { backgroundColor: colors.accentSoft }]}>
                <Text style={[styles.stepText, { color: colors.accent }]}>{index + 1}</Text>
              </View>
              <View style={styles.foundationCopy}>
                <Text style={[styles.foundationTitle, { color: colors.text }]}>{title}</Text>
                <Text style={[styles.foundationDescription, { color: colors.textMuted }]}>
                  {description}
                </Text>
              </View>
            </View>
          ))}
        </View>
      </Card>

      <Button label="Ver la arquitectura base" onPress={() => router.push('/foundation')} />
      <Text style={[styles.note, { color: colors.textSubtle }]}>
        Auth, MFA y contenido del vault se incorporarán en las siguientes fases.
      </Text>
    </Screen>
  );
}

const styles = StyleSheet.create({
  hero: { gap: 12, paddingTop: 28 },
  brandRow: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  eyebrow: { fontSize: 12, fontWeight: '800', letterSpacing: 1.6, marginTop: 22 },
  title: { fontSize: 42, fontWeight: '800', letterSpacing: -1.5, lineHeight: 46, maxWidth: 560 },
  subtitle: { fontSize: 17, lineHeight: 25, maxWidth: 600 },
  cardTitle: { fontSize: 18, fontWeight: '700' },
  foundationList: { gap: 18, marginTop: 20 },
  foundationRow: { alignItems: 'center', flexDirection: 'row', gap: 14 },
  foundationCopy: { flex: 1, gap: 3 },
  foundationTitle: { fontSize: 15, fontWeight: '700' },
  foundationDescription: { fontSize: 14, lineHeight: 20 },
  step: { alignItems: 'center', borderRadius: 14, height: 42, justifyContent: 'center', width: 42 },
  stepText: { fontSize: 15, fontWeight: '800' },
  note: { fontSize: 12, lineHeight: 18, paddingBottom: 10, textAlign: 'center' },
});
