import React, { useEffect, useState, useCallback } from 'react';
import {
  View, Text, FlatList, TouchableOpacity, StyleSheet, ActivityIndicator,
} from 'react-native';
import { useRouter, useFocusEffect } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { MedCard } from '@/components/MedCard';
import { supabase, PILL_BUCKET } from '@/lib/supabaseClient';
import type { Medication } from '@/lib/types';

export default function CabinetScreen() {
  const router = useRouter();
  const [meds, setMeds] = useState<Medication[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    const { data, error } = await supabase
      .from('medications')
      .select('*')
      .order('created_at', { ascending: false });
    if (error) setError(error.message);
    else setMeds(data as Medication[]);
  }

  useFocusEffect(
    useCallback(() => { void load(); }, []),
  );

  async function remove(id: string, image_path: string | null) {
    setMeds((prev) => prev?.filter((m) => m.id !== id) ?? null);
    await supabase.from('medications').delete().eq('id', id);
    if (image_path) await supabase.storage.from(PILL_BUCKET).remove([image_path]);
  }

  const count = meds?.length ?? 0;

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <View>
          <Text style={styles.title}>Medicine cabinet</Text>
          <Text style={styles.sub}>
            {meds === null ? 'Loading…' : `${count} medication${count === 1 ? '' : 's'} saved`}
          </Text>
        </View>
        {count >= 2 && (
          <TouchableOpacity
            style={styles.btnSecondary}
            onPress={() => router.push('/(tabs)/interactions')}
          >
            <Text style={styles.btnSecondaryText}>Check interactions</Text>
          </TouchableOpacity>
        )}
      </View>

      {error && (
        <View style={styles.errorCard}>
          <Text style={styles.errorText}>{error}</Text>
        </View>
      )}

      {meds === null && (
        <View style={styles.center}>
          <ActivityIndicator color="#5B8266" size="large" />
        </View>
      )}

      {meds !== null && meds.length === 0 && (
        <View style={styles.empty}>
          <Text style={styles.emptyIcon}>🗂</Text>
          <Text style={styles.emptyTitle}>Your cabinet is empty</Text>
          <Text style={styles.emptySub}>Scan a pill to add your first medication.</Text>
          <TouchableOpacity
            style={styles.btnPrimary}
            onPress={() => router.push('/(tabs)/')}
          >
            <Text style={styles.btnPrimaryText}>Scan a pill</Text>
          </TouchableOpacity>
        </View>
      )}

      {meds !== null && meds.length > 0 && (
        <FlatList
          data={meds}
          keyExtractor={(m) => m.id}
          renderItem={({ item }) => (
            <MedCard med={item} onRemove={() => remove(item.id, item.image_path)} />
          )}
          contentContainerStyle={styles.list}
          showsVerticalScrollIndicator={false}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#FBF9F3' },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 12,
  },
  title: { fontSize: 26, fontWeight: '700', color: '#1C2321', letterSpacing: -0.4 },
  sub: { fontSize: 13, color: '#8A9490', marginTop: 2 },
  btnSecondary: {
    backgroundColor: '#F0F4F0',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderWidth: 1,
    borderColor: '#E3ECE4',
  },
  btnSecondaryText: { color: '#3B6347', fontSize: 13, fontWeight: '600' },
  btnPrimary: { backgroundColor: '#5B8266', borderRadius: 14, paddingVertical: 14, paddingHorizontal: 32, marginTop: 8 },
  btnPrimaryText: { color: '#FFF', fontSize: 15, fontWeight: '600' },
  list: { paddingHorizontal: 20, paddingBottom: 32 },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  empty: { flex: 1, justifyContent: 'center', alignItems: 'center', gap: 8, padding: 32 },
  emptyIcon: { fontSize: 48 },
  emptyTitle: { fontSize: 18, fontWeight: '600', color: '#1C2321' },
  emptySub: { fontSize: 14, color: '#8A9490', textAlign: 'center' },
  errorCard: { margin: 20, backgroundColor: '#FEF2F2', borderRadius: 12, padding: 16 },
  errorText: { color: '#991B1B', fontSize: 14 },
});
