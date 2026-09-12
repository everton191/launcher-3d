package br.com.ne3d.spbshellmodern.shell3d.widgets

import android.graphics.Bitmap
/** Contract for a future AppWidgetHostView capture coordinator; no per-frame capture. */
interface AndroidWidgetSurface { fun snapshotIfDirty(): Bitmap? }
