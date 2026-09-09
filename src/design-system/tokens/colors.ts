export const lightColors = {
  canvas: '#F4F7FB',
  surface: '#FFFFFF',
  surfacePressed: '#E9EEF7',
  border: '#DDE5F0',
  text: '#132038',
  textMuted: '#51617A',
  textSubtle: '#718099',
  accent: '#175CD3',
  accentPressed: '#134AA9',
  accentContrast: '#FFFFFF',
  accentSoft: '#E8F0FF',
  success: '#087A55',
  successSoft: '#DDF7EC',
} as const;

export const darkColors = {
  canvas: '#09111F',
  surface: '#111C2E',
  surfacePressed: '#1A2940',
  border: '#253753',
  text: '#F4F7FC',
  textMuted: '#B4C0D2',
  textSubtle: '#8796AD',
  accent: '#75A7FF',
  accentPressed: '#9ABEFF',
  accentContrast: '#07101E',
  accentSoft: '#172F56',
  success: '#62D6A9',
  successSoft: '#123B31',
} as const;

export type AppColors = { [Key in keyof typeof lightColors]: string };
