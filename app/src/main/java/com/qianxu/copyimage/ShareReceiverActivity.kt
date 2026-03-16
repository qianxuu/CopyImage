package com.qianxu.copyimage

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.content.FileProvider.getUriForFile
import androidx.core.content.IntentCompat
import com.qianxu.copyimage.ClipboardUtil.writeImageUriToClipboard
import com.qianxu.copyimage.ToastUtil.showToast
import java.io.File

class ShareReceiverActivity : Activity() {

  companion object {
    private const val TAG = "ShareReceiverActivity"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val receivedUri: Uri? =
      IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        ?: intent.data

    if (receivedUri == null) {
      showToast(this, R.string.toast_need_image)
      finish()
      return
    }

    Log.d(TAG, "收到分享: $receivedUri")

    try {
      val receivedImageFile = File(cacheDir, "received_image.png")
      contentResolver.openInputStream(receivedUri)?.use { inputStream ->
        java.io.FileOutputStream(receivedImageFile).use { outputStream ->
          inputStream.copyTo(outputStream)
        }
      }
      val cacheFileUri =
        getUriForFile(this, getString(R.string.file_provider_authority), receivedImageFile)

      Log.d(TAG, "缓存文件: $cacheFileUri")

      if (writeImageUriToClipboard(this, cacheFileUri)) {
        showToast(this, R.string.toast_image_copied_success)
      } else {
        showToast(this, R.string.toast_image_copy_failed)
      }
    } catch (e: Exception) {
      Log.e(TAG, e.message, e)
      showToast(this, R.string.toast_image_read_failed)
    } finally {
      finish()
    }
  }
}
