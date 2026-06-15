'use client';
import React, { useState } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet, ScrollView,
  ActivityIndicator,
} from 'react-native';
import { router } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { downloadModels, DownloadProgress, GEMMA_MODELS } from '@/lib/modelManager';

type Stage = 'intro' | 'downloading' | 'done' | 'error';

function fmt(bytes: number): string {
  if (bytes >= 1e9) return `${(bytes / 1e9).toFixed(1)} GB`;
  if (bytes >= 1e6) return `${(bytes / 1e6).toFixed(0)} MB`;
  return `${Math.round(bytes / 1e3)} KB`;
}

export default function SetupScreen() {
  const [stage, setStage] = useState<Stage>('intro');
  const [progress, setProgress] = useState<DownloadProgress | null>(null);
  const [errorMsg, setErrorMsg] = useState('');

  const totalSize = GEMMA_MODELS.reduce((s, m) => s + m.sizeBytes, 0);

  async function startDownload() {
    setStage('downloading');
    const ok = await downloadModels(
      (p) => setProgress(p),
      (msg) => { setErrorMsg(msg); setStage('error'); },
    );
    if (ok) setStage('done');
  }

  const pct = progress
    ? Math.round(
        ((progress.fileIndex * 1 + progress.bytesWritten / progress.totalBytes) /
          progress.totalFiles) *
          100,
      )
    : 0;

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.container} bounces={false}>
        <Text style={styles.pill}>💊</Text>
        <Text style={styles.title}>PillID</Text>
        <Text style={styles.sub}>
          On-device pill identification — Gemma 4 runs entirely on your phone.
        </Text>

        {stage === 'intro' && (
          <View style={styles.card}>
            <Text style={styles.cardTitle}>One-time model download</Text>
            <Text style={styles.cardBody}>
              PillID uses a quantized Gemma 3 4B vision model (~{fmt(totalSize)}) to identify pills
              and check interactions directly on your device. Your images never leave your phone.
            </Text>

            <View style={styles.fileList}>
              {GEMMA_MODELS.map((m) => (
                <View key={m.name} style={styles.fileRow}>
                  <Text style={styles.fileName}>{m.name}</Text>
                  <Text style={styles.fileSize}>{fmt(m.sizeBytes)}</Text>
                </View>
              ))}
            </View>

            <TouchableOpacity style={styles.btnPrimary} onPress={startDownload}>
              <Text style={styles.btnPrimaryText}>Download model</Text>
            </TouchableOpacity>
          </View>
        )}

        {stage === 'downloading' && (
          <View style={styles.card}>
            <ActivityIndicator color="#5B8266" size="large" />
            <Text style={[styles.cardTitle, { marginTop: 16 }]}>
              Downloading… {pct}%
            </Text>
            {progress && (
              <>
                <Text style={styles.cardBody}>
                  {progress.file} ({progress.fileIndex + 1}/{progress.totalFiles})
                </Text>
                <View style={styles.trackBg}>
                  <View style={[styles.trackFill, { width: `${pct}%` }]} />
                </View>
                <Text style={styles.fileSize}>
                  {fmt(progress.bytesWritten)} / {fmt(progress.totalBytes)}
                </Text>
              </>
            )}
            <Text style={styles.hint}>Keep the app open while downloading.</Text>
          </View>
        )}

        {stage === 'done' && (
          <View style={styles.card}>
            <Text style={styles.checkmark}>✅</Text>
            <Text style={styles.cardTitle}>Model ready!</Text>
            <Text style={styles.cardBody}>
              Gemma 4 is installed and ready. All inference runs on-device.
            </Text>
            <TouchableOpacity
              style={styles.btnPrimary}
              onPress={() => router.replace('/(tabs)')}
            >
              <Text style={styles.btnPrimaryText}>Get started</Text>
            </TouchableOpacity>
          </View>
        )}

        {stage === 'error' && (
          <View style={styles.card}>
            <Text style={styles.cardTitle}>Download failed</Text>
            <Text style={styles.cardBody}>{errorMsg}</Text>
            <TouchableOpacity style={styles.btnPrimary} onPress={() => { setStage('intro'); setErrorMsg(''); }}>
              <Text style={styles.btnPrimaryText}>Try again</Text>
            </TouchableOpacity>
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#FBF9F3' },
  container: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 12 },
  pill: { fontSize: 56 },
  title: { fontSize: 32, fontWeight: '700', color: '#1C2321', letterSpacing: -0.5 },
  sub: { fontSize: 15, color: '#4B5553', textAlign: 'center', maxWidth: 300 },
  card: {
    width: '100%',
    backgroundColor: '#FFFFFF',
    borderRadius: 20,
    padding: 20,
    gap: 12,
    shadowColor: '#2C3A2E',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 16,
    elevation: 4,
    alignItems: 'center',
  },
  cardTitle: { fontSize: 18, fontWeight: '600', color: '#1C2321', textAlign: 'center' },
  cardBody: { fontSize: 14, color: '#4B5553', textAlign: 'center', lineHeight: 20 },
  fileList: { width: '100%', gap: 6 },
  fileRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 4, borderBottomWidth: 1, borderColor: '#F4F7F4' },
  fileName: { fontSize: 12, color: '#4B5553', flex: 1 },
  fileSize: { fontSize: 12, color: '#8A9490', fontWeight: '500' },
  btnPrimary: {
    backgroundColor: '#5B8266',
    borderRadius: 14,
    paddingVertical: 14,
    paddingHorizontal: 32,
    width: '100%',
    alignItems: 'center',
    marginTop: 4,
  },
  btnPrimaryText: { color: '#FFFFFF', fontSize: 16, fontWeight: '600' },
  trackBg: { width: '100%', height: 8, backgroundColor: '#E3ECE4', borderRadius: 4, overflow: 'hidden' },
  trackFill: { height: '100%', backgroundColor: '#5B8266', borderRadius: 4 },
  hint: { fontSize: 12, color: '#8A9490', textAlign: 'center' },
  checkmark: { fontSize: 40 },
});
