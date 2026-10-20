//! TontOS Telephony Core (tont-telephony)
//! Manages voice calls, SMS dispatch, and modem status transitions.

use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum TelephonyError {
    #[error("Call not found: {0}")]
    CallNotFound(u64),
    #[error("Invalid state transition: cannot {action} in state {state:?}")]
    InvalidTransition {
        action: &'static str,
        state: CallState,
    },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum CallState {
    Dialing,
    RingingInbound,
    Active,
    Terminated,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CallInfo {
    pub call_id: u64,
    pub remote_number: String,
    pub state: CallState,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SmsMessage {
    pub message_id: u64,
    pub sender: String,
    pub content: String,
    pub timestamp_secs: u64,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum CellularNetworkStatus {
    NoSignal,
    Registered {
        operator: String,
        signal_percent: u8,
    },
    Roaming {
        operator: String,
        signal_percent: u8,
    },
}

pub struct TelephonyManager {
    next_call_id: u64,
    next_sms_id: u64,
    active_calls: HashMap<u64, CallInfo>,
    sms_inbox: Vec<SmsMessage>,
    pub network_status: CellularNetworkStatus,
}

impl Default for TelephonyManager {
    fn default() -> Self {
        Self {
            next_call_id: 1,
            next_sms_id: 1,
            active_calls: HashMap::new(),
            sms_inbox: Vec::new(),
            network_status: CellularNetworkStatus::NoSignal,
        }
    }
}

impl TelephonyManager {
    pub fn new() -> Self {
        Self::default()
    }

    /// Notify that an inbound call has arrived (e.g. from ModemManager or RIL).
    pub fn handle_incoming_call(&mut self, remote_number: String) -> CallInfo {
        let call_id = self.next_call_id;
        self.next_call_id += 1;

        let call = CallInfo {
            call_id,
            remote_number,
            state: CallState::RingingInbound,
        };
        self.active_calls.insert(call_id, call.clone());
        call
    }

    /// Answer an incoming ringing call.
    pub fn answer_call(&mut self, call_id: u64) -> Result<CallInfo, TelephonyError> {
        let call = self
            .active_calls
            .get_mut(&call_id)
            .ok_or(TelephonyError::CallNotFound(call_id))?;

        if call.state != CallState::RingingInbound {
            return Err(TelephonyError::InvalidTransition {
                action: "answer",
                state: call.state,
            });
        }

        call.state = CallState::Active;
        Ok(call.clone())
    }

    /// Hang up or decline a call.
    pub fn terminate_call(&mut self, call_id: u64) -> Result<CallInfo, TelephonyError> {
        let call = self
            .active_calls
            .get_mut(&call_id)
            .ok_or(TelephonyError::CallNotFound(call_id))?;

        call.state = CallState::Terminated;
        let info = call.clone();
        self.active_calls.remove(&call_id);
        Ok(info)
    }

    /// Store a received SMS into the local store.
    pub fn handle_incoming_sms(
        &mut self,
        sender: String,
        content: String,
        timestamp_secs: u64,
    ) -> SmsMessage {
        let message_id = self.next_sms_id;
        self.next_sms_id += 1;

        let sms = SmsMessage {
            message_id,
            sender,
            content,
            timestamp_secs,
        };
        self.sms_inbox.push(sms.clone());
        sms
    }

    pub fn get_inbox(&self) -> &[SmsMessage] {
        &self.sms_inbox
    }
}

