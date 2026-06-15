import React, { useState } from 'react';
import {
  View, Text, Image, TouchableOpacity, ScrollView,
  ActivityIndicator, StyleSheet, Alert,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Disclaimer } from '@/components/Disclaimer';
import { ConfidenceRing } from '@/components/ConfidenceRing';
import { identifyPill } from '@/lib/identify';
import { supabase, PILL_BUCKET } from '@/lib/supabaseClient';
import type { Identification } from '@/lib/types';

type Status = 'idle' | 'identifying' | 'done' | 'error';

export default function ScanScreen() {
  const [imageUri, setImageUri] = useState<string | null>(null);
  const [status, setStatus] = useState<Status>('idle');
  const [result, setResult] = useState<Identification | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  function reset() {
    setImageUri(null);
    setStatus('idle');
    setResult(null);
    setError(null);
    setSaved(false);
  }

  async function pickImage(source: 'camera' | 'library') {
    const permFn =
      source === 'camera'
        ? ImagePicker.requestCameraPermissionsAsync
        : ImagePicker.requestMediaLibraryPermissionsAsync;

    const { status: perm } = await permFn();
    if (perm !== 'granted') {
      Alert.alert('Permission required', `PillID needs ${source} access to identify pills.`);
      return;
    }

    const launchFn =
      source === 'camera'
        ? ImagePicker.launchCameraAsync
        : ImagePicker.launchImageLibraryAsync;

    const res = await launchFn({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsEditing: true,
      quality: 0.85,
    });

    if (res.canceled) return;
    const asset = res.assets[0];
    setImageUri(asset.uri);
    setSaved(false);
    setResult(null);
    await runIdentify(asset.uri);
  }

  async function runIdentify(uri: string) {
    setStatus('identifying');
    setError(null);
    try {
      const id = await identifyPill(uri);
      setResult(id);
      setStatus('done');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setStatus('error');
    }
  }

  async function addToCabinet() {
    if (!result) return;
    setSaving(true);
    try {
      let image_path: string | null = null;
      if (imageUri) {
        const ext = imageUri.split('.').pop() ?? 'jpg';
        const path = `pill-${Date.now()}.${ext}`;
        const response = await fetch(imageUri);
        const arrayBuffer = await response.arrayBuffer();
        const { error: upErr } = await supabase.storage
          .from(PILL_BUCKET)
          .upload(path, arrayBuffer, { contentType: `image/${ext}`, upsert: false });
        if (!upErr) image_path = path;
      }

      const v = result.vision;
      const { error: insErr } = await supabase.from('medications').insert({
        name: result.canonical_name ?? v.name ?? 'Unknown medication',
        brand_name: result.label?.brand_name ?? null,
        rxcui: result.rxcui,
        strength: v.strength,
        dosage_form: v.dosage_form,
        imprint: v.imprint,
        shape: v.shape,
        color: v.color,
        confidence: v.confidence,
        image_path,
        label_data: result.label,
      });
      if (insErr) throw insErr;
      setSaved(true);
    } catch (e) {
      Alert.alert('Save failed', e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  }

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.container} showsVerticalScrollIndicator={false}>
        <Text style={styles.eyebrow}>Scan · identify · verify</Text>
        <Text style={styles.title}>Identify a pill</Text>
        <Text style={styles.sub}>
          Take or upload a clear photo. Gemma 4 reads it on-device and looks up the FDA label.
        </Text>

        <Disclaimer />

        {!imageUri && (
          <View style={styles.card}>
            <View style={styles.uploadZone}>
              <Text style={styles.cameraIcon}>📷</Text>
              <Text style={styles.uploadTitle}>Add a pill photo</Text>
              <Text style={styles.uploadSub}>Good lighting and a plain background help.</Text>
              <View style={styles.btnRow}>
                <TouchableOpacity style={styles.btnPrimary} onPress={() => pickImage('camera')}>
                  <Text style={styles.btnPrimaryText}>Take photo</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.btnSecondary} onPress={() => pickImage('library')}>
                  <Text style={styles.btnSecondaryText}>Choose from library</Text>
                </TouchableOpacity>
              </View>
            </View>
          </View>
        )}

        {imageUri && (
          <>
            <View style={styles.card}>
              <Image source={{ uri: imageUri }} style={styles.preview} resizeMode="contain" />
              <View style={styles.previewRow}>
                <Text style={styles.previewLabel}>Your photo</Text>
                <TouchableOpacity onPress={reset}>
                  <Text style={styles.startOver}>Start over</Text>
                </TouchableOpacity>
              </View>
            </View>

            {status === 'identifying' && (
              <View style={[styles.card, styles.center]}>
                <ActivityIndicator color="#5B8266" size="large" />
                <Text style={styles.identifying}>Reading pill with Gemma 4…</Text>
              </View>
            )}

            {status === 'error' && (
              <View style={[styles.card, styles.errorCard]}>
                <Text style={styles.errorTitle}>Couldn't identify this pill</Text>
                <Text style={styles.errorBody}>{error}</Text>
                <TouchableOpacity style={styles.btnSecondary} onPress={() => runIdentify(imageUri)}>
                  <Text style={styles.btnSecondaryText}>Try again</Text>
                </TouchableOpacity>
              </View>
            )}

            {status === 'done' && result && (
              <ResultCard result={result} saved={saved} saving={saving} onSave={addToCabinet} />
            )}
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function ResultCard({
  result, saved, saving, onSave,
}: { result: Identification; saved: boolean; saving: boolean; onSave: () => void }) {
  const v = result.vision;
  const displayName = result.canonical_name ?? v.name ?? 'Not recognized';
  const attrs = [
    ['Imprint', v.imprint], ['Shape', v.shape], ['Color', v.color],
    ['Strength', v.strength], ['Form', v.dosage_form],
  ].filter(([, val]) => val) as [string, string][];

  const [showPurpose, setShowPurpose] = useState(false);
  const [showWarnings, setShowWarnings] = useState(false);

  return (
    <View style={styles.card}>
      <View style={styles.resultHeader}>
        <View style={{ flex: 1 }}>
          <Text style={styles.bestGuess}>Best guess</Text>
          <Text style={styles.drugName}>{displayName}</Text>
          {result.label?.brand_name && (
            <Text style={styles.brandName}>Brand: {result.label.brand_name}</Text>
          )}
        </View>
        <ConfidenceRing value={v.confidence} />
      </View>

      {v.reasoning ? <Text style={styles.reasoning}>"{v.reasoning}"</Text> : null}

      {attrs.length > 0 && (
        <View style={styles.attrGrid}>
          {attrs.map(([k, val]) => (
            <View key={k} style={styles.attrItem}>
              <Text style={styles.attrKey}>{k}</Text>
              <Text style={styles.attrVal}>{val}</Text>
            </View>
          ))}
        </View>
      )}

      {v.candidates.length > 0 && (
        <Text style={styles.candidates}>
          <Text style={styles.candidatesLabel}>Also possible: </Text>
          {v.candidates.join(', ')}
        </Text>
      )}

      {result.label && (
        <View style={styles.labelSection}>
          <Text style={styles.fdaLabel}>From the FDA label</Text>
          {result.label.purpose && (
            <TouchableOpacity style={styles.accordion} onPress={() => setShowPurpose(!showPurpose)}>
              <Text style={styles.accordionTitle}>› Purpose</Text>
              {showPurpose && <Text style={styles.accordionBody}>{result.label.purpose}</Text>}
            </TouchableOpacity>
          )}
          {result.label.warnings && (
            <TouchableOpacity style={styles.accordion} onPress={() => setShowWarnings(!showWarnings)}>
              <Text style={styles.accordionTitle}>› Warnings</Text>
              {showWarnings && <Text style={styles.accordionBody}>{result.label.warnings}</Text>}
            </TouchableOpacity>
          )}
        </View>
      )}

      <View style={styles.actionRow}>
        {saved ? (
          <View style={styles.savedChip}>
            <Text style={styles.savedText}>✓ Added to cabinet</Text>
          </View>
        ) : (
          <TouchableOpacity style={styles.btnPrimary} onPress={onSave} disabled={saving}>
            <Text style={styles.btnPrimaryText}>{saving ? 'Saving…' : 'Add to cabinet'}</Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
}

const SAGE = '#5B8266';
const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#FBF9F3' },
  container: { padding: 20, gap: 14, paddingBottom: 32 },
  eyebrow: { fontSize: 11, fontWeight: '700', color: '#5B8266', letterSpacing: 1.5, textTransform: 'uppercase' },
  title: { fontSize: 28, fontWeight: '700', color: '#1C2321', letterSpacing: -0.5 },
  sub: { fontSize: 14, color: '#4B5553', lineHeight: 20 },
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius: 20,
    overflow: 'hidden',
    shadowColor: '#2C3A2E',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 12,
    elevation: 3,
  },
  uploadZone: { alignItems: 'center', padding: 32, gap: 10 },
  cameraIcon: { fontSize: 44 },
  uploadTitle: { fontSize: 16, fontWeight: '600', color: '#1C2321' },
  uploadSub: { fontSize: 13, color: '#8A9490', textAlign: 'center' },
  btnRow: { width: '100%', gap: 10, marginTop: 4 },
  btnPrimary: { backgroundColor: SAGE, borderRadius: 14, paddingVertical: 14, alignItems: 'center' },
  btnPrimaryText: { color: '#FFF', fontSize: 15, fontWeight: '600' },
  btnSecondary: { backgroundColor: '#F0F4F0', borderRadius: 14, paddingVertical: 13, alignItems: 'center', borderWidth: 1, borderColor: '#E3ECE4' },
  btnSecondaryText: { color: '#3B6347', fontSize: 15, fontWeight: '600' },
  preview: { width: '100%', height: 240, backgroundColor: '#F4F7F4' },
  previewRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 12 },
  previewLabel: { fontSize: 13, color: '#8A9490' },
  startOver: { fontSize: 14, color: SAGE, fontWeight: '500' },
  center: { padding: 32, alignItems: 'center', gap: 12 },
  identifying: { fontSize: 14, color: '#4B5553', textAlign: 'center' },
  errorCard: { padding: 20, gap: 8, borderWidth: 1, borderColor: '#FECACA' },
  errorTitle: { fontSize: 16, fontWeight: '600', color: '#991B1B' },
  errorBody: { fontSize: 14, color: '#7F1D1D', lineHeight: 20 },
  resultHeader: { flexDirection: 'row', alignItems: 'flex-start', padding: 20, gap: 12 },
  bestGuess: { fontSize: 11, color: '#8A9490', textTransform: 'uppercase', letterSpacing: 1 },
  drugName: { fontSize: 20, fontWeight: '600', color: '#1C2321', textTransform: 'capitalize', marginTop: 2 },
  brandName: { fontSize: 13, color: '#4B5553', marginTop: 2 },
  reasoning: { fontSize: 13, color: '#4B5553', fontStyle: 'italic', paddingHorizontal: 20, marginTop: -8 },
  attrGrid: { flexDirection: 'row', flexWrap: 'wrap', backgroundColor: '#F4F7F4', margin: 16, borderRadius: 12, padding: 12, gap: 12 },
  attrItem: { minWidth: '40%' },
  attrKey: { fontSize: 11, color: '#8A9490' },
  attrVal: { fontSize: 13, fontWeight: '600', color: '#1C2321', textTransform: 'capitalize' },
  candidates: { paddingHorizontal: 20, fontSize: 13, color: '#4B5553', textTransform: 'capitalize' },
  candidatesLabel: { color: '#8A9490' },
  labelSection: { borderTopWidth: 1, borderColor: '#F4F7F4', margin: 16, paddingTop: 12, gap: 8 },
  fdaLabel: { fontSize: 11, color: '#8A9490', textTransform: 'uppercase', letterSpacing: 1 },
  accordion: { backgroundColor: '#F8FAF8', borderRadius: 10, padding: 12 },
  accordionTitle: { fontSize: 14, fontWeight: '500', color: '#1C2321' },
  accordionBody: { fontSize: 13, color: '#4B5553', lineHeight: 20, marginTop: 8 },
  actionRow: { padding: 20, paddingTop: 8 },
  savedChip: { backgroundColor: '#F0FDF4', borderRadius: 12, paddingVertical: 12, paddingHorizontal: 16, alignItems: 'center', borderWidth: 1, borderColor: '#BBF7D0' },
  savedText: { fontSize: 14, fontWeight: '600', color: '#166534' },
});
