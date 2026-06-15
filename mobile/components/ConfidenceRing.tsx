import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Svg, { Circle } from 'react-native-svg';

export function ConfidenceRing({ value }: { value: number }) {
  const size = 64;
  const r = 26;
  const cx = size / 2;
  const circ = 2 * Math.PI * r;
  const pct = Math.max(0, Math.min(100, value));
  const dash = (pct / 100) * circ;
  const stroke = pct >= 70 ? '#5B8266' : pct >= 40 ? '#9A4F1E' : '#8F362A';
  const textColor = pct >= 70 ? '#3B6347' : pct >= 40 ? '#7C3D14' : '#7F1D1D';

  return (
    <View style={styles.wrap}>
      <Svg width={size} height={size} style={{ transform: [{ rotate: '-90deg' }] }}>
        <Circle cx={cx} cy={cx} r={r} fill="none" stroke="#E3ECE4" strokeWidth={6} />
        <Circle
          cx={cx}
          cy={cx}
          r={r}
          fill="none"
          stroke={stroke}
          strokeWidth={6}
          strokeLinecap="round"
          strokeDasharray={`${dash} ${circ}`}
        />
      </Svg>
      <View style={styles.label}>
        <Text style={[styles.pct, { color: textColor }]}>{value}%</Text>
      </View>
      <Text style={styles.caption}>confidence</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { alignItems: 'center' },
  label: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 20,
    justifyContent: 'center',
    alignItems: 'center',
  },
  pct: { fontSize: 14, fontWeight: '700' },
  caption: { fontSize: 9, fontWeight: '600', color: '#8A9490', letterSpacing: 1, marginTop: 2, textTransform: 'uppercase' },
});
