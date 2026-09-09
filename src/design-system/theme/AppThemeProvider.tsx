import { createContext, PropsWithChildren, useContext, useMemo } from 'react';
import { ColorSchemeName, useColorScheme } from 'react-native';

import { AppColors, darkColors, lightColors } from '../tokens/colors';

type AppTheme = {
  colorScheme: Exclude<ColorSchemeName, null | undefined>;
  colors: AppColors;
};

const AppThemeContext = createContext<AppTheme | null>(null);

export function AppThemeProvider({ children }: PropsWithChildren) {
  const systemColorScheme = useColorScheme();
  const colorScheme = systemColorScheme === 'dark' ? 'dark' : 'light';
  const value = useMemo<AppTheme>(
    () => ({ colorScheme, colors: colorScheme === 'dark' ? darkColors : lightColors }),
    [colorScheme],
  );

  return <AppThemeContext.Provider value={value}>{children}</AppThemeContext.Provider>;
}

export function useAppTheme(): AppTheme {
  const theme = useContext(AppThemeContext);

  if (!theme) {
    throw new Error('useAppTheme must be used inside AppThemeProvider');
  }

  return theme;
}
