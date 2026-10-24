//! TontOS Backup & Restore Core (tont-backup)
//! Manages system settings, SMS archives, and app states with SHA-256 cryptographic verification.

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::fs;
use std::path::Path;
use thiserror::Error;
use tont_settings::TontSettings;
use tont_shell::AppEntry;
use tont_telephony::SmsMessage;

#[derive(Debug, Error)]
pub enum BackupError {
    #[error("I/O error during backup: {0}")]
    Io(#[from] std::io::Error),
    #[error("JSON serialization error: {0}")]
    Json(#[from] serde_json::Error),
    #[error("Integrity check failed: payload checksum mismatch (tampered or corrupt backup)")]
    IntegrityFailure,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct BackupPayload {
    pub settings: TontSettings,
    pub sms_history: Vec<SmsMessage>,
    pub installed_apps: Vec<AppEntry>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct BackupManifest {
    pub version: u32,
    pub created_at_secs: u64,
    pub device_target: String,
    pub payload_sha256: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct BackupPackage {
    pub manifest: BackupManifest,
    pub payload: BackupPayload,
}

impl BackupPackage {
    /// Calculate SHA-256 hex string of the serialized payload.
    pub fn compute_sha256(payload: &BackupPayload) -> Result<String, BackupError> {
        let json = serde_json::to_vec(payload)?;
        let mut hasher = Sha256::new();
        hasher.update(&json);
        let hash = hasher.finalize();
        let hex = hash.iter().map(|b| format!("{b:02x}")).collect::<String>();
        Ok(hex)
    }

    /// Create a new verified backup package.
    pub fn create(
        settings: TontSettings,
        sms_history: Vec<SmsMessage>,
        installed_apps: Vec<AppEntry>,
        timestamp_secs: u64,
        device_target: String,
    ) -> Result<Self, BackupError> {
        let payload = BackupPayload {
            settings,
            sms_history,
            installed_apps,
        };

        let checksum = Self::compute_sha256(&payload)?;
        let manifest = BackupManifest {
            version: 1,
            created_at_secs: timestamp_secs,
            device_target,
            payload_sha256: checksum,
        };

        Ok(Self { manifest, payload })
    }

    /// Verify cryptographic integrity of the archive.
    pub fn verify_integrity(&self) -> bool {
        match Self::compute_sha256(&self.payload) {
            Ok(calc) => calc == self.manifest.payload_sha256,
            Err(_) => false,
        }
    }

    /// Write backup package to target file.
    pub fn save_to_file(&self, path: &Path) -> Result<(), BackupError> {
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent)?;
        }
        let json = serde_json::to_string_pretty(self)?;
        fs::write(path, json)?;
        Ok(())
    }

    /// Read backup package from file and verify cryptographic checksum.
    pub fn load_from_file(path: &Path) -> Result<Self, BackupError> {
        let content = fs::read_to_string(path)?;
        let package: BackupPackage = serde_json::from_str(&content)?;

        if !package.verify_integrity() {
            return Err(BackupError::IntegrityFailure);
        }

        Ok(package)
    }
}
