# TontOS — AI Assistance & Human Authorship Provenance

**Project:** TontOS  
**Maintainer:** FalseThinks <paproken@protonmail.com>  
**Legal Framework:** Real Decreto Legislativo 1/1996 (LPI), Directiva 2009/24/CE, CJEU C-406/10 (*SAS Institute v World Programming*)  
**Status:** Active Provenance Register  

---

## 1. Authorship Doctrine & Human Creative Contribution

Under Spanish Intellectual Property Law (art. 96–97 LPI) and European Union law, computer programs are protected by copyright only if they are original in the sense that they are the author's own intellectual creation.

TontOS utilizes AI coding assistants as technical tools within an engineer-directed development workflow. AI assistants do not hold copyright; copyright vests in the human author (FalseThinks) through:
1. **System Architecture & Design**: Conceptualizing the digital-detox OS hierarchy, cgroups v2 resource bounds, direct boot isolation, volume HUD, and custom launcher mechanics.
2. **Technical Specifications & Requirements**: Defining exact state machines, protocol boundaries, and security constraints.
3. **Interactive Curation & Code Review**: Evaluating, refining, editing, refactoring, and rejecting AI suggestions.
4. **Integration & Automated Verification**: Wiring components into AOSP, writing unit/system test suites, and executing verification gates.

---

## 2. Component Authorship Classification

| Component / Layer | Role of Human Author (FalseThinks) | AI Tooling Role | IP Status |
|---|---|---|---|
| **System Architecture & ADRs** | Primary designer of all contracts, state machines, and threat models | None / Documentation formatting assistance | Human Original Creation |
| **Rust Daemons (`crates/*`)** | Core design, protocol definitions, cgroups logic, memory management | Implementation synthesis under strict specification | Human Author with AI Tool Assistance |
| **Launcher (`launcher_apk/*`)** | UI UX design, direct boot isolation, volume HUD, tactile UI architecture | Boilerplate view assembly and syntax refactoring | Human Author with AI Tool Assistance |
| **AOSP Patches (`patches/*`)** | Selection of platform hook points, overlayfs orchestration | Patch extraction and diff formatting | Third-Party Upstream (AOSP/Apache-2.0) |
| **Automated Tests (`tests/*`)** | Specification of invariants, test vectors, security boundaries | Test harness execution and assertion mapping | Human Original Creation |

---

## 3. Provenance Register Format

For each major subsystem and architectural iteration, FalseThinks maintains internal records tracking:
- **Date & Commit**: Git commit hash and commit message.
- **Architectural Intent**: Problem solved, constraints respected, alternatives considered.
- **Human Verification**: Tests executed, coverage achieved, and manual code review notes.
- **Upstream Verification**: Confirmation that no third-party copyrighted code was inadvertently ingested.
