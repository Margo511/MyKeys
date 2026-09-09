import { createClient, SupabaseClient } from '@supabase/supabase-js';

import { readPublicEnvironment } from '@/config/env';

let client: SupabaseClient | undefined;

export function getSupabaseClient(): SupabaseClient {
  if (!client) {
    const environment = readPublicEnvironment();
    client = createClient(environment.supabaseUrl, environment.supabasePublishableKey, {
      auth: {
        autoRefreshToken: true,
        detectSessionInUrl: false,
        persistSession: false,
      },
    });
  }

  return client;
}
