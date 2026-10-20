use tont_shell::{AppCategory, AppEntry, AppRegistry, ShellState};

#[test]
fn test_app_registry_default() {
    let registry = AppRegistry::default();
    let apps = registry.list_apps();
    assert!(apps.iter().any(|a| a.id == "com.aurora.store"));
    assert!(apps.iter().any(|a| a.id == "org.fossify.phone"));
    assert!(apps.iter().any(|a| a.id == "org.fossify.contacts"));
    assert!(apps.iter().any(|a| a.id == "org.fossify.camera"));
    assert!(apps.iter().any(|a| a.id == "org.fossify.gallery"));
    assert!(apps.iter().any(|a| a.id == "org.fossify.musicplayer"));
    assert!(apps.iter().any(|a| a.id == "org.fossify.filemanager"));
    assert!(apps.iter().any(|a| a.id == "org.breezyweather"));
    assert!(apps.iter().any(|a| a.id == "app.organicmaps"));
    assert!(apps.iter().any(|a| a.id == "de.baumann.browser"));
    assert!(apps.iter().any(|a| a.id == "eu.faircode.email"));
    assert!(apps.iter().any(|a| a.id == "org.tontos.settings"));
    assert_eq!(apps.len(), 13);
}

#[test]
fn test_install_arbitrary_app() {
    let mut registry = AppRegistry::default();
    registry.install_app(AppEntry {
        id: "com.anyapp.custom".to_string(),
        display_name: "Custom User App".to_string(),
        category: AppCategory::Utility,
    });

    let apps = registry.list_apps();
    assert!(apps.iter().any(|a| a.id == "com.anyapp.custom"));
}

#[test]
fn test_shell_state_receives_all_installed_apps() {
    let registry = AppRegistry::default();
    let state = ShellState::new("12:00".to_string(), None, &registry);
    assert_eq!(state.available_apps.len(), registry.list_apps().len());
}
