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
