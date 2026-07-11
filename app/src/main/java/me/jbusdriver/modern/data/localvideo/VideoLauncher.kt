package me.jbusdriver.modern.data.localvideo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.LocalVideo
import androidx.core.net.toUri

/**
 * 用系统播放器打开本地视频。借 SAF tree 的持久化读权限向播放器授予读权限。
 */
fun launchLocalVideo(context: Context, video: LocalVideo) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(video.uri.toUri(), video.mime ?: "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.play_local_video))
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_video_player, Toast.LENGTH_SHORT).show()
    }
}
