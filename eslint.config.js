const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');

module.exports = defineConfig([
  {
    ignores: [
      '.expo/**',
      '.gradle-user-home/**',
      'android/**',
      'ios/**',
      'coverage/**',
      'dist/**',
      'mobile/.gradle/**',
      'mobile/.kotlin/**',
      'mobile/**/build/**',
      'supabase/.temp/**',
    ],
  },
  expoConfig,
  {
    rules: {
      'no-console': 'error',
    },
  },
]);
