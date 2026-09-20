# Pose Suggester

Android camera app that reads the scene you are pointing at, suggests a pose that suits it, and
draws a hand-drawn stick-figure guide over the live preview.

## Status

| Layer | State |
| --- | --- |
| 1. Camera (CameraX) | **Working** — preview, capture, analysis |
| 2. Perception (MediaPipe) | **Working** — Object Detection → `SceneState`. Pose Landmarker not wired yet |
| 3. Reasoning | **Working via templates** — heuristic selection from a 30-pose library. Both VLM paths stubbed and reporting unavailable |
| 4. Refinement | **Working** — plain Kotlin, no ML |
| 5. Render (sketch overlay) | **Working** — hand-drawn stick figure over the live preview |
| 6. Orchestration (MVVM) | **Working** |

## Build

Requires JDK 17 and Android SDK Platform 37.

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :app:assembleDebug
```

## Layout

```
app/src/main/java/com/posesuggester/
├── ui/              MainActivity, CameraViewModel, PoseOverlayView (sketch renderer)
├── camera/          CameraController — binds Preview + ImageCapture + ImageAnalysis
├── perception/      ObjectDetectionAnalyzer, SceneStateBuilder
├── domain/          PoseSuggestionSource (the seam), PoseSuggestionRepository
│   └── model/       Keypoint, BodyPart, PoseSuggestion, SceneState, DetectedObject
├── pose/            PoseTemplateLibrary (30 poses), PoseTemplateSelector
├── data/source/     TemplatePoseSuggestionSource (real), Cloud/OnDevice VLM (stubs)
├── refinement/      PoseRefiner
└── util/            ConnectivityMonitor
```

## Design notes

**The capture never contains the overlay.** `Preview` and `ImageCapture` are separate use cases;
the guide is a view drawn on top of `PreviewView`, so it is not in the image stream that
`ImageCapture` writes to disk.

**The preview letterboxes rather than centre-crops.** `PreviewView` uses `FIT_CENTER`, so the
whole analysed 4:3 frame stays on screen. Without this the overlay's 0..1 coordinates covered
frame area the user could not see, and poses were drawn partly off-screen.

**Coordinates are relative everywhere.** `Keypoint.x/y` and every `RectF` in `SceneState` are
normalised to 0..1 against the preview. Only the render layer converts to pixels, so nothing
upstream needs to know the display size.

**Detection cannot stall the camera.** `ImageAnalysis` runs on its own single-thread executor with
`STRATEGY_KEEP_ONLY_LATEST`, so a slow detector drops frames instead of building a queue.

**Both reasoning paths return the same type.** `PoseSuggestionSource` is the only seam the
repository knows about. Swapping in a real cloud endpoint or a real on-device VLM means replacing
one class each — nothing downstream changes.

## Why templates instead of VLM coordinates

The original design had the VLM emit `keypoints` directly. Coordinate regression is one of the
weakest things small vision models do — a 2B on-device model asked for seven normalised body-part
positions mostly returns plausible numbers that do not match the image.

So the library is the backbone instead. `PoseTemplateLibrary` holds 30 hand-authored poses, each
in its own 0..1 box with an `aspect` describing its true on-screen proportions.
`PoseTemplateSelector` scores them against `SceneState` — affordances (COCO labels mapped to
`SEAT`, `TABLE`, `GREENERY`, `VEHICLE`, plus `OPEN_GROUND` from the free regions), whether the
space fits the framing, how cluttered the foreground is, and a recency penalty so the same pose is
not served twice running — then scales the winner into the best open region.

This makes the app work fully offline with no model, and it is also the shape the VLM paths should
take: let the model *choose a template*, which is classification and something small models are
good at, then reuse this placement code.

## Swapping in the real reasoning layer

- **Cloud:** implement `CloudPoseSuggestionSource.suggestPose()` and pass the endpoint and key to
  its constructor in `CameraViewModel.Factory`. The 4s budget lives in the repository
  (`CLOUD_TIMEOUT_MS`), not in the source — just run to completion and let cancellation happen.
- **On-device:** implement `OnDevicePoseSuggestionSource` against the MediaPipe LLM Inference API
  (`tasks-genai`) or a llama.cpp JNI binding, and make `isAvailable()` report whether the model is
  loaded. No VLM can ship inside the APK — they run 1–4 GB, so it has to be downloaded to app
  storage on first run.

Both sit above `TemplatePoseSuggestionSource` in the repository chain, which needs no model and so
always answers. The app is never left without a pose.

## Next slices

1. Pose Landmarker into `SceneStateBuilder` to fill `SceneState.person`.
2. Real VLM behind one or both sources, selecting from the template library.
3. Trim the close-crop templates so the spine stops at chest height instead of running to the
   bottom of the frame.

## Models

| Model | Size | State |
| --- | --- | --- |
| `efficientdet_lite0.tflite` | 13.8 MB | Bundled in `assets/`, stored uncompressed so MediaPipe can mmap it |
| Pose Landmarker | ~5 MB | Not added |
| Any VLM | 1–4 GB | Not added, and cannot be bundled — runtime download only |
