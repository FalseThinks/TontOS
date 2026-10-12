//! TontOS Minimal Wayland Compositor Core (tont-compositor)
//! Implements direct scanout evaluation and damage-driven zero-overhead rendering.

use thiserror::Error;

#[derive(Debug, Error)]
pub enum CompositorError {
    #[error("Initialization error: {0}")]
    Init(String),
    #[error("Display server error: {0}")]
    Server(String),
}

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
        damage_rects: Vec<Rect>,
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
            damage_regions: damage_rects,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_skip_frame_when_no_damage() {
        let evaluator = DirectScanoutEvaluator::new(1080, 2400);
        let decision = evaluator.evaluate_frame(false, vec![], false, Some(1), Some((1080, 2400)));
        assert_eq!(decision, ScanoutDecision::SkipFrame);
    }

    #[test]
    fn test_direct_scanout_when_fullscreen_and_no_overlay() {
        let evaluator = DirectScanoutEvaluator::new(1080, 2400);
        let damage = vec![Rect {
            x: 0,
            y: 0,
            width: 1080,
            height: 2400,
        }];
        let decision = evaluator.evaluate_frame(true, damage, false, Some(42), Some((1080, 2400)));
        assert_eq!(decision, ScanoutDecision::DirectScanout { surface_id: 42 });
    }

    #[test]
    fn test_composite_damage_when_overlay_present() {
        let evaluator = DirectScanoutEvaluator::new(1080, 2400);
        let damage = vec![Rect {
            x: 0,
            y: 0,
            width: 1080,
            height: 50,
        }];
        let decision =
            evaluator.evaluate_frame(true, damage.clone(), true, Some(42), Some((1080, 2400)));
        assert_eq!(
            decision,
            ScanoutDecision::CompositeDamage {
                damage_regions: damage
            }
        );
    }
}
