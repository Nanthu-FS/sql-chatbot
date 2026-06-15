import React, { useEffect, useState } from 'react';
import { Stack, router } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { modelsReady } from '@/lib/modelManager';

export default function RootLayout() {
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    modelsReady().then((ready) => {
      if (!ready) router.replace('/setup');
      setChecked(true);
    });
  }, []);

  if (!checked) return null;

  return (
    <SafeAreaProvider>
      <StatusBar style="dark" />
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="setup" options={{ animation: 'fade' }} />
      </Stack>
    </SafeAreaProvider>
  );
}
