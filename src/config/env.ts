export type PublicEnvironment = {
  appEnv: 'development' | 'staging' | 'production';
  supabaseUrl: string;
  supabasePublishableKey: string;
};

const allowedAppEnvironments = new Set<PublicEnvironment['appEnv']>([
  'development',
  'staging',
  'production',
]);

export function readPublicEnvironment(
  source: Record<string, string | undefined> = process.env,
): PublicEnvironment {
  const appEnv = source.EXPO_PUBLIC_APP_ENV ?? 'development';
  const supabaseUrl = source.EXPO_PUBLIC_SUPABASE_URL;
  const supabasePublishableKey = source.EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY;

  if (!allowedAppEnvironments.has(appEnv as PublicEnvironment['appEnv'])) {
    throw new Error('EXPO_PUBLIC_APP_ENV has an unsupported value');
  }

  if (!supabaseUrl || !supabasePublishableKey) {
    throw new Error('Public Supabase environment is incomplete');
  }

  const parsedUrl = new URL(supabaseUrl);
  if (
    parsedUrl.protocol !== 'https:' &&
    parsedUrl.hostname !== '127.0.0.1' &&
    parsedUrl.hostname !== 'localhost'
  ) {
    throw new Error('Supabase URL must use HTTPS outside local development');
  }

  return {
    appEnv: appEnv as PublicEnvironment['appEnv'],
    supabaseUrl: parsedUrl.toString().replace(/\/$/, ''),
    supabasePublishableKey,
  };
}
