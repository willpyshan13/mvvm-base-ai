package com.will.mvvmbase.core.designsystem.components

import com.will.mvvmbase.core.designsystem.R

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Requests one runtime permission, explains denial, and refreshes after returning from Settings. */
@Composable
fun AppPermissionGate(permission: String, rationale: String, content: @Composable () -> Unit) {
    val resources = LocalContext.current.resources
    val context = LocalContext.current
    val activity = context.permissionActivity()
    val owner = LocalLifecycleOwner.current
    var requested by rememberSaveable(permission) { mutableStateOf(false) }
    var granted by remember(permission) { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    var permanentlyDenied by rememberSaveable(permission) { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        permanentlyDenied = !result && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
    DisposableEffect(owner, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                if (granted) permanentlyDenied = false
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (granted) content() else Column {
        Text(rationale)
        TextButton(onClick = {
            if (permanentlyDenied && requested) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")))
            else { requested = true; launcher.launch(permission) }
        }) { Text(if (permanentlyDenied && requested) resources.getString(R.string.open_settings) else resources.getString(R.string.grant_permission)) }
    }
}

private tailrec fun Context.permissionActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.permissionActivity()
    else -> null
}
