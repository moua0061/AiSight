# AiSight – three-level phone vibration alerts (original partner version)

This is the original **MediaPipe / EfficientDet-Lite0** Android-only project, enhanced with proximity-dependent haptics. It does **not** use YOLO11, YOLO-World, or a Python server.

## Vibration patterns

| Estimated distance | Pattern | When it fires |
|---|---|---|
| Unknown or above 3.0 m | **One** 120 ms pulse | When the tracker confirms a new object |
| Above 1.5 m, up to 3.0 m | **Two** 110 ms pulses, separated by 130 ms | For a newly tracked object in this range, or on first escalation into this range |
| Up to 1.5 m | **Three** 220 ms pulses, separated by 110 ms | For a newly tracked object in this range, or on first escalation into this range |

- Each track alerts at discovery and when it first enters a **more urgent** level; it does not vibrate every video frame.
- If many tracks qualify on one frame, the highest-urgency level is played first.
- Global cooldown: **1.5 seconds** for detected and near alerts, **650 milliseconds** for close alerts. Multiple alerts may still occur over time as tracks appear or change.
- When a track disappears from the active list, its state is removed so it can alert again when recognized anew.
- Unknown, invalid, or unavailable distance triggers **only one detection pulse**. A cut-off bounding box alone is *not* proof of closeness.
- The phone's vibration settings and hardware can affect whether any alert is felt.

## Files changed

- `app/src/main/java/com/CEN4090c/aisight/DetectionHaptics.kt`: distance tiers, patterns, tracking, and cooldown.
- `app/src/main/java/com/CEN4090c/aisight/StreamViewModel.kt`: continues to call `DetectionHaptics` after each tracking update and reset it on shutdown (no further changes required).
- `app/src/main/AndroidManifest.xml` already includes `android.permission.VIBRATE`.

## How to test on a physical Android phone

1. Open `AiSight-main` in Android Studio, sync Gradle, and install on your Android phone.
2. Connect/register your Meta glasses and tap **Start stream**.
3. Look at an object with a known-size category (for example, an upright chair or person), and approach it in a controlled setting.
4. Compare the on-screen estimated distance with the vibration: greater than 3 m = one pulse; 1.5–3 m = two pulses; 1.5 m or less = three pulses.
5. Keep the object in frame to verify the phone does **not** vibrate every frame. Test an unknown-distance object to verify the single-pulse fallback.
6. Reset the stream and repeat. Use actual tape-measured distances to evaluate accuracy, not just the overlay.

## Safety and accuracy — read before demonstration

**These distance warnings are experimental, not reliable collision warnings.** `ObjectTracker.kt` uses an **uncalibrated placeholder focal length of 640 pixels**, assumes standard object heights, and may return no distance if the object class is unknown or the bounding box is cut off. A low or unknown alert **does not mean the scene is safe**, and distance estimates can be wrong in either direction. Do not rely on this feature for navigation or obstacle avoidance. Test under controlled supervision and calibrate/validate distances before considering it for an accessibility use case.

These thresholds are software defaults for a prototype; adjust `CLOSE_DISTANCE_M` and `NEAR_DISTANCE_M` in `DetectionHaptics.kt` after measurement-based validation.
