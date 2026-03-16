package com.qianxu.copyimage

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import com.qianxu.copyimage.ClipboardUtil.writeImageUriToClipboard
import com.qianxu.copyimage.ToastUtil.showToast
import com.qianxu.copyimage.ui.theme.MainActivityTheme
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      val context = LocalContext.current

      // 权限请求处理器，用于请求读取图片的权限
      val requestPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted
          ->
          if (isGranted) {
            showToast(context, R.string.toast_permission_granted)
          } else {
            showToast(context, R.string.toast_permission_denied)
          }
        }

      // 状态管理
      var latestImageUri by remember { mutableStateOf<Uri?>(null) }
      var showImageDialog by remember { mutableStateOf(false) }

      MainActivityTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SettingsButton(
              text = stringResource(R.string.button_grant_read_image_permission),
              onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                  requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
              },
            )

            SettingsButton(
              text = stringResource(R.string.button_add_quick_settings_tile),
              onClick = { addQuickSettingsTile(context) },
            )

            SettingsButton(
              text = stringResource(R.string.button_test_read_image),
              onClick = {
                readLatestImage(
                  onResult = { latestImageUri = it },
                  onShowDialog = { showImageDialog = true },
                  onError = { showToast(context, R.string.toast_image_read_failed) },
                )
              },
            )

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
              Text(
                text = stringResource(R.string.main_usage_title),
                color = colorScheme.onSurface,
                style = typography.bodyLarge,
              )
              Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                Row {
                  Text(
                    "•",
                    color = colorScheme.onSurface,
                    style = typography.bodyMedium,
                    modifier = Modifier.padding(end = 4.dp),
                  )
                  Text(
                    text = stringResource(R.string.main_usage_item1),
                    color = colorScheme.onSurface,
                    style = typography.bodyMedium,
                  )
                }
                Row {
                  Text(
                    "•",
                    color = colorScheme.onSurface,
                    style = typography.bodyMedium,
                    modifier = Modifier.padding(end = 4.dp),
                  )
                  Text(
                    text = stringResource(R.string.main_usage_item2),
                    color = colorScheme.onSurface,
                    style = typography.bodyMedium,
                  )
                }
              }
              Spacer(modifier = Modifier.padding(16.dp))
              Text(
                text = stringResource(R.string.main_permission_hint),
                color = colorScheme.error,
                style = typography.bodySmall,
              )
            }
          }
        }

        if (showImageDialog && latestImageUri != null) {
          val imagePainter = rememberAsyncImagePainter(model = latestImageUri)

          DialogWithImage(
            painter = imagePainter,
            onDismissRequest = { showImageDialog = false },
            onConfirmation = {
              if (writeImageUriToClipboard(context, latestImageUri!!)) {
                showToast(context, R.string.toast_image_copied_success)
                showImageDialog = false
              } else {
                showToast(context, R.string.toast_image_copy_failed)
              }
            },
          )
        }
      }
    }
  }

  /**
   * 添加快速设置图块服务 注意：此功能仅在 Android 13 及以上版本可用
   *
   * @param context 上下文对象
   */
  private fun addQuickSettingsTile(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val statusBarManager = getSystemService(StatusBarManager::class.java)
      statusBarManager.requestAddTileService(
        ComponentName(context, CopyTileService::class.java),
        getString(R.string.tile_name),
        Icon.createWithResource(context, R.drawable.ic_tile),
        Executors.newSingleThreadExecutor(),
      ) { result ->
        val mainHandler = Handler(Looper.getMainLooper())
        when (result) {
          StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> {
            mainHandler.post { showToast(context, R.string.toast_tile_added_success) }
          }
          StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> {
            mainHandler.post { showToast(context, R.string.toast_tile_already_exists) }
          }
          else -> {
            mainHandler.post { showToast(context, R.string.toast_tile_add_failed) }
          }
        }
      }
    }
  }

  /**
   * 读取最新的图片并更新界面
   *
   * @param onResult 用于更新图片 Uri 的回调
   * @param onShowDialog 显示图片对话框的回调
   * @param onError 错误处理回调
   */
  private fun readLatestImage(
    onResult: (Uri?) -> Unit,
    onShowDialog: () -> Unit,
    onError: () -> Unit,
  ) {
    lifecycleScope.launch {
      val uri = MediaStoreUtil.getLatestImageUri(contentResolver)
      onResult(uri)
      if (uri != null) {
        onShowDialog()
      } else {
        onError()
      }
    }
  }

  /**
   * 设置按钮组件
   *
   * @param text 按钮显示的文本
   * @param onClick 点击按钮时执行的操作
   */
  @Composable
  fun SettingsButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
      Text(text = text)
    }
  }

  /**
   * 带图片的对话框组件
   *
   * @param painter 用于显示图片的绘制器
   * @param onConfirmation 确认按钮的回调
   * @param onDismissRequest 取消按钮的回调
   */
  @Composable
  fun DialogWithImage(painter: Painter, onConfirmation: () -> Unit, onDismissRequest: () -> Unit) {
    Dialog(onDismissRequest = { onDismissRequest() }) {
      Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Image(
            painter = painter,
            contentDescription = "Image",
            modifier =
              Modifier.fillMaxWidth()
                .fillMaxHeight(0.6f)
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 0.dp),
          )
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onDismissRequest() }) {
              Text(text = stringResource(R.string.button_cancel))
            }
            TextButton(onClick = { onConfirmation() }) {
              Text(text = stringResource(R.string.button_confirm))
            }
          }
        }
      }
    }
  }
}
