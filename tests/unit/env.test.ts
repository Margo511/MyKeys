import { readPublicEnvironment } from '@/config/env';

describe('readPublicEnvironment', () => {
  it('accepts the local Supabase URL', () => {
    expect(
      readPublicEnvironment({
        EXPO_PUBLIC_APP_ENV: 'development',
        EXPO_PUBLIC_SUPABASE_URL: 'http://127.0.0.1:54321',
        EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY: 'local-publishable-key',
      }),
    ).toEqual({
      appEnv: 'development',
      supabaseUrl: 'http://127.0.0.1:54321',
      supabasePublishableKey: 'local-publishable-key',
    });
  });

  it('rejects an insecure remote URL', () => {
    expect(() =>
      readPublicEnvironment({
        EXPO_PUBLIC_APP_ENV: 'production',
        EXPO_PUBLIC_SUPABASE_URL: 'http://example.com',
        EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY: 'publishable-key',
      }),
    ).toThrow('Supabase URL must use HTTPS outside local development');
  });

  it('never accepts an incomplete Supabase configuration', () => {
    expect(() => readPublicEnvironment({ EXPO_PUBLIC_APP_ENV: 'development' })).toThrow(
      'Public Supabase environment is incomplete',
    );
  });
});
