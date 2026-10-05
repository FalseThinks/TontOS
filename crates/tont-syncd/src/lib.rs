//! TontOS Radio Synchronization Daemon (tont-syncd)
//! Coalesces periodic background data traffic into synchronized power windows.

use tont_common::SyncWindowConfig;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SyncState {
    Sleeping { elapsed_in_interval: u64 },
    WindowOpen { elapsed_in_window: u64 },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SyncEvent {
    WindowOpened,
    WindowClosed,
    NoChange,
}

pub struct SyncScheduler {
    pub config: SyncWindowConfig,
    pub state: SyncState,
}

impl SyncScheduler {
    pub fn new(config: SyncWindowConfig) -> Self {
        Self {
            config,
            state: SyncState::Sleeping { elapsed_in_interval: 0 },
        }
    }

    /// Advance the internal clock by `seconds` and compute the state transition.
    pub fn tick(&mut self, seconds: u64) -> SyncEvent {
        match self.state {
            SyncState::Sleeping { elapsed_in_interval } => {
                let next = elapsed_in_interval + seconds;
                if next >= self.config.interval_secs {
                    self.state = SyncState::WindowOpen { elapsed_in_window: 0 };
                    SyncEvent::WindowOpened
                } else {
                    self.state = SyncState::Sleeping { elapsed_in_interval: next };
                    SyncEvent::NoChange
                }
            }
            SyncState::WindowOpen { elapsed_in_window } => {
                let next = elapsed_in_window + seconds;
                if next >= self.config.duration_secs {
                    self.state = SyncState::Sleeping { elapsed_in_interval: 0 };
                    SyncEvent::WindowClosed
                } else {
                    self.state = SyncState::WindowOpen { elapsed_in_window: next };
                    SyncEvent::NoChange
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sync_window_cycle() {
        let config = SyncWindowConfig {
            interval_secs: 10,
            duration_secs: 2,
        };
        let mut scheduler = SyncScheduler::new(config);

        assert_eq!(scheduler.tick(5), SyncEvent::NoChange);
        assert_eq!(scheduler.tick(5), SyncEvent::WindowOpened);
        assert_eq!(scheduler.tick(1), SyncEvent::NoChange);
        assert_eq!(scheduler.tick(1), SyncEvent::WindowClosed);
        assert_eq!(scheduler.tick(2), SyncEvent::NoChange);
    }
}
