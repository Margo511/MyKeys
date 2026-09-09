import { router } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Screen } from '@/components/ui/Screen';
import { useAppTheme } from '@/design-system/theme/AppThemeProvider';

const layers = [
  ['Presentación', 'Rutas y componentes accesibles, sin lógica de negocio.'],
  ['Aplicación y dominio', 'Casos de uso, invariantes y puertos independientes de Expo.'],
  ['Infraestructura', 'Supabase, criptografía y capacidades nativas detrás de adaptadores.'],
] as const;

export default function FoundationDetailsScreen() {
  const { colors } = useAppTheme();

  return (
    <Screen>
      <View style={styles.header}>
        <Text style={[styles.kicker, { color: colors.accent }]}>ARQUITECTURA</Text>
        <Text accessibilityRole="header" style={[styles.title, { color: colors.text }]}>
          Límites claros desde el primer día
        </Text>
        <Text style={[styles.body, { color: colors.textMuted }]}>
          La interfaz puede cambiar sin poner claves, sesiones o reglas del vault en riesgo.
        </Text>
      </View>

      {layers.map(([title, description]) => (
        <Card key={title}>
          <Text style={[styles.layerTitle, { color: colors.text }]}>{title}</Text>
          <Text style={[styles.body, { color: colors.textMuted }]}>{description}</Text>
        </Card>
      ))}

      <Button label="Volver" variant="secondary" onPress={() => router.back()} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { gap: 10, paddingBottom: 8, paddingTop: 38 },
  kicker: { fontSize: 12, fontWeight: '800', letterSpacing: 1.6 },
  title: { fontSize: 34, fontWeight: '800', letterSpacing: -1, lineHeight: 39 },
  body: { fontSize: 15, lineHeight: 23 },
  layerTitle: { fontSize: 17, fontWeight: '700', marginBottom: 6 },
});
