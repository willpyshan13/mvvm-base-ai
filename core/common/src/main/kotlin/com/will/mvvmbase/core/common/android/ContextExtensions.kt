package com.will.mvvmbase.core.common.android

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Copies plain text to the system clipboard. Returns false if the clipboard service is unavailable. */
fun Context.copyText(text: String, label: String = "Text"): Boolean {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    return true
}

/** Opens an HTTP or HTTPS URL in an installed browser. Invalid URLs and missing browsers return false. */
fun Context.openWebPage(url: String): Boolean {
    val uri = url.toHttpUriOrNull() ?: return false
    val intent = Intent(Intent.ACTION_VIEW, uri).withNewTaskIfNeeded(this)
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/** Shares plain text with apps that support Android's share intent. */
fun Context.shareText(text: String, chooserTitle: CharSequence = "Share"): Boolean {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(shareIntent, chooserTitle).withNewTaskIfNeeded(this)
    return try {
        startActivity(chooser)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun Intent.withNewTaskIfNeeded(context: Context): Intent = apply {
    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Returns a parsed URI only for absolute HTTP and HTTPS URLs with a host. */
fun String.toHttpUriOrNull(): Uri? {
    val candidate = trim()
    if (candidate.isEmpty()) return null

    val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
    val scheme = uri.scheme ?: return null
    val isHttp = scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
    return uri.takeIf { isHttp && !it.host.isNullOrBlank() }
}
