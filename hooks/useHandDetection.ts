import { useState, useRef, useEffect, useCallback } from 'react';
import { Dimensions } from 'react-native';
import type { CameraView } from 'expo-camera';

export interface Landmark {
  x: number;
  y: number;
  z?: number;
  name?: string;
}

export interface HandDetectionResult {
  landmarks: Landmark[] | null;
  fingerCount: number;
  isModelLoaded: boolean;
  isReady: boolean;
}

// Count raised fingers from hand pose landmarks
export function countRaisedFingers(landmarks: Landmark[]): number {
  if (landmarks.length < 21) return 0;

  let count = 0;

  const wrist = landmarks[0];
  const indexMCP = landmarks[5];
  const thumbTip = landmarks[4];
  const thumbIP = landmarks[3];

  // Determine if right or left hand (mirrored front camera)
  // In mirrored front camera: if wrist.x > indexMCP.x = appears as right hand on screen
  const isRightHandOnScreen = wrist.x > indexMCP.x;

  // Thumb: check horizontal extension
  if (isRightHandOnScreen) {
    if (thumbTip.x > thumbIP.x) count++;
  } else {
    if (thumbTip.x < thumbIP.x) count++;
  }

  // Index finger: tip (8) above PIP (6)
  if (landmarks[8].y < landmarks[6].y) count++;
  // Middle finger: tip (12) above PIP (10)
  if (landmarks[12].y < landmarks[10].y) count++;
  // Ring finger: tip (16) above PIP (14)
  if (landmarks[16].y < landmarks[14].y) count++;
  // Pinky finger: tip (20) above PIP (18)
  if (landmarks[20].y < landmarks[18].y) count++;

  return count;
}

export function useHandDetection(
  cameraRef: React.RefObject<CameraView | null>
): HandDetectionResult {
  const [landmarks, setLandmarks] = useState<Landmark[] | null>(null);
  const [fingerCount, setFingerCount] = useState<number>(-1);
  const [isModelLoaded, setIsModelLoaded] = useState(false);
  const [isReady, setIsReady] = useState(false);

  const detectorRef = useRef<any>(null);
  const isProcessingRef = useRef(false);
  const mountedRef = useRef(true);
  const lastImgDims = useRef<{ width: number; height: number } | null>(null);

  // Initialize TensorFlow.js and hand-pose detector
  useEffect(() => {
    mountedRef.current = true;

    const initTF = async () => {
      try {
        const tf = await import('@tensorflow/tfjs');
        // Try react-native backend first, fall back to cpu
        try {
          await import('@tensorflow/tfjs-react-native');
          await tf.setBackend('rn-webgl');
        } catch {
          await tf.setBackend('cpu');
        }
        await tf.ready();

        const handPoseDetection = await import('@tensorflow-models/hand-pose-detection');
        const model = handPoseDetection.SupportedModels.MediaPipeHands;
        const detector = await handPoseDetection.createDetector(model, {
          runtime: 'tfjs' as any,
          modelType: 'lite',
          maxHands: 1,
        });

        detectorRef.current = detector;
        if (mountedRef.current) {
          setIsModelLoaded(true);
          setIsReady(true);
        }
      } catch (err) {
        console.warn('Hand detection init failed:', err);
        if (mountedRef.current) setIsReady(true); // Still mark ready to show camera
      }
    };

    initTF();

    return () => {
      mountedRef.current = false;
    };
  }, []);

  // Frame capture and processing loop
  const processFrame = useCallback(async () => {
    if (
      !cameraRef.current ||
      !detectorRef.current ||
      isProcessingRef.current ||
      !mountedRef.current
    ) return;

    isProcessingRef.current = true;

    try {
      const pic = await (cameraRef.current as any).takePictureAsync({
        base64: true,
        quality: 0.25,
        skipProcessing: true,
        fastMode: true,
      });

      if (!pic?.base64 || !mountedRef.current) return;

      const tf = await import('@tensorflow/tfjs');
      let decodeJpeg: ((data: Uint8Array, channels?: number) => any) | null = null;

      try {
        const rn = await import('@tensorflow/tfjs-react-native');
        decodeJpeg = rn.decodeJpeg;
      } catch {
        decodeJpeg = null;
      }

      let imageTensor: any = null;

      if (decodeJpeg) {
        // Convert base64 to Uint8Array
        const binaryStr = atob(pic.base64);
        const bytes = new Uint8Array(binaryStr.length);
        for (let i = 0; i < binaryStr.length; i++) {
          bytes[i] = binaryStr.charCodeAt(i);
        }
        imageTensor = decodeJpeg(bytes, 3);
      } else {
        return; // Can't process without decoder
      }

      if (!imageTensor) return;

      const imgW = pic.width ?? 640;
      const imgH = pic.height ?? 480;
      lastImgDims.current = { width: imgW, height: imgH };

      const hands = await detectorRef.current.estimateHands(imageTensor, {
        flipHorizontal: true, // Front camera is mirrored
      });

      tf.dispose(imageTensor);

      if (!mountedRef.current) return;

      if (hands && hands.length > 0) {
        const screen = Dimensions.get('window');
        const scaleX = screen.width / imgW;
        const scaleY = screen.height / imgH;

        const scaledLandmarks: Landmark[] = hands[0].keypoints.map((kp: any) => ({
          x: kp.x * scaleX,
          y: kp.y * scaleY,
          z: kp.z,
          name: kp.name,
        }));

        setLandmarks(scaledLandmarks);
        setFingerCount(countRaisedFingers(scaledLandmarks));
      } else {
        setLandmarks(null);
        setFingerCount(-1);
      }
    } catch (err) {
      // Silently ignore frame errors
    } finally {
      isProcessingRef.current = false;
    }
  }, [cameraRef]);

  // Start processing loop once model is ready
  useEffect(() => {
    if (!isModelLoaded) return;

    const interval = setInterval(processFrame, 200); // ~5fps
    return () => clearInterval(interval);
  }, [isModelLoaded, processFrame]);

  return { landmarks, fingerCount, isModelLoaded, isReady };
}
