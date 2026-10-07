/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.ui.theme

import androidx.compose.ui.graphics.Color

// AIS Terminal brand palette (AIS blue / teal / amber). Developed by DT.
val md_theme_light_primary = Color(0xFF1F5FAF)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFD6E3FF)
val md_theme_light_onPrimaryContainer = Color(0xFF001B3E)
val md_theme_light_secondary = Color(0xFF00897B)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFB2F1E8)
val md_theme_light_onSecondaryContainer = Color(0xFF00201C)
val md_theme_light_tertiary = Color(0xFF8B5000)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFFFDDB8)
val md_theme_light_onTertiaryContainer = Color(0xFF2C1600)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = Color(0xFFF6F8FC)
val md_theme_light_onBackground = Color(0xFF171C22)
val md_theme_light_surface = Color(0xFFF6F8FC)
val md_theme_light_onSurface = Color(0xFF171C22)
val md_theme_light_surfaceVariant = Color(0xFFDEE3EB)
val md_theme_light_onSurfaceVariant = Color(0xFF424750)
val md_theme_light_outline = Color(0xFF727780)
val md_theme_light_outlineVariant = Color(0xFFC2C7CF)
val md_theme_light_inverseOnSurface = Color(0xFFEEF1F7)
val md_theme_light_inverseSurface = Color(0xFF2C3137)
val md_theme_light_inversePrimary = Color(0xFFA8C8FF)
val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow = Color(0xFFF0F3F8)
val md_theme_light_surfaceContainer = Color(0xFFEAEEF4)
val md_theme_light_surfaceContainerHigh = Color(0xFFE4E8EF)
val md_theme_light_surfaceContainerHighest = Color(0xFFDEE3EA)
val md_theme_light_surfaceBright = Color(0xFFF6F8FC)
val md_theme_light_surfaceDim = Color(0xFFD6DAE1)

val md_theme_dark_primary = Color(0xFFA8C8FF)
val md_theme_dark_onPrimary = Color(0xFF003062)
val md_theme_dark_primaryContainer = Color(0xFF1F4A86)
val md_theme_dark_onPrimaryContainer = Color(0xFFD6E3FF)
val md_theme_dark_secondary = Color(0xFF5FDBCB)
val md_theme_dark_onSecondary = Color(0xFF003731)
val md_theme_dark_secondaryContainer = Color(0xFF005048)
val md_theme_dark_onSecondaryContainer = Color(0xFF7EF8E7)
val md_theme_dark_tertiary = Color(0xFFFFB95C)
val md_theme_dark_onTertiary = Color(0xFF4A2800)
val md_theme_dark_tertiaryContainer = Color(0xFF6A3C00)
val md_theme_dark_onTertiaryContainer = Color(0xFFFFDDB8)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF0E1218)
val md_theme_dark_onBackground = Color(0xFFDEE3EA)
val md_theme_dark_surface = Color(0xFF0E1218)
val md_theme_dark_onSurface = Color(0xFFDEE3EA)
val md_theme_dark_surfaceVariant = Color(0xFF424750)
val md_theme_dark_onSurfaceVariant = Color(0xFFC2C7CF)
val md_theme_dark_outline = Color(0xFF8C919A)
val md_theme_dark_outlineVariant = Color(0xFF424750)
val md_theme_dark_inverseOnSurface = Color(0xFF2C3137)
val md_theme_dark_inverseSurface = Color(0xFFDEE3EA)
val md_theme_dark_inversePrimary = Color(0xFF1F5FAF)
val md_theme_dark_surfaceContainerLowest = Color(0xFF090D12)
val md_theme_dark_surfaceContainerLow = Color(0xFF161B21)
val md_theme_dark_surfaceContainer = Color(0xFF1A1F26)
val md_theme_dark_surfaceContainerHigh = Color(0xFF242A31)
val md_theme_dark_surfaceContainerHighest = Color(0xFF2F353C)
val md_theme_dark_surfaceBright = Color(0xFF343A41)
val md_theme_dark_surfaceDim = Color(0xFF0E1218)

// Connection status colours used on the AIS home screen
val StatusLive = Color(0xFF22C55E)
val StatusIdle = Color(0xFF94A3B8)
val StatusError = Color(0xFFEF4444)

val KeyBackgroundNormal = Color(0x55F0F0F0)
val KeyBackgroundPressed = Color(0xAAA0A0FF)
val KeyBackgroundLayout = Color(0x55000000)
val KeyboardBackground = Color(0x55B0B0F0)

// Terminal-specific colors (used for overlays over terminal)
// These are independent of light/dark theme since terminal background is always dark
val TerminalOverlayBackground = Color(0x80000000) // Semi-transparent black
val TerminalOverlayText = Color(0xFFFFFFFF) // White
val TerminalOverlayTextSecondary = Color(0xB3FFFFFF) // White at 70% opacity
