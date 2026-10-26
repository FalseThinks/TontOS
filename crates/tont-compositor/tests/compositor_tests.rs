use tont_compositor::{DirectScanoutEvaluator, Rect, ScanoutDecision};

#[test]
fn test_skip_frame_when_no_damage() {
    let evaluator = DirectScanoutEvaluator::new(1080, 2400);
    let decision = evaluator.evaluate_frame(false, &[], false, Some(1), Some((1080, 2400)));
    assert_eq!(decision, ScanoutDecision::SkipFrame);
}

#[test]
fn test_direct_scanout_when_fullscreen_and_no_overlay() {
    let evaluator = DirectScanoutEvaluator::new(1080, 2400);
    let damage = [Rect {
        x: 0,
        y: 0,
        width: 1080,
        height: 2400,
    }];
    let decision = evaluator.evaluate_frame(true, &damage, false, Some(42), Some((1080, 2400)));
    assert_eq!(decision, ScanoutDecision::DirectScanout { surface_id: 42 });
}

#[test]
fn test_composite_damage_when_overlay_present() {
    let evaluator = DirectScanoutEvaluator::new(1080, 2400);
    let damage = [Rect {
        x: 0,
        y: 0,
        width: 1080,
        height: 50,
    }];
    let decision = evaluator.evaluate_frame(true, &damage, true, Some(42), Some((1080, 2400)));
    assert_eq!(
        decision,
        ScanoutDecision::CompositeDamage {
            damage_regions: damage.to_vec()
        }
    );
}
