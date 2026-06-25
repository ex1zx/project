import React from 'react';
import { StyleSheet, ViewStyle, Dimensions } from 'react-native';
import Svg, { Line, Circle, G } from 'react-native-svg';
import type { Landmark } from '@/hooks/useHandDetection';
import { HAND_CONNECTIONS, HIGHLIGHT_INDICES } from '@/constants/handConnections';

interface HandOverlayProps {
  landmarks: Landmark[] | null;
  style?: ViewStyle | ViewStyle[];
}

const { width: SW, height: SH } = Dimensions.get('window');

const GREEN = '#00ff88';
const GREEN_DIM = '#00cc66';
const GREEN_GLOW = '#00ff8833';

export default function HandOverlay({ landmarks, style }: HandOverlayProps) {
  if (!landmarks || landmarks.length < 21) {
    return null;
  }

  return (
    <Svg style={[StyleSheet.absoluteFill, style]} width={SW} height={SH}>
      <G>
        {/* Glow lines (thicker, semi-transparent for glow effect) */}
        {HAND_CONNECTIONS.map(([from, to], i) => {
          const a = landmarks[from];
          const b = landmarks[to];
          if (!a || !b) return null;
          return (
            <Line
              key={`glow-${i}`}
              x1={a.x}
              y1={a.y}
              x2={b.x}
              y2={b.y}
              stroke={GREEN_GLOW}
              strokeWidth={10}
              strokeLinecap="round"
            />
          );
        })}

        {/* Main connection lines */}
        {HAND_CONNECTIONS.map(([from, to], i) => {
          const a = landmarks[from];
          const b = landmarks[to];
          if (!a || !b) return null;
          return (
            <Line
              key={`line-${i}`}
              x1={a.x}
              y1={a.y}
              x2={b.x}
              y2={b.y}
              stroke={GREEN_DIM}
              strokeWidth={2}
              strokeLinecap="round"
            />
          );
        })}

        {/* All landmark dots */}
        {landmarks.map((lm, i) => (
          <Circle
            key={`dot-${i}`}
            cx={lm.x}
            cy={lm.y}
            r={HIGHLIGHT_INDICES.includes(i) ? 5 : 3}
            fill={HIGHLIGHT_INDICES.includes(i) ? GREEN : GREEN_DIM}
            opacity={0.9}
          />
        ))}

        {/* Fingertip larger glow dots */}
        {[4, 8, 12, 16, 20].map((idx) => {
          const lm = landmarks[idx];
          if (!lm) return null;
          return (
            <G key={`tip-${idx}`}>
              <Circle cx={lm.x} cy={lm.y} r={12} fill={GREEN_GLOW} />
              <Circle cx={lm.x} cy={lm.y} r={6} fill={GREEN} opacity={0.95} />
            </G>
          );
        })}
      </G>
    </Svg>
  );
}
