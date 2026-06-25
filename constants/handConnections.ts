// MediaPipe Hand Landmark indices
// 0: Wrist
// 1-4: Thumb (CMC, MCP, IP, TIP)
// 5-8: Index (MCP, PIP, DIP, TIP)
// 9-12: Middle (MCP, PIP, DIP, TIP)
// 13-16: Ring (MCP, PIP, DIP, TIP)
// 17-20: Pinky (MCP, PIP, DIP, TIP)

export const HAND_CONNECTIONS: [number, number][] = [
  // Thumb
  [0, 1], [1, 2], [2, 3], [3, 4],
  // Index finger
  [0, 5], [5, 6], [6, 7], [7, 8],
  // Middle finger
  [0, 9], [9, 10], [10, 11], [11, 12],
  // Ring finger
  [0, 13], [13, 14], [14, 15], [15, 16],
  // Pinky finger
  [0, 17], [17, 18], [18, 19], [19, 20],
  // Palm base connections
  [5, 9], [9, 13], [13, 17],
];

// Fingertip landmark indices
export const FINGERTIP_INDICES = [4, 8, 12, 16, 20];

// PIP (Proximal Interphalangeal) joint indices for each finger
export const FINGER_PIP_INDICES = [3, 6, 10, 14, 18];

// MCP (Metacarpophalangeal) joint indices
export const FINGER_MCP_INDICES = [2, 5, 9, 13, 17];

// Highlight joints (knuckles + tips)
export const HIGHLIGHT_INDICES = [0, 4, 8, 12, 16, 20, 5, 9, 13, 17];
