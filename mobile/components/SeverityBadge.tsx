import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import type { Severity } from '@/lib/types';

const MAP = {
  minor: { bg: '#F0FDF4', text: '#166534', label: 'Minor' },
  moderate: { bg: '#FFF7ED', text: '#9A3412', label: 'Moderate' },
  major: { bg: '#FEF2F2', text: '#991B1B', label: 'Major' },
};

export function severityRank(s: Severity) {
  return s === 'major' ? 2 : s === 'moderate' ? 1 : 0;
}

export function SeverityBadge({ severity }: { severity: Severity }) {
  const { bg, text, label } = MAP[severity] ?? MAP.minor;
  return (
    <View style={[styles.chip, { backgroundColor: bg }]}>
      <Text style={[styles.label, { color: text }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  chip: {
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 3,
  },
  label: {
    fontSize: 12,
    fontWeight: '600',
  },
});
