package com.lyihub.archiveassistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lyihub.archiveassistant.domain.Topic
import com.lyihub.archiveassistant.state.TopicNameDialogMode
import com.lyihub.archiveassistant.ui.components.ArchiveDialog
import com.lyihub.archiveassistant.ui.components.ArchiveDialogAction

@Composable
fun TopicManagementDialogs(
  topics: List<Topic>,
  topicNameDialogMode: TopicNameDialogMode?,
  topicNameDialogTopicId: String?,
  topicValidationMessage: String?,
  deleteConfirmTopicId: String?,
  onConfirmCreateTopic: (String) -> Unit,
  onConfirmRenameTopic: (String) -> Unit,
  onConfirmDeleteTopic: () -> Unit,
  onCloseTopicNameDialog: () -> Unit,
  onCloseDeleteConfirmDialog: () -> Unit,
) {
  val renamingTopic = topics.firstOrNull { it.id == topicNameDialogTopicId }
  if (topicNameDialogMode != null) {
    TopicNameDialog(
      mode = topicNameDialogMode,
      initialName = renamingTopic?.title ?: "",
      validationMessage = topicValidationMessage,
      onConfirm = { name ->
        when (topicNameDialogMode) {
          TopicNameDialogMode.CREATE -> onConfirmCreateTopic(name)
          TopicNameDialogMode.RENAME -> onConfirmRenameTopic(name)
        }
      },
      onDismiss = onCloseTopicNameDialog,
    )
  }

  if (deleteConfirmTopicId != null) {
    val deletingTopic = topics.firstOrNull { it.id == deleteConfirmTopicId }
    DeleteTopicConfirmDialog(
      topicTitle = deletingTopic?.title ?: "",
      onConfirm = onConfirmDeleteTopic,
      onDismiss = onCloseDeleteConfirmDialog,
    )
  }
}

@Composable
private fun TopicNameDialog(
  mode: TopicNameDialogMode,
  initialName: String,
  validationMessage: String?,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var text by remember { mutableStateOf(initialName) }
  val title =
    when (mode) {
      TopicNameDialogMode.CREATE -> "建立新主题"
      TopicNameDialogMode.RENAME -> "重命名主题"
    }

  ArchiveDialog(
    title = title,
    onDismissRequest = onDismiss,
    testTag = "topic-name-dialog",
    actions = {
      ArchiveDialogAction(
        label = "取消",
        onClick = onDismiss,
        testTag = "topic-name-dialog-dismiss",
      )
      ArchiveDialogAction(
        label = "确定",
        onClick = { onConfirm(text) },
        primary = true,
        testTag = "topic-name-dialog-confirm",
      )
    },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("主题名称") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Text(
        text = "${text.length} / 20",
        style = MaterialTheme.typography.bodySmall,
        color = Color.Black.copy(alpha = 0.58f),
      )
      if (validationMessage != null) {
        Text(
          text = validationMessage,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun DeleteTopicConfirmDialog(
  topicTitle: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  ArchiveDialog(
    title = "确认删除",
    onDismissRequest = onDismiss,
    testTag = "delete-confirm-dialog",
    actions = {
      ArchiveDialogAction(
        label = "取消",
        onClick = onDismiss,
        testTag = "delete-confirm-dialog-dismiss",
      )
      ArchiveDialogAction(
        label = "删除",
        onClick = onConfirm,
        destructive = true,
        testTag = "delete-confirm-dialog-confirm",
      )
    },
  ) {
    Text(
      text = "确定要删除主题 \"$topicTitle\" 吗？该主题下的所有内容也将被删除。",
      style = MaterialTheme.typography.bodyMedium,
      color = Color.Black,
    )
  }
}
