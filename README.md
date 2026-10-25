# X200T Camera Lab v0.6.2

Changes in this build:
- Continuous-picture autofocus is prioritized and selected by default.
- Shutter press triggers autofocus and captures after AF reports a lock, with a 650 ms fallback for fast shots.
- JPEG quality request set to 100.
- Preview stream selection prefers a higher-resolution size with an aspect ratio close to the viewfinder.

This source package has not been built or tested on a physical device in this environment. Actual photo quality and AF behavior depend on the Vivo camera HAL and supported stream combinations.
\n\n## v1.2.2 Popup camera menu\n\nThe landscape right-side utility row now shows one menu button instead of permanently visible flash, grid, and color-look buttons. The popup exposes Flash, Grid, Color Look, and Camera Settings. It is a PopupMenu overlay and does not add layout space or intentionally resize the preview.\n

## v1.2.3 Experimental video mode

- Removed the duplicate Camera Settings item from the popup; the existing Settings control outside the popup remains.
- Added Video/Photo mode and a 10-bit HDR preference entry to the popup.
- Added an HEVC MediaRecorder path, recording start/stop on the shutter, a 65:24 sensor crop request, and private app Movies output.
- Important: this is an experimental first video pass. The Android Camera2 session currently uses the device-supported MediaRecorder output size nearest to 65:24; the encoded file dimensions may therefore not be exactly 65:24. Main10 profile selection is requested where the API exposes it, but an explicit HDR dynamic-range session profile is not configured here, so 10-bit HDR is NOT guaranteed. Validate the output on the Vivo X200T and inspect codec/profile and dimensions before treating this as a production 65:24 10-bit recorder.


## v1.2.4 public video saving fix

Video recordings are now created through Android MediaStore in the public `Movies/Phantom Xpan` collection, rather than app-private `Android/data/.../files/Movies`. On Android 10+ the app marks the file pending while recording and publishes it when recording stops. This change addresses visibility in Gallery/file managers; the experimental video pipeline still requires an Android Studio build and device testing.


## v1.2.5 mode menu prototype

- Popup now separates Photo mode and Video mode options; the color filter remains common.
- Video resolution choices (720p/1080p/2K/4K) and frame-rate choices (24/30/60 FPS, plus 120 FPS at 1080p) are selectable and feed the MediaRecorder request.
- 10-bit is a requested encoder profile, not proof of HDR capture. Unsupported size/FPS/profile combinations can fail and must be validated on device.
- The photo megapixel selection menu is currently a placeholder and does not yet change the ImageReader capture size.
- This version does not yet produce guaranteed 65:24 encoded dimensions. The screenshot's 3840x1600 is 2.4:1, not 65:24 (2.708:1). Exact 65:24 requires a real crop/re-encode or a compatible camera/encoder output size and consistent preview framing.
- Not compiled or device-tested.


## v1.2.6 selector wiring fixes (not build-verified)
- Photo megapixel/resolution menu now opens the existing MP choice dialog rather than a placeholder toast.
- Video resolution and FPS choices are saved in preferences and restored when the app launches.
- Video capture now requests the selected supported AE FPS range and fails with a message if the selected FPS is not reported as supported.
- The video output still uses a standard MediaRecorder stream size; this patch does not crop/re-encode the saved file to exact 65:24 and does not implement Camera2 constrained-high-speed recording for true 120 FPS. Do not treat 120 FPS or 10-bit HDR as guaranteed without device testing.


## Phantom Xpan v1.3.0 — photo-only stabilisation

This release removes the experimental video UI and recording path. The app is focused on still photography.

Changes:
- Photo-only popup menu with working resolution, public storage location, flash, grid, color-look and camera-settings actions.
- Photo resolution picker opens the existing supported-size selection flow and remembers the chosen megapixel target.
- JPEG crop now selects dimensions as integer multiples of 65×24, making the saved pixel dimensions an exact 65:24 ratio when crop decoding succeeds.
- Added a live 65:24 crop guide with dimmed areas outside the saved frame.
- Save confirmation reports actual JPEG pixel dimensions and destination.
- Existing manual camera controls and color looks are retained.

Validation status:
- Source-level checks only. An Android Gradle build could not be completed in this environment because the Gradle distribution download is blocked by unavailable network access and the SDK path in local.properties points to the developer's Windows machine.
- Install and test on the Vivo X200T before treating this as a release build. Confirm the image dimensions shown in the save toast are exactly 65:24 and check all resolution choices, front/rear camera switching, flash and saving.


## v1.3.1 app branding
- Added the supplied Phantom Xpan camera logo as legacy and Android adaptive launcher icons.
- Set the launcher icon and round icon in AndroidManifest.xml.
- Kept the original logo art; generated padded launcher-safe variants for Android icon masks.
- Build and verify on device before release.


## v1.3.2 — zoom and framing consistency (source update; device verification required)
- Calibrates 1×/2×/5× presets using the camera HAL's reported focal-length transition when multiple focal lengths are exposed; falls back to standard ratios when they are not.
- Copies CONTROL_ZOOM_RATIO from preview to still capture on Android 11+ so the photo uses the same zoom as the viewfinder.
- Uses a uniform center-crop transform for the rotation-0 TextureView path instead of allowing a mismatched preview buffer to stretch.
- Crop guide and saved JPEG crop use landscape 65:24 or portrait 24:65 based on orientation.
- Build and test on the Vivo X200T; physical-lens transitions and crop alignment depend on the device Camera2 HAL.


## v1.3.3 zoom quality diagnostics
- Added Settings > Camera / Zoom Diagnostics, with camera IDs, facing, focal lengths, sensor/pixel-array sizes, max digital zoom, Android zoom-ratio range, largest JPEG output and logical multi-camera physical IDs where exposed.
- Zoom preset is clamped to the reported supported zoom range instead of silently assuming all devices support 5×.
- Zoom slider now uses logarithmic mapping, which makes fine control at low magnification more usable.
- Important: diagnostics cannot force the Vivo camera HAL to switch physical lenses. If Camera2 exposes only one usable stream or the HAL does not switch at a boundary, high zoom remains digital and quality will fall. Test diagnostics on the actual phone before promising optical 2×/5×.
- This source has not been compiled or device-tested in this environment.


## v1.3.3 compile fix (follow-up)
- Fixed the Camera2 physical-camera diagnostic to use `CameraCharacteristics.physicalCameraIds`, matching the API used elsewhere in this project.
- Compile verification was attempted, but this environment could not download the configured Gradle 9.6.0 distribution because network DNS access is unavailable. Run `gradlew.bat :app:compileDebugKotlin` locally to confirm.


## Lifecycle / immersive UI fix
- Reopens Camera2 after returning from the system photo picker when TextureView remains available and does not send a new surface-available callback.
- Applies immersive sticky mode to hide status/navigation bars, reapplying when the window regains focus.
- Build and test on a physical device before release; this source archive is not a prebuilt APK.


## Lens rail layout fix
- Added safe inset padding around the right-side control rail.
- Increased right rail width slightly and moved lens presets down from the top edge.
- Corrected the lens-row height mismatch: the parent previously measured 34dp while lens buttons were 38dp, which could clip them.
- This is source-only and needs an Android Studio build/device check.


## In-viewfinder zoom slider
- Moved 1× / 2× / 5× presets into a compact overlay at the bottom-center of the preview.
- Added a logarithmic continuous zoom slider that updates Camera2 zoom as it is dragged.
- Removed the old lens buttons from the right rail to prevent clipping.
- Needs Android Studio compilation and device verification.


## Pro controls overlay
- Added a PRO button in the top-left of the viewfinder.
- Opens a scrollable pro panel containing EV compensation, manual ISO, shutter speed, focus distance, focus mode, white balance, and AE/AWB locks.
- Uses the existing Camera2 control handlers and reports device-supported ranges.
- Source-only change; compile and verify on device.


## v1.3.4 viewfinder controls visibility fix
- Added stable tags to the viewfinder zoom overlay, Pro button, and Pro panel.
- Explicitly reasserts visibility and z-order of zoom overlay and Pro entry button after XPAN mode layout changes.
- Increments app version to 1.3.4.
- Source change only. The included APK, if any, is not rebuilt by this patch; rebuild in Android Studio.


## Patch 1.3.4 settings and capture quality update
- Consolidated the overflow popup into one Settings entry; all user options live in one settings dialog.
- Added volume up/down shutter option (Power button cannot be intercepted by ordinary Android apps).
- Added an optical stabilization toggle when Camera2 reports OIS support.
- Added maximum/standard JPEG quality selection and uses JPEG quality 100 for capture and crop output in maximum mode.
- Expanded camera diagnostics with sensor CFA, white/black levels, AWB/color-correction and stabilization capabilities.
- Still capture requests AUTO AWB when supported and FAST color correction when advertised.
- Added bit-depth explanation based on actual camera capabilities; 10-bit ISP processing does not imply 10-bit JPEG output. RAW/DNG requires a separate capture pipeline and is not falsely advertised as implemented.
- No additional gamma curve is applied to already encoded JPEGs; double gamma would harm color.
- Source changes are not build/device verified in this environment.


## v1.3.5 Camera2 compile correction
- Removed invalid `CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_MODES` references.
- Reports `COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES` separately from `CaptureRequest.COLOR_CORRECTION_MODE`.
- Explicitly requests standard `COLOR_CORRECTION_MODE_FAST`.
- Lint warnings about string resources, unused code and deprecated APIs are separate from compilation errors and still need cleanup.
- Source archive only; build not verified here.


## v1.3.8 UI fixes
- Removed the duplicate in-viewfinder zoom preset overlay; the right-side LENS rail remains the single visible preset control.
- Expanded the Pro controls ScrollView to the available viewfinder height so lower controls can be reached by scrolling instead of being cut off at a fixed 236dp.
- Source patch only; compile and device testing are still required.


## v1.3.9 UI update
- Single expandable zoom scope inside the viewfinder.
- Presets: 0.6×, 0.9×, 1×, 2×, 3×, 5×, 10×, 20×, 50×, 100×. Presets are clamped to the active camera's reported range; advertised zoom cannot guarantee equivalent optical detail.
- Added a ZEISS-inspired color look; this is a creative approximation, not an official ZEISS profile.
- Standard JPEG output remains 8-bit/channel; no fake high-bit-depth output is claimed.


## v1.4.2 UI refinement
- Removed the duplicate zoom preset button row; expanded zoom uses only the premium wheel.
- Centered the Phantom Xpan title and subtitle across the header, with LIVE anchored at the right.
- Fixed the ZoomWheelView Paint.strokeWidth Int-to-Float compile mismatch.


## v1.4.3 flash control fix
- Added the flash control directly to the landscape viewfinder because the legacy camera bar is hidden in Xpan mode. Tap cycles still-photo flash Off/Auto/On; long-press toggles torch.
- Flash operation still depends on the active camera exposing a flash unit and supported Camera2 AE modes.
