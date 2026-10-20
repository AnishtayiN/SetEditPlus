package com.seteditplus.ui.hub

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import com.seteditplus.viewmodel.SettingsViewModel

enum class Route(val title: String) {
    HUB("ابزارها"),
    ENGINE("موتور داخلی"),
    TERMINAL("ترمینال ممتاز"),
    APPS("مدیریت برنامه‌ها"),
    DNS("DNS خصوصی"),
    TWEAKS("ترفندهای سریع"),
    DOCTOR("عیب‌یاب سرویس"),
    LOG("گزارش فعالیت"),
    GUIDES("راهنما")
}

/** پشتهٔ ناوبری سادهٔ ابزارها (روی صفحهٔ اصلی باز می‌شود) */
class HubNavigator {
    val stack: SnapshotStateList<Route> = mutableStateListOf()
    val current: Route? get() = stack.lastOrNull()
    fun open(route: Route) {
        if (stack.lastOrNull() != route) stack.add(route)
    }
    fun back() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }
    fun close() = stack.clear()
}

@Composable
fun HubHost(nav: HubNavigator, viewModel: SettingsViewModel) {
    val route = nav.current ?: return
    BackHandler { nav.back() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                (slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(260)))
                    .togetherWith(slideOutHorizontally(tween(200)) { -it / 8 } + fadeOut(tween(200)))
            },
            label = "hub"
        ) { r ->
            val back = { nav.back() }
            when (r) {
                Route.HUB -> ToolsHubScreen(viewModel, onOpen = nav::open, onBack = back)
                Route.ENGINE -> EngineScreen(viewModel, onBack = back, onOpen = nav::open)
                Route.TERMINAL -> TerminalScreen(viewModel, onBack = back)
                Route.APPS -> AppManagerScreen(onBack = back)
                Route.DNS -> DnsScreen(viewModel, onBack = back)
                Route.TWEAKS -> TweaksScreen(viewModel, onBack = back)
                Route.DOCTOR -> DoctorScreen(viewModel, onBack = back, onOpen = nav::open)
                Route.LOG -> LogScreen(onBack = back)
                Route.GUIDES -> GuidesScreen(onBack = back, onOpen = nav::open)
            }
        }
    }
}
