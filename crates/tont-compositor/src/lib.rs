//! TontOS Minimal Wayland Compositor Core (tont-compositor)
//! Evaluates Wayland scanout decisions and damage regions.

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Rect {
    pub x: i32,
    pub y: i32,
    pub width: u32,
    pub height: u32,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ScanoutDecision {
    /// Zero changes on screen: Panel enters Self-Refresh (PSR) at 0Hz.
    SkipFrame,
    /// Fullscreen surface bypasses 3D GPU completely (Direct KMS Scanout).
    DirectScanout { surface_id: u64 },
    /// Composite only the damaged sub-rectangles onto the framebuffer.
    CompositeDamage { damage_regions: Vec<Rect> },
}

pub struct DirectScanoutEvaluator {
    pub display_width: u32,
    pub display_height: u32,
}

impl DirectScanoutEvaluator {
    pub fn new(display_width: u32, display_height: u32) -> Self {
        Self {
            display_width,
            display_height,
        }
    }

    /// Determine the lowest-power rendering path for the current frame.
    pub fn evaluate_frame(
        &self,
        has_damage: bool,
        damage_rects: &[Rect],
        has_active_overlay: bool,
        focused_surface_id: Option<u64>,
        focused_surface_size: Option<(u32, u32)>,
    ) -> ScanoutDecision {
        if !has_damage || damage_rects.is_empty() {
            return ScanoutDecision::SkipFrame;
        }

        // Direct scanout candidate: fullscreen surface with no overlapping layers/status bars
        if !has_active_overlay
            && let (Some(id), Some((w, h))) = (focused_surface_id, focused_surface_size)
            && w == self.display_width
            && h == self.display_height
        {
            return ScanoutDecision::DirectScanout { surface_id: id };
        }

        ScanoutDecision::CompositeDamage {
            damage_regions: damage_rects.to_vec(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_skip_frame_when_no_damage() {
        let evaluator = DirectScanoutEvaluator::new(1080, 2400);
        let decision = evaluator.evaluate_frame(false, &[], false, None, None);
        assert_eq!(decision, ScanoutDecision::SkipFrame);
    }

    #[test]
    fn test_direct_scanout_fullscreen() {
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
    fn test_composite_damage_with_overlay() {
        let evaluator = DirectScanoutEvaluator::new(1080, 2400);
        let damage = [Rect {
            x: 10,
            y: 10,
            width: 100,
            height: 100,
        }];
        let decision = evaluator.evaluate_frame(true, &damage, true, Some(42), Some((1080, 2400)));
        assert_eq!(
            decision,
            ScanoutDecision::CompositeDamage {
                damage_regions: damage.to_vec()
            }
        );
    }
}
