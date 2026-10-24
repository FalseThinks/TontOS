use tont_telephony::{
    CallInfo, CallState, CellularNetworkStatus, TelephonyError, TelephonyManager,
};

#[test]
fn test_call_lifecycle() {
    let mut mgr = TelephonyManager::new();
    let call = mgr.handle_incoming_call("+123456789".to_string());
    assert_eq!(call.state, CallState::RingingInbound);

    let answered = mgr.answer_call(call.call_id).unwrap();
    assert_eq!(answered.state, CallState::Active);

    let terminated = mgr.terminate_call(call.call_id).unwrap();
    assert_eq!(terminated.state, CallState::Terminated);
}

#[test]
fn test_invalid_call_transition() {
    let mut mgr = TelephonyManager::new();
    let call = mgr.handle_incoming_call("+123456789".to_string());
    mgr.answer_call(call.call_id).unwrap();

    // Answering already active call should fail
    let err = mgr.answer_call(call.call_id).unwrap_err();
    match err {
        TelephonyError::InvalidTransition { action, state } => {
            assert_eq!(action, "answer");
            assert_eq!(state, CallState::Active);
        }
        _ => panic!("Unexpected error type"),
    }
}

#[test]
fn test_sms_reception() {
    let mut mgr = TelephonyManager::new();
    let sms = mgr.handle_incoming_sms("Mom".to_string(), "Call me back".to_string(), 1700000000);

    assert_eq!(sms.message_id, 1);
    assert_eq!(mgr.get_inbox().len(), 1);
    assert_eq!(mgr.get_inbox()[0].content, "Call me back");
}

#[test]
fn test_call_not_found_errors() {
    let mut mgr = TelephonyManager::new();
    let err_ans = mgr.answer_call(9999).unwrap_err();
    match err_ans {
        TelephonyError::CallNotFound(id) => assert_eq!(id, 9999),
        _ => panic!("Expected CallNotFound"),
    }

    let err_term = mgr.terminate_call(9999).unwrap_err();
    match err_term {
        TelephonyError::CallNotFound(id) => assert_eq!(id, 9999),
        _ => panic!("Expected CallNotFound"),
    }
}

#[test]
fn test_multiple_concurrent_calls() {
    let mut mgr = TelephonyManager::new();
    let call1 = mgr.handle_incoming_call("+111".to_string());
    let call2 = mgr.handle_incoming_call("+222".to_string());

    assert_eq!(call1.call_id, 1);
    assert_eq!(call2.call_id, 2);
    assert_ne!(call1.call_id, call2.call_id);

    let ans1 = mgr.answer_call(call1.call_id).unwrap();
    assert_eq!(ans1.state, CallState::Active);

    let term2 = mgr.terminate_call(call2.call_id).unwrap();
    assert_eq!(term2.state, CallState::Terminated);
}

#[test]
fn test_multiple_sms_fifo_order() {
    let mut mgr = TelephonyManager::new();
    mgr.handle_incoming_sms("Alice".to_string(), "Msg 1".to_string(), 100);
    mgr.handle_incoming_sms("Bob".to_string(), "Msg 2".to_string(), 101);
    mgr.handle_incoming_sms("Charlie".to_string(), "Msg 3".to_string(), 102);

    let inbox = mgr.get_inbox();
    assert_eq!(inbox.len(), 3);
    assert_eq!(inbox[0].sender, "Alice");
    assert_eq!(inbox[1].sender, "Bob");
    assert_eq!(inbox[2].sender, "Charlie");
}

#[test]
fn test_network_status_variants() {
    let mut mgr = TelephonyManager::new();
    assert_eq!(mgr.network_status, CellularNetworkStatus::NoSignal);

    mgr.network_status = CellularNetworkStatus::Registered {
        operator: "T-Mobile".to_string(),
        signal_percent: 85,
    };
    assert_eq!(
        mgr.network_status,
        CellularNetworkStatus::Registered {
            operator: "T-Mobile".to_string(),
            signal_percent: 85
        }
    );

    mgr.network_status = CellularNetworkStatus::Roaming {
        operator: "PartnerNet".to_string(),
        signal_percent: 40,
    };
    assert!(matches!(
        mgr.network_status,
        CellularNetworkStatus::Roaming { .. }
    ));
}

#[test]
fn test_call_info_serde_roundtrip() {
    let call = CallInfo {
        call_id: 42,
        remote_number: "+34600000000".to_string(),
        state: CallState::Active,
    };
    let json = serde_json::to_string(&call).unwrap();
    let de: CallInfo = serde_json::from_str(&json).unwrap();
    assert_eq!(call, de);
}
