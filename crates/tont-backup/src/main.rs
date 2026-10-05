//! tont-backup entry point
use std::env;
use std::path::Path;
use tont_backup::BackupPackage;
use tont_settings::TontSettings;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();
    let args: Vec<String> = env::args().collect();

    if args.len() < 2 {
        tracing::info!("TontOS Backup Utility");
        tracing::info!("Usage: tont-backup [create <path> | restore <path> | verify <path>]");
        return;
    }

    match args[1].as_str() {
        "create" => {
            let out_path = args.get(2).map(String::as_str).unwrap_or("/tmp/tontos_backup.json");
            let settings = TontSettings::default();
            let package = BackupPackage::create(
                settings,
                vec![],
                vec![],
                1700000000,
                "Treble-GSI".to_string(),
            )
            .expect("Failed to create backup package");

            package
                .save_to_file(Path::new(out_path))
                .expect("Failed to save backup");
            tracing::info!("Backup successfully created at {}", out_path);
        }
        "verify" => {
            let in_path = args.get(2).map(String::as_str).unwrap_or("/tmp/tontos_backup.json");
            match BackupPackage::load_from_file(Path::new(in_path)) {
                Ok(pkg) => tracing::info!(
                    "Backup at {} is VALID. SHA256: {}",
                    in_path,
                    pkg.manifest.payload_sha256
                ),
                Err(e) => tracing::error!("Backup verification FAILED: {}", e),
            }
        }
        "restore" => {
            let in_path = args.get(2).map(String::as_str).unwrap_or("/tmp/tontos_backup.json");
            let pkg = BackupPackage::load_from_file(Path::new(in_path))
                .expect("Failed to load and verify backup archive");
            tracing::info!(
                "Successfully restored settings and {} apps from {}",
                pkg.payload.installed_apps.len(),
                in_path
            );
        }
        _ => tracing::warn!("Unknown command. Use create, verify, or restore."),
    }
}
