# Contributing to TontOS

Thank you for your interest in contributing to TontOS! We welcome contributions, bug fixes, hardware adaptations, and improvements from the open-source community.

---

## 1. Open Source & Forking Policy

TontOS is dual-licensed under **Apache License 2.0** and **MIT License**.

- You are **free to fork**, modify, redistribute, and build upon TontOS.
- **Attribution Obligation:** If you fork this repository or distribute a derivative ROM/launcher, you **must retain the original copyright notice** and attribute the original author:
  > **"Based on TontOS by FalseThinks <paproken@protonmail.com>"**
  (See [`NOTICE`](NOTICE) and [`LICENSE`](LICENSE) for legal terms).

---

## 2. Getting Started

### Fork & Clone
1. Fork the repository on GitHub: `https://github.com/FalseThinks/TontOS/fork`.
2. Clone your fork locally:
   ```bash
   git clone git@github.com:<your-username>/TontOS.git
   cd TontOS
   git remote add upstream git@github.com:FalseThinks/TontOS.git
   ```

### Prerequisites
- **Rust Toolchain:** Stable with `clippy` and `rustfmt`.
- **Java:** OpenJDK 17 (`javac 17`).
- **Android SDK:** Platform 34 and Build-Tools 36.0.0.
- **Docker:** Required for full containerized AOSP compilation (`./bin/docker_build_rom.sh`).

---

## 3. Development & Verification Workflow

Before submitting a Pull Request, run the local test suite:

```bash
# 1. Rust workspace formatting, linter, and unit tests
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace

# 2. Launcher compilation and APK signature
./bin/build_launcher_apk.sh

# 3. Shell script hygiene & JSON syntax
bash -n bin/*.sh
jq empty platform/manifests/pinned.json tests/ui/package.json
```

---

## 4. Coding Standards

### Rust Daemons (`crates/`)
- **Zero `unsafe`:** `forbid(unsafe_code)` is strictly enforced across all 14 crates.
- **Streaming over Buffering:** Use streaming hashing (`sha2`) and chunked I/O; never buffer unbounded payloads into memory.
- **Error Handling:** Use `thiserror` for library crates and proper error propagation; never `unwrap()` or `panic!` in daemon code.

### Android Launcher (`launcher_apk/`)
- **Typography:** Enforce `Typeface.MONOSPACE` across 100% of text views, buttons, and dialogs.
- **Security:** Never call `Runtime.getRuntime().exec` directly with raw strings. Always use [`SafeCommandExecutor`](launcher_apk/src/org/tontos/launcher/SafeCommandExecutor.java) with parameterized string arrays.
- **Zero Allocations on Hot Paths:** Avoid allocations (such as formatters, Drawables, or layouts) inside recurring loops, clock updates, or scroll callbacks.

---

## 5. Submitting a Pull Request

1. Create a feature branch branched from `development` (never from `main`):
   ```bash
   git checkout -b feat/my-improvement upstream/development
   ```
2. Make atomic, well-tested commits following Conventional Commits (`feat: ...`, `fix: ...`, `docs: ...`).
3. Push to your fork and open a Pull Request targeting the **`development`** branch.
4. Ensure all CI quality gates pass on GitHub Actions.
5. Every Pull Request requires review and explicit approval from `@FalseThinks` before merging into `development`.
6. Merges from `development` into `main` are restricted exclusively to `@FalseThinks`. Pushing to `main` automatically triggers official version tagging and GitHub Release generation.

Thank you for helping build a calmer, more intentional mobile operating system!
