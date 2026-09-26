# ContrastGuard

100% offline, on-device Android malware detection. No cloud calls, no telemetry, no data leaving the phone. Combines a locally-run neural network with a rule-based security engine to classify apps as **Safe** or **Malware**, entirely on-device.

> **Status: active development, not production-ready.** See Known Limitations before relying on this for real security decisions.

## What it does

- Scans a single APK, or all installed apps in a batch, for malware indicators
- Runs a PyTorch Mobile neural network (trained on 120K labeled Android apps) on-device
- Cross-checks AI predictions against 250+ hand-written rules (dangerous permission combos, suspicious API calls, known-safe publisher whitelist) to cut false positives
- Real-time protection: monitors newly installed apps in the background, notifies on threats
- Full scan history stored locally in Room — nothing is ever uploaded

## How it works

```
APK → Feature Extraction (299 features) → Rule Engine (250+ checks)
    → Neural Network (359K params) → Consensus Decision Engine → Verdict
```

Trained on the AndroMD Balanced 120K dataset (60K benign / 60K malware), using a pretrained contrastive-learning encoder fine-tuned with a classifier head, exported to TorchScript Lite for CPU-only mobile inference.

## Tech stack

Kotlin, Coroutines, Room, Foreground Services · PyTorch Mobile Lite for inference · trained with PyTorch + scikit-learn on Kaggle GPU · no backend, no external database.

## Known limitations

- **Feature extraction is approximate, not exact** — the on-device extractor uses byte-pattern matching to approximate the original dataset's precise opcode-level counting, causing false positives on large legitimate apps. This is the top-priority fix.
- **No known-malware hash database** — detection relies entirely on the model + rules generalizing to unseen apps
- **Static analysis only** — no runtime behavior monitoring
- **Background reliability varies by OEM** — some manufacturers' battery managers may kill background monitoring unless the user exempts the app

## Building

```bash
git clone https://github.com/<your-username>/ContrastGuard.git
cd ContrastGuard
./gradlew assembleDebug
```

Requires `minSdk 26`, `targetSdk 34`.

## Disclaimer

Personal/research project, not a certified security product, not independently audited. Do not rely on it as your sole defense against malware.

## License

*(Add your chosen license here.)*
