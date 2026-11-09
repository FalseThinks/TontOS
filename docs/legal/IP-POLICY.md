# TontOS — Intellectual Property & Licensing Policy

**Maintainer:** FalseThinks <paproken@protonmail.com>  
**Version:** 1.0  
**Effective Date:** 2026-11-01  

---

## 1. Multi-Tier IP Architecture

TontOS operates under a clear, tiered intellectual property structure designed to protect original work while maintaining full compliance with upstream third-party ecosystems:

```text
                      TontOS Operating System
                                 │
                 ┌───────────────┴───────────────┐
                 ▼                               ▼
       Original TontOS Materials        Third-Party Upstream
                 │                               │
        TontOS-NCSL-1.0                          │
       (Source-Available NC)                     ▼
                 │                      Retain Original Licenses
        ┌────────┴────────┐             ├── AOSP: Apache-2.0
        ▼                 ▼             ├── Linux Kernel: GPLv2
  Non-Commercial      Commercial        ├── Rust Crates: MIT / Apache-2.0
  (Community / Free)  (License Req.)    └── FOSS Apps: On-Demand Fetch
```

## 2. Protection Against Unauthorized Exploitation

This policy establishes strict defenses against:
1. **Commercial Appropriation**: OEMs, vendors, and device sellers cannot bundle TontOS without a paid commercial license (`LICENSE-COMMERCIAL.md`).
2. **Attribution Stripping**: All forks, mirrors, and derived ROM distributions must visibly display credit to FalseThinks (`NOTICE`).
3. **Counterfeit Official Distributions**: Third-party modifications may not use the official TontOS logo or claim to be an official release (`TRADEMARKS.md`).
4. **Binary Bundling Risk**: Third-party FOSS APKs are fetched on-demand during device setup, eliminating binary redistribution obligations from base ROM images (`docs/legal/FOSS-DOWNLOAD-POLICY.md`).

## 3. Contributor Policy & Inbound Rights

Until a formal Contributor License Agreement (CLA) or Developer Certificate of Origin (DCO) is enacted:
- External contributors must warrant that submitted pull requests represent their own original work or properly attributed FOSS.
- Contributions to original TontOS crates and launcher code are accepted under TontOS-NCSL-1.0.
