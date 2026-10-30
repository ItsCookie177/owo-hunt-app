package ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

@Composable
fun isInDarkTheme(): Boolean = isSystemInDarkTheme()
