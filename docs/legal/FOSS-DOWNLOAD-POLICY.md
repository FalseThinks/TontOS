# TontOS — FOSS Applications On-Demand Delivery Policy

**Project:** TontOS  
**Maintainer:** FalseThinks <paproken@protonmail.com>  
**Status:** Adopted Architectural Policy  

---

## 1. Principle & Objective

To eliminate third-party binary redistribution liability and maintain clean, independent base system builds, TontOS establishes an **On-Demand First-Boot Delivery Model**:

1. **Clean Base Architecture**: Base TontOS release images (`system.img` / `super.img`) do not bundle third-party application APKs by default.
2. **First-Boot & Catalog Selector**: During setup or from the catalog manager, users choose which curated applications to download.
3. **Cryptographic Checksum Verification**: Applications are fetched directly from official release channels and verified against pinned SHA-256 hashes defined in `apps/catalog.json`.
4. **Third-Party Licensing Independence**: Each application retains its own upstream open-source license. Integrating or recommending an application does not alter its original license.

---

## 2. Complete 17-Application Catalog

| Application | Package Identifier | Version | Upstream Repository | License | Checksum (SHA-256) |
|---|---|---|---|---|---|
| Fossify Phone | `org.fossify.phone` | 1.11.1 | https://github.com/FossifyOrg/Phone | GPL-3.0-or-later | `d1ad08467034e5138a6e68f03b6e150f0bb010dfcbf490ff3d2bd4822b755728` |
| Fossify Messages | `org.fossify.messages` | 1.9.1 | https://github.com/FossifyOrg/Messages | GPL-3.0-or-later | `95f226c7929adc2699cdb2bc6ead29a704bddb8f55bd92d9b4ecf74bb604f1c6` |
| Fossify Contacts | `org.fossify.contacts` | 1.6.0 | https://github.com/FossifyOrg/Contacts | GPL-3.0-or-later | `6a89be96b5ec39f5e2b11bd61dc7031f4b88261b324ab13af5743fbc62f0cddb` |
| Fossify Gallery | `org.fossify.gallery` | 1.13.1 | https://github.com/FossifyOrg/Gallery | GPL-3.0-or-later | `ae7e699599e81f70e2b82626bb1dfa883fe096cd938387133e123c116e5641d9` |
| Fossify Camera | `org.fossify.camera` | 1.5.0 | https://github.com/FossifyOrg/Camera | GPL-3.0-or-later | `116004fd8743f9e47bf25eb9c6049eb77d3f19d59506c3f47513f584c6138435` |
| Fossify File Manager | `org.fossify.filemanager` | 1.6.1 | https://github.com/FossifyOrg/File-Manager | GPL-3.0-or-later | `9e97d2faf55fb2702386c3dc51e38a6b073781016b125701d3ab668419a70008` |
| Fossify Music Player | `org.fossify.musicplayer` | 1.8.1 | https://github.com/FossifyOrg/Music-Player | GPL-3.0-or-later | `b5d0ce367544df1ac162adf71d642878c5b963a304ca0ddf8f8556f07bbba79b` |
| FairEmail | `eu.faircode.email` | 1.2338 | https://github.com/M66B/FairEmail | GPL-3.0-only | `666b9f4ada53f69199355f1ccc2bed73e01fa23786fc0189cf81cc51932dc5a9` |
| FOSS Browser | `de.baumann.browser` | 8.4 | https://github.com/scoute-dich/browser | GPL-3.0-or-later | `3168ee449e259aa6fee932c1fbd4aba5a12e8fb71dfc0eb0732393b15cebc6c9` |
| HeliBoard | `helium314.keyboard` | 4.2 | https://github.com/Helium314/HeliBoard | GPL-3.0-or-later | `69b2b2fb349934371b1ed2d3569541cc0bda987f8aaf5eb5f5b157961f76de86` |
| Organic Maps | `app.organicmaps` | 2026.09.29 | https://github.com/organicmaps/organicmaps | Apache-2.0 | `654a736d43317084f13560281fe7013f869800610e9d6036b6789d9c9e07f8bc` |
| Breezy Weather | `org.breezyweather` | 6.2.2 | https://github.com/breezy-weather/breezy-weather | GPL-3.0-or-later | `1369d817d426498715f8ccb6750baff19dadd78e85f707088e9bca43977570c0` |
| PDF Viewer Plus | `me.zhanghai.android.pdfviewer` | 1.3.1 | https://github.com/zhanghai/PdfViewerPlus | Apache-2.0 | `9d7b4e9e421c6ff1b312a0f8bfd9a5b3a32dc51bc73295db2839958ecbe0dfeb` |
| OpenDocument Reader | `at.tomtasche.reader.foss` | 4.22.1 | https://github.com/opengapps/opendocument-reader | GPL-3.0-or-later | `965c52508d9a49c6ac926be63587be875e157ddf76efe4290efbcc24925b96ae` |
| NewPipe | `org.schabi.newpipe` | 0.29.1 | https://github.com/TeamNewPipe/NewPipe | GPL-3.0-or-later | `18447bfb1e06d113edc88df93f471827280de06f6f3d4dc42f56f28b9c1bab79` |
| BitChat | `com.bitchat.droid` | 2.0.3 | https://github.com/bitchat-app/bitchat-android | GPL-3.0-or-later | `1ee83d51f73e0f08c0938873399fad3ca741bedd47e0421bf43c950b5436fe7c` |
| Aurora Store | `com.aurora.store` | 4.8.4 | https://gitlab.com/AuroraOSS/AuroraStore | GPL-3.0-or-later | `fd9c75d90d0f4a7c132b9b4a5a2cf1992a45e03b8d8ff988b7dcfbc0db2c4d11` |

---

## 3. Fallback Alternatives

If a user unchecks a recommended utility, TontOS does not force installation. Where applicable, default AOSP equivalents (e.g., `com.android.dialer`, `com.android.messaging`, `com.android.gallery3d`) or user-supplied packages provide baseline functionality.
