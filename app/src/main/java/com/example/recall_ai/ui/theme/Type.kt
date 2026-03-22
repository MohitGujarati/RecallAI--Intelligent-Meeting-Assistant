package com.example.recall_ai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography system.
 *
 * Two families:
 *   RecallSans  — humanist sans for all body, labels, headings
 *                 (uses system default sans on device)
 *   RecallMono  — monospace for the live recording timer
 *                 (uses system default mono — Droid Sans Mono on Android)
 *
 * On production devices, add Google Fonts dependency and swap in:
 *   RecallSans → "DM Sans"      (clean, modern humanist)
 *   RecallMono → "DM Mono"      (distinctive tech timer feel)
 *
 * Add to build.gradle.kts:
 *   implementation("androidx.compose.ui:ui-text-google-fonts:<compose_version>")
 */

val RecallSans = FontFamily.Default
val RecallMono = FontFamily.Monospace

/**
 * Scale used across the app:
 *
 *   displayLarge   — never used (too big)
 *   displayMedium  — 57 sp recording timer (MM:SS:mm)
 *   headlineLarge  — screen titles
 *   titleLarge     — meeting card titles
 *   titleMedium    — section headers
 *   bodyLarge      — transcript text
 *   bodyMedium     — status labels, metadata
 *   labelSmall     — chips, timestamps, tiny metadata
 */
val RecallTypography = Typography(

    // ── Timer ──────────────────────────────────────────────────────────
    displayMedium = TextStyle(
        fontFamily = RecallMono,
        fontWeight = FontWeight.Light,
        fontSize   = 64.sp,
        lineHeight = 72.sp,
        letterSpacing = 4.sp
    ),

    // ── Screen title ───────────────────────────────────────────────────
    headlineLarge = TextStyle(
        fontFamily = RecallSans,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),

    headlineMedium = TextStyle(
        fontFamily = RecallSans,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),

    // ── Meeting card title ─────────────────────────────────────────────
    titleLarge = TextStyle(
        fontFamily = RecallSans,
        fontWeight = FontWeight.Medium,
        fontSize   = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),

    titleMedium = TextStyle(
        fontFamily = RecallSans,
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // ── Body / transcript ──────────────────────────────────────────────
    bodyLarge = TextStyle(
        fontFamily = RecallSans,
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),

    bodyMedium = TextStyle(
        fontFamily = RecallSans,
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),

    // ── Chips / timestamps ─────────────────────────────────────────────
    labelSmall = TextStyle(
        fontFamily = RecallSans,
        fontWeight = FontWeight.Medium,
        fontSize   = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),

    labelMedium = TextStyle(
        fontFamily = RecallSans,
        fontWeight = FontWeight.Medium,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )
)