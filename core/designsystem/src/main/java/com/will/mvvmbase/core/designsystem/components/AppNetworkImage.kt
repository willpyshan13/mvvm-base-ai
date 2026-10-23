package com.will.mvvmbase.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.will.mvvmbase.core.common.android.observeInternetConnectivity
import com.will.mvvmbase.core.designsystem.R

/** Cached public images, sized to layout constraints. Use a versioned URL when content changes. */
@Composable
fun AppNetworkImage(
    url: String?,
    description: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cacheEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val online by remember(context) { context.observeInternetConnectivity() }
        .collectAsStateWithLifecycle(initialValue = true)
    var attempt by remember(url) { mutableIntStateOf(0) }
    var failed by remember(url) { mutableStateOf(false) }
    var previousOnline by remember(url) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(online, url) {
        if (previousOnline == false && online && failed) {
            failed = false
            attempt++
        }
        previousOnline = online
    }
    val request = remember(context, url, attempt, cacheEnabled) {
        ImageRequest.Builder(context).data(url)
            .memoryCachePolicy(if (cacheEnabled) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .diskCachePolicy(if (cacheEnabled) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .build()
    }
    key(url, attempt, cacheEnabled) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = description,
            modifier = modifier,
            contentScale = contentScale,
            onLoading = { failed = false },
            onSuccess = { failed = false },
            onError = { failed = true },
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            },
            error = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { failed = false; attempt++ }) {
                        Text(stringResource(if (online) R.string.ui_appformcomponents_2 else R.string.image_offline_retry))
                    }
                }
            },
        )
    }
}
