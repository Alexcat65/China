package com.example.chinaprice.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chinaprice.MainViewModel
import com.example.chinaprice.model.ModelState
import com.example.chinaprice.model.OverlayItem
import com.example.chinaprice.model.RateInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun CameraScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!granted) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("Для работы нужен доступ к камере", color = Color.White, fontSize = 18.sp)
                Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 16.dp)
                ) { Text("Разрешить") }
            }
        }
        return
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var frozen by remember { mutableStateOf<Bitmap?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var showRateDialog by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewSize = it }
    ) {
        CameraPreview(
            previewView = previewView,
            torch = state.torch,
            onFrame = { blocks, w, h -> vm.onFrame(blocks, w, h) },
            modifier = Modifier.fillMaxSize()
        )

        frozen?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        OverlayLayer(state.items, state.imageWidth, state.imageHeight, viewSize)

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            ModelBanner(state.model, onRetry = vm::prepareModel, modifier = Modifier.weight(1f))
            RateChip(state.rate, state.rateLoading) { showRateDialog = true }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(onClick = vm::toggleTorch) {
                Text(if (state.torch) "Фонарик: вкл" else "Фонарик: выкл")
            }
            Button(onClick = {
                frozen = if (!state.paused) previewView.bitmap else null
                vm.togglePause()
            }) {
                Text(if (state.paused) "Продолжить" else "Пауза")
            }
        }
    }

    if (showRateDialog) {
        RateDialog(
            state.rate, state.rateLoading, state.rateError,
            onRefresh = vm::refreshRate,
            onAuto = vm::clearManualRate,
            onApply = vm::setManualRate,
            onDismiss = { showRateDialog = false }
        )
    }
}

@Composable
private fun OverlayLayer(items: List<OverlayItem>, imgW: Int, imgH: Int, viewSize: IntSize) {
    if (imgW <= 0 || imgH <= 0 || viewSize.width == 0 || viewSize.height == 0) return
    val density = LocalDensity.current
    // PreviewView.ScaleType.FILL_CENTER: масштаб по большей стороне + центрирование
    val scale = max(viewSize.width / imgW.toFloat(), viewSize.height / imgH.toFloat())
    val dx = (viewSize.width - imgW * scale) / 2f
    val dy = (viewSize.height - imgH * scale) / 2f
    val maxWidthPx = viewSize.width * 0.7f
    val maxWidth = with(density) { maxWidthPx.toDp() }
    val reservedBottomPx = with(density) { 90.dp.toPx() }

    items.forEach { item ->
        val x = (item.box.left * scale + dx).roundToInt()
            .coerceIn(0, (viewSize.width * 0.3f).toInt())
        val y = (item.box.top * scale + dy).roundToInt()
            .coerceIn(0, max(0, (viewSize.height - reservedBottomPx).toInt()))
        Surface(
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .offset { IntOffset(x, y) }
                .widthIn(max = maxWidth)
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(item.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                item.sub?.let { Text(it, color = Color(0xFFFFD54F), fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun ModelBanner(model: ModelState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when (model) {
        ModelState.Ready -> Box(modifier)
        ModelState.Checking, ModelState.Downloading -> Surface(
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(10.dp),
            modifier = modifier.padding(end = 8.dp)
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    if (model == ModelState.Checking) "  Проверка языковой модели…"
                    else "  Скачивание модели перевода (нужен интернет)…",
                    color = Color.White, fontSize = 13.sp
                )
            }
        }
        is ModelState.Error -> Surface(
            color = Color(0xCCB00020),
            shape = RoundedCornerShape(10.dp),
            modifier = modifier.padding(end = 8.dp)
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(model.message, color = Color.White, fontSize = 13.sp)
                TextButton(onClick = onRetry) { Text("Повторить", color = Color.White) }
            }
        }
    }
}

@Composable
private fun RateChip(rate: RateInfo?, loading: Boolean, onClick: () -> Unit) {
    Surface(
        color = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                if (rate != null) "1 ¥ = ${String.format(Locale.US, "%.2f", rate.value)} ₽" else "Курс: н/д",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
            Text(
                when {
                    loading -> "обновление…"
                    rate != null -> rate.source
                    else -> "нажмите"
                },
                color = Color(0xFFBDBDBD), fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun RateDialog(
    rate: RateInfo?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onAuto: () -> Unit,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember {
        mutableStateOf(rate?.let { String.format(Locale.US, "%.4f", it.value) } ?: "")
    }
    val updated = rate?.takeIf { it.updatedAt > 0 }?.let {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date(it.updatedAt))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Курс CNY → RUB") },
        text = {
            Column {
                Text(
                    buildString {
                        append("Источник: ").append(rate?.source ?: "нет данных")
                        if (updated != null) append("\nОбновлено: ").append(updated)
                        if (loading) append("\nОбновление…")
                        if (error != null) append("\n").append(error)
                    },
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Свой курс, ₽ за 1 ¥") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.padding(top = 12.dp)
                )
                Row {
                    TextButton(onClick = onRefresh) { Text("Обновить") }
                    TextButton(onClick = onAuto) { Text("Авто") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(text); onDismiss() }) { Text("Применить свой") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}
