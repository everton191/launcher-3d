package br.com.ne3d.spbshellmodern.shell3d.widgets

/** Future AppWidget policy: host view in normal pages, captured texture only while transitioning, then crossfade back.
 * SurfaceView, TextureView, WebView, video and protected content must fall back to the live host view. */
interface AppWidget3DStrategy { fun canCaptureForTransition(): Boolean }
