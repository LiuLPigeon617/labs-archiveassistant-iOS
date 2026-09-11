package com.lyihub.archiveassistant.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun ActionButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  testTag: String? = null,
  icon: ImageVector? = null,
  enabled: Boolean = true,
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = if (testTag != null) modifier.testTag(testTag) else modifier,
    colors =
      ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
      ),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
  ) {
    if (icon != null) {
      Icon(imageVector = icon, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(text = label, style = MaterialTheme.typography.labelLarge)
  }
}

@Composable
fun TextActionButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  testTag: String? = null,
  icon: ImageVector? = null,
  contentColor: Color = MaterialTheme.colorScheme.primary,
  enabled: Boolean = true,
) {
  androidx.compose.material3.TextButton(
    onClick = onClick,
    enabled = enabled,
    modifier = if (testTag != null) modifier.testTag(testTag) else modifier,
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
      )
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = contentColor,
    )
  }
}
