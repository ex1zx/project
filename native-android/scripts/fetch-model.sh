#!/usr/bin/env bash
set -euo pipefail

mkdir -p app/src/main/assets
curl --fail --location --retry 3 \
  "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task" \
  --output app/src/main/assets/hand_landmarker.task
test -s app/src/main/assets/hand_landmarker.task
echo "Downloaded local hand landmark model."