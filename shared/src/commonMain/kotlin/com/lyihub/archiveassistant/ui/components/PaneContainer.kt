package com.lyihub.archiveassistant.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PaneContainer(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxSize(),
    color = Color.Transparent,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      content()
    }
  }
}

@Composable
fun PaneDivider() {
  HorizontalDivider(
    color = MaterialTheme.colorScheme.outlineVariant,
    thickness = 1.dp,
  )
}

@Composable
fun PaneContentPadding(content: @Composable () -> Unit) {
  Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
    content()
  }
}
