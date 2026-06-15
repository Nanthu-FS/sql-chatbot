import React from 'react';
import { View, Text, Image, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import { supabase, PILL_BUCKET } from '@/lib/supabaseClient';
import type { Medication } from '@/lib/types';

interface Props {
  med: Medication;
  onRemove: () => void;
}

export function MedCard({ med, onRemove }: Props) {
  const imgUrl = med.image_path
    ? supabase.storage.from(PILL_BUCKET).getPublicUrl(med.image_path).data.publicUrl
    : null;

  const facts = [med.strength, med.dosage_form, med.color, med.shape]
    .filter(Boolean)
    .join(' · ');

  function confirmRemove() {
    Alert.alert('Remove medication', `Remove ${med.name} from your cabinet?`, [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Remove', style: 'destructive', onPress: onRemove },
    ]);
  }

  return (
    <View style={styles.card}>
      <View style={styles.thumb}>
        {imgUrl ? (
          <Image source={{ uri: imgUrl }} style={styles.img} resizeMode="cover" />
        ) : (
          <View style={styles.placeholder}>
            <Text style={styles.pillIcon}>💊</Text>
          </View>
        )}
      </View>

      <View style={styles.body}>
        <View style={styles.row}>
          <Text style={styles.name} numberOfLines={1}>{med.name}</Text>
          <TouchableOpacity onPress={confirmRemove} hitSlop={8} style={styles.trashBtn}>
            <Text style={styles.trash}>🗑</Text>
          </TouchableOpacity>
        </View>
        {med.brand_name ? <Text style={styles.sub} numberOfLines={1}>{med.brand_name}</Text> : null}
        {facts ? <Text style={styles.facts} numberOfLines={1}>{facts}</Text> : null}
        {med.imprint ? (
          <View style={styles.imprintChip}>
            <Text style={styles.imprintText}>Imprint: {med.imprint}</Text>
          </View>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 12,
    gap: 12,
    shadowColor: '#2C3A2E',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 8,
    elevation: 2,
    marginBottom: 10,
  },
  thumb: { width: 76, height: 76, borderRadius: 12, overflow: 'hidden', backgroundColor: '#F4F7F4' },
  img: { width: '100%', height: '100%' },
  placeholder: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  pillIcon: { fontSize: 28 },
  body: { flex: 1, justifyContent: 'center', gap: 2 },
  row: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: 4 },
  name: { flex: 1, fontSize: 15, fontWeight: '600', color: '#1C2321', textTransform: 'capitalize' },
  sub: { fontSize: 13, color: '#4B5553' },
  facts: { fontSize: 12, color: '#8A9490', textTransform: 'capitalize' },
  imprintChip: { backgroundColor: '#F0F4F0', borderRadius: 6, paddingHorizontal: 8, paddingVertical: 3, alignSelf: 'flex-start', marginTop: 2 },
  imprintText: { fontSize: 11, color: '#4B5553', fontWeight: '500' },
  trashBtn: { padding: 4 },
  trash: { fontSize: 16 },
});
