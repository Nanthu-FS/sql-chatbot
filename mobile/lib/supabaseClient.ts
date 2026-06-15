import { createClient } from '@supabase/supabase-js';
import AsyncStorage from '@react-native-async-storage/async-storage';
import Constants from 'expo-constants';

const url: string = Constants.expoConfig?.extra?.supabaseUrl ?? '';
const key: string = Constants.expoConfig?.extra?.supabaseAnonKey ?? '';

export const supabase = createClient(url, key, {
  auth: { storage: AsyncStorage, autoRefreshToken: true, persistSession: true },
});

export const PILL_BUCKET = 'pill-images';
