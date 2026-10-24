use std::time::{Duration, Instant};
use tont_init::reap_available_children;

#[test]
fn reaps_all_exited_children_in_one_pass() {
    let child_pids: Vec<libc::pid_t> = (0..3)
        .map(|_| {
            let pid = unsafe { libc::fork() };
            assert!(pid >= 0, "fork failed: {}", std::io::Error::last_os_error());
            if pid == 0 {
                unsafe { libc::_exit(0) };
            }
            pid
        })
        .collect();

    for pid in &child_pids {
        wait_until_exited(*pid);
    }

    let mut reaped_pids = reap_available_children().expect("reaping children should succeed");
    reaped_pids.sort_unstable();
    let mut expected_pids = child_pids;
    expected_pids.sort_unstable();
    assert_eq!(reaped_pids, expected_pids);
}

fn wait_until_exited(pid: libc::pid_t) {
    let deadline = Instant::now() + Duration::from_secs(5);

    loop {
        let mut info = unsafe { std::mem::zeroed::<libc::siginfo_t>() };
        let result = unsafe {
            libc::waitid(
                libc::P_PID,
                pid as libc::id_t,
                &mut info,
                libc::WEXITED | libc::WNOHANG | libc::WNOWAIT,
            )
        };
        assert_eq!(
            result,
            0,
            "waitid failed: {}",
            std::io::Error::last_os_error()
        );
        if unsafe { info.si_pid() } == pid {
            return;
        }
        assert!(
            Instant::now() < deadline,
            "child {pid} did not exit in time"
        );
        std::thread::sleep(Duration::from_millis(1));
    }
}
