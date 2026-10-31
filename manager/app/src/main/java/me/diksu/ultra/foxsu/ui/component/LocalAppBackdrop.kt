package me.diksu.ultra.foxsu.ui.component

import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 整个页面内容的 backdrop（MainScreen 里创建的 layerBackdrop）。
 * 液态玻璃类的组件（底栏拖动高光等）靠它采样下方真实像素来做折射/高光。
 */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop?> { null }
