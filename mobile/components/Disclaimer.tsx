import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

export function Disclaimer() {
  return (
    <View style={styles.wrap}>
      <Text style={styles.icon}>⚠️</Text>
      <Text style={styles.text}>
        <Text style={styles.bold}>Not medical advice.</Text> PillID can be wrong. Always confirm
        with the original packaging, a pharmacist, or your doctor.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 8,
    backgroundColor: '#FFFBEB',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#FCD34D',
    padding: 12,
  },
  icon: { fontSize: 14, lineHeight: 20 },
  text: { flex: 1, fontSize: 13, lineHeight: 18, color: '#78350F' },
  bold: { fontWeight: '600' },
});
