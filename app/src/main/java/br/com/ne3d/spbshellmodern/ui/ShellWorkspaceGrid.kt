package br.com.ne3d.spbshellmodern.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import br.com.ne3d.spbshellmodern.model.*

@Composable
fun ShellWorkspaceGrid(
    items: List<ShellItem>, apps: List<AppItem>, editing: Boolean, interactive: Boolean,
    draggingId: String?, onStart: (ShellItem, Offset) -> Unit,
    onDrag: (Offset) -> Unit, onDrop: () -> Unit, onCancel: () -> Unit,
    onBounds: (String, Rect) -> Unit, onOpenFolder: (ShellItem) -> Unit,
    onLaunch: (AppItem) -> Unit, widget: @Composable (ShellItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Adicione atalhos e widgets pelo botão +", color = Color.LightGray)
        }
    } else LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize().testTag("home-items"),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items(items, key = { it.id }, span = { GridItemSpan(if (it.appPackage != null || it.presentation == WidgetPresentation.ICON) 1 else if (it.presentation == WidgetPresentation.COMPACT) 2 else 4) }) { item ->
            var bounds by remember { mutableStateOf(Rect.Zero) }
            val start by rememberUpdatedState(onStart)
            val drag by rememberUpdatedState(onDrag)
            val drop by rememberUpdatedState(onDrop)
            val cancel by rememberUpdatedState(onCancel)
            val height = when {
                item.appPackage != null || item.presentation == WidgetPresentation.ICON -> 76.dp
                item.type == PanelType.FOLDER && item.presentation == WidgetPresentation.ROW -> 96.dp
                item.type == PanelType.FOLDER -> 180.dp
                item.presentation == WidgetPresentation.COMPACT -> 220.dp
                item.presentation == WidgetPresentation.ROW -> 190.dp
                item.presentation == WidgetPresentation.FULL_PANEL -> 420.dp
                else -> 310.dp
            }
            Box(Modifier.fillMaxWidth().height(height).testTag("item-${item.id}")
                .onGloballyPositioned { bounds = it.boundsInRoot(); onBounds(item.id, bounds) }
                .then(if (editing) Modifier.border(1.dp, Color.White.copy(alpha = .30f)) else Modifier)
                .background(if (draggingId == item.id) Color.White.copy(alpha = .15f) else Color.Transparent)
                .pointerInput(item.id, interactive || draggingId == item.id) {
                    if (interactive || draggingId == item.id) detectDragGesturesAfterLongPress(
                        onDragStart = { start(item, bounds.topLeft + it) },
                        onDragEnd = { drop() }, onDragCancel = { cancel() },
                        onDrag = { change, amount -> change.consume(); drag(amount) })
                }) {
                when {
                    item.appPackage != null -> {
                        val app = apps.firstOrNull { it.packageName == item.appPackage && it.className == item.appClass }
                        ShellAppIcon(app, item.title, interactive && !editing, onLaunch)
                    }
                    item.type == PanelType.FOLDER -> ShellFolder(item, apps, interactive && !editing, { onOpenFolder(item) }, onLaunch)
                    item.presentation == WidgetPresentation.ICON -> Column(Modifier.fillMaxSize()
                        .clickable(enabled = interactive && !editing) { onOpenFolder(item) }, horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text("▣", color = Color(0xFFE4D4AE), fontSize = 28.sp)
                        Text(item.title.ifBlank { panelTemplate(item.type).title }, fontSize = 11.sp, maxLines = 2)
                    }
                    else -> widget(item)
                }
            }
        }
    }
}

@Composable
fun ShellAppIcon(app: AppItem?, title: String, enabled: Boolean, onLaunch: (AppItem) -> Unit) {
    Column(Modifier.fillMaxSize().clickable(enabled = enabled && app != null) { app?.let(onLaunch) }.padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val icon = remember(app?.packageName, app?.className) { app?.icon?.toBitmap(96, 96)?.asImageBitmap() }
        if (icon != null) Image(icon, null, Modifier.size(38.dp))
        else Text("◇", color = Color.LightGray, fontSize = 26.sp)
        Text(title.ifBlank { app?.label ?: "App indisponível" }, color = Color.White, fontSize = 11.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ShellFolder(item: ShellItem, apps: List<AppItem>, enabled: Boolean, open: () -> Unit, onLaunch: (AppItem) -> Unit) {
    val limit = when (item.presentation) { WidgetPresentation.ICON -> 0; WidgetPresentation.ROW -> 3; else -> 7 }
    Column(Modifier.fillMaxSize().background(Color(0x77505048)).padding(4.dp)) {
        Text(item.title.ifBlank { "Pasta" }, Modifier.clickable(enabled = enabled, onClick = open), color = Color.White, fontSize = 12.sp)
        if (limit == 0) {
            Text("▦", Modifier.fillMaxSize().clickable(enabled = enabled, onClick = open), fontSize = 30.sp)
        } else LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize()) {
            items(item.children.take(limit), key = { it.id }) { child ->
                Box(Modifier.height(62.dp)) {
                    ShellAppIcon(apps.firstOrNull { it.packageName == child.appPackage && it.className == child.appClass }, child.title, enabled, onLaunch)
                }
            }
            item { Box(Modifier.height(62.dp).fillMaxWidth().clickable(enabled = enabled, onClick = open), contentAlignment = Alignment.Center) { Text("▦", fontSize = 26.sp) } }
        }
    }
}

@Composable
fun SpbAppsList(apps: List<AppItem>, panels: List<LauncherPanel>, onLaunch: (AppItem) -> Unit,
                onAdd: (AppItem) -> Unit, onLocate: (String) -> Unit, interactive: Boolean) {
    var query by remember { mutableStateOf("") }
    fun contains(items: List<ShellItem>, app: AppItem): Boolean = items.any {
        (it.appPackage == app.packageName && it.appClass == app.className) || contains(it.children, app)
    }
    Column(Modifier.fillMaxSize()) {
        if (interactive) OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("apps-search"),
            singleLine = true, label = { Text("Buscar aplicativo") })
        LazyColumn(Modifier.weight(1f)) {
            items(apps.filter { query.isBlank() || it.label.contains(query, true) }, key = { it.packageName + it.className }) { app ->
                val home = panels.firstOrNull { contains(it.widgets, app) }
                Row(Modifier.fillMaxWidth().height(62.dp).clickable(enabled = interactive) { onLaunch(app) }, verticalAlignment = Alignment.CenterVertically) {
                    val icon = remember(app.packageName, app.className) { app.icon?.toBitmap(72, 72)?.asImageBitmap() }
                    if (icon != null) Image(icon, null, Modifier.padding(5.dp).size(36.dp))
                    Text(app.label, Modifier.weight(1f), maxLines = 2, color = Color.White, fontSize = 14.sp)
                    if (home != null) TextButton(enabled = interactive, onClick = { onLocate(home.id) }) { Text("Na Home", fontSize = 11.sp) }
                    else TextButton(enabled = interactive, onClick = { onAdd(app) }) { Text("+", fontSize = 23.sp) }
                }
                HorizontalDivider(color = Color.White.copy(alpha = .12f))
            }
        }
    }
}
