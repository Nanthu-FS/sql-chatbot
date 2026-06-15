import React, { useCallback, useState } from 'react';
import {
  View, Text, ScrollView, TouchableOpacity, StyleSheet, ActivityIndicator,
} from 'react-native';
import { useFocusEffect, useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Disclaimer } from '@/components/Disclaimer';
import { SeverityBadge, severityRank } from '@/components/SeverityBadge';
import { supabase } from '@/lib/supabaseClient';
import { checkInteractions } from '@/lib/interactions';
import type { InteractionFinding, Medication } from '@/lib/types';

type Stage = 'loading' | 'ready' | 'checking' | 'done' | 'error';

export default function InteractionsScreen() {
  const router = useRouter();
  const [meds, setMeds] = useState<Medication[]>([]);
  const [stage, setStage] = useState<Stage>('loading');
  const [findings, setFindings] = useState<InteractionFinding[] | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      setStage('loading');
      setFindings(null);
      setNote(null);
      setError(null);
      supabase
        .from('medications')
        .select('*')
        .order('created_at', { ascending: false })
        .then(({ data, error }) => {
          if (error) { setError(error.message); setStage('error'); }
          else { setMeds((data ?? []) as Medication[]); setStage('ready'); }
        });
    }, []),
  );

  async function run() {
    setStage('checking');
    setError(null);
    try {
      const { findings: f, note: n } = await checkInteractions(meds);
      const sorted = [...f].sort((a, b) => severityRank(b.severity) - severityRank(a.severity));
      setFindings(sorted);
      setNote(n);
      setStage('done');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setStage('error');
    }
  }

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.container} showsVerticalScrollIndicator={false}>
        <Text style={styles.title}>Interaction check</Text>
        <Text style={styles.sub}>
          Cross-checks your cabinet using FDA label text. Runs on-device with Gemma 4.
        </Text>

        <Disclaimer />

        {stage === 'loading' && (
          <View style={styles.center}>
            <ActivityIndicator color="#5B8266" />
          </View>
        )}

        {stage !== 'loading' && meds.length < 2 && (
          <View style={styles.emptyCard}>
            <Text style={styles.emptyTitle}>Add at least two medications</Text>
            <Text style={styles.emptySub}>
              Interaction checks need two or more saved medications.
            </Text>
            <TouchableOpacity style={styles.btnPrimary} onPress={() => router.push('/(tabs)/')}>
              <Text style={styles.btnPrimaryText}>Scan a pill</Text>
            </TouchableOpacity>
          </View>
        )}

        {meds.length >= 2 && (
          <View style={styles.card}>
            <Text style={styles.cardEyebrow}>Checking {meds.length} medications</Text>
            <View style={styles.chips}>
              {meds.map((m) => (
                <View key={m.id} style={styles.chip}>
                  <Text style={styles.chipText}>{m.name}</Text>
                </View>
              ))}
            </View>
            {stage !== 'done' && (
              <TouchableOpacity
                style={[styles.btnPrimary, stage === 'checking' && styles.disabled]}
                onPress={run}
                disabled={stage === 'checking'}
              >
                <Text style={styles.btnPrimaryText}>
                  {stage === 'checking' ? 'Checking with Gemma 4…' : 'Run interaction check'}
                </Text>
              </TouchableOpacity>
            )}
            {stage === 'checking' && <ActivityIndicator color="#5B8266" style={{ marginTop: 8 }} />}
          </View>
        )}

        {stage === 'error' && (
          <View style={styles.errorCard}>
            <Text style={styles.errorTitle}>Check failed</Text>
            <Text style={styles.errorBody}>{error}</Text>
            <TouchableOpacity style={styles.btnSecondary} onPress={run}>
              <Text style={styles.btnSecondaryText}>Try again</Text>
            </TouchableOpacity>
          </View>
        )}

        {stage === 'done' && findings !== null && (
          <Results findings={findings} note={note} onRecheck={run} />
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function Results({ findings, note, onRecheck }: {
  findings: InteractionFinding[];
  note: string | null;
  onRecheck: () => void;
}) {
  return (
    <View style={{ gap: 10 }}>
      <View style={styles.resultsHeader}>
        <Text style={styles.resultsTitle}>
          {findings.length === 0
            ? 'No interactions found'
            : `${findings.length} potential interaction${findings.length === 1 ? '' : 's'}`}
        </Text>
        <TouchableOpacity onPress={onRecheck}>
          <Text style={styles.recheck}>Re-check</Text>
        </TouchableOpacity>
      </View>

      {findings.length === 0 && (
        <View style={styles.clearCard}>
          <Text style={styles.clearTitle}>Nothing flagged in the FDA label text.</Text>
          <Text style={styles.clearSub}>
            {note ?? 'Labels don\'t list every interaction — always confirm with a pharmacist.'}
          </Text>
        </View>
      )}

      {findings.map((f, i) => (
        <View key={i} style={styles.findingCard}>
          <View style={styles.findingHeader}>
            <Text style={styles.findingDrugs}>
              <Text style={styles.drugA}>{f.drug_a}</Text>
              <Text style={styles.plus}> + </Text>
              <Text style={styles.drugB}>{f.drug_b}</Text>
            </Text>
            <SeverityBadge severity={f.severity} />
          </View>
          <Text style={styles.findingSummary}>{f.summary}</Text>
          <Text style={styles.findingSource}>
            Grounded in the FDA label for {f.source_drug}.
          </Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#FBF9F3' },
  container: { padding: 20, gap: 14, paddingBottom: 40 },
  title: { fontSize: 26, fontWeight: '700', color: '#1C2321', letterSpacing: -0.4 },
  sub: { fontSize: 14, color: '#4B5553', lineHeight: 20 },
  center: { alignItems: 'center', padding: 24 },
  card: {
    backgroundColor: '#FFF',
    borderRadius: 20,
    padding: 20,
    gap: 12,
    shadowColor: '#2C3A2E',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 12,
    elevation: 3,
  },
  cardEyebrow: { fontSize: 11, color: '#8A9490', textTransform: 'uppercase', letterSpacing: 1 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { backgroundColor: '#F0F4F0', borderRadius: 999, paddingHorizontal: 12, paddingVertical: 6 },
  chipText: { fontSize: 13, color: '#3B6347', fontWeight: '500', textTransform: 'capitalize' },
  btnPrimary: { backgroundColor: '#5B8266', borderRadius: 14, paddingVertical: 14, alignItems: 'center' },
  btnPrimaryText: { color: '#FFF', fontSize: 15, fontWeight: '600' },
  btnSecondary: { backgroundColor: '#F0F4F0', borderRadius: 14, paddingVertical: 13, alignItems: 'center', borderWidth: 1, borderColor: '#E3ECE4' },
  btnSecondaryText: { color: '#3B6347', fontSize: 14, fontWeight: '600' },
  disabled: { opacity: 0.6 },
  emptyCard: {
    backgroundColor: '#FFF',
    borderRadius: 20,
    padding: 28,
    alignItems: 'center',
    gap: 8,
    shadowColor: '#2C3A2E',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 10,
    elevation: 2,
  },
  emptyTitle: { fontSize: 17, fontWeight: '600', color: '#1C2321', textAlign: 'center' },
  emptySub: { fontSize: 13, color: '#8A9490', textAlign: 'center', lineHeight: 18 },
  errorCard: { backgroundColor: '#FEF2F2', borderRadius: 16, padding: 20, gap: 8, borderWidth: 1, borderColor: '#FECACA' },
  errorTitle: { fontSize: 16, fontWeight: '600', color: '#991B1B' },
  errorBody: { fontSize: 14, color: '#7F1D1D', lineHeight: 20 },
  resultsHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  resultsTitle: { fontSize: 17, fontWeight: '600', color: '#1C2321', flex: 1 },
  recheck: { fontSize: 14, color: '#5B8266', fontWeight: '500' },
  clearCard: { backgroundColor: '#F0FDF4', borderRadius: 16, padding: 16, gap: 6, borderWidth: 1, borderColor: '#BBF7D0' },
  clearTitle: { fontSize: 15, fontWeight: '600', color: '#166534' },
  clearSub: { fontSize: 13, color: '#14532D', lineHeight: 18 },
  findingCard: {
    backgroundColor: '#FFF',
    borderRadius: 16,
    padding: 16,
    gap: 8,
    shadowColor: '#2C3A2E',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 2,
  },
  findingHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 },
  findingDrugs: { flex: 1, fontSize: 15, fontWeight: '600', textTransform: 'capitalize' },
  drugA: { color: '#1C2321' },
  plus: { color: '#8A9490' },
  drugB: { color: '#1C2321' },
  findingSummary: { fontSize: 14, color: '#4B5553', lineHeight: 20 },
  findingSource: { fontSize: 12, color: '#8A9490' },
});
