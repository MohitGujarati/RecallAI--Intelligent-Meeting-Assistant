package com.example.recall_ai.ui.theme

import androidx.compose.ui.graphics.Color

// ── Base surfaces ────────────────────────────────────────────────────
val ColorBackground     = Color(0xFFF6F7F8)   // bg-background-light
val ColorBackgroundDark = Color(0xFF111B21)   // bg-background-dark
val ColorSurface        = Color(0xFFFFFFFF)   // white card surfaces
val ColorSurfaceVariant = Color(0xFFF1F3F5)   // slightly tinted grey for chips/sections

// ── Primary accent — deep navy ───────────────────────────────────────
val ColorNavy           = Color(0xFF0D3E5E)   // primary accent (#0d3e5e)
val ColorNavyLight      = Color(0x330D3E5E)   // primary/20
val ColorNavyGradientEnd= Color(0xFFFFFFFF)   // gradient to white

// ── Text ──────────────────────────────────────────────────────────────
val ColorOnBackground   = Color(0xFF1A1A2E)   // near-black primary text
val ColorOnSurfaceDim   = Color(0xFF6B7280)   // text-slate-500
val ColorTextSlate400   = Color(0xFF94A3B8)   // text-slate-400
val ColorTextSlate900   = Color(0xFF0F172A)   // text-slate-900

// ── Accent — recording state ──────────────────────────────────────────
val ColorRecordRed      = Color(0xFFEF4444)   // active recording dot
val ColorRecordRedDim   = Color(0x22EF4444)   // pulse ring outer

// ── Accent — status states ────────────────────────────────────────────
val ColorDone           = Color(0xFF22C55E)   // completed / success green
val ColorProcessing     = Color(0xFF3B82F6)   // in-progress blue
val ColorWarning        = Color(0xFFF59E0B)   // warnings, amber
val ColorError          = Color(0xFFEF4444)   // errors, failures

// ── Dividers & borders ────────────────────────────────────────────────
val ColorBorder         = Color(0xFFE5E7EB)   // subtle card borders

// ── Scrim ─────────────────────────────────────────────────────────────
val ColorScrim          = Color(0x66000000)   // semi-transparent black overlay

// ── Dashboard specific ────────────────────────────────────────────────
val ColorGradientStart  = Color(0x190D3E5E)   // primary/10
val ColorGradientEnd    = Color(0xFFFFFFFF)
val ColorOrangeIconBg   = Color(0xFFFFEDD5)   // bg-orange-100
val ColorOrangeIcon     = Color(0xFFF97316)   // text-orange-500
val ColorMintTint       = Color(0xFFE8F5E9)   // soft green tint
val ColorPeachTint      = Color(0xFFFFF3E0)   // soft peach/orange tint
val ColorBlueTint       = Color(0xFFE3F2FD)   // soft blue tint