package fansirsqi.xposed.sesame.ui.extra

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.viewmodel.compose.viewModel
import fansirsqi.xposed.sesame.ui.extra.ui.RequestListScreen
import fansirsqi.xposed.sesame.ui.extra.viewmodel.RpcDebugViewModel

object ComposeBinder {
    @JvmStatic
    fun bindRpcList(composeView: ComposeView, initial: List<RequestItem>, callbacks: Callbacks) {
        composeView.setContent {
            MaterialTheme {
                val vm: RpcDebugViewModel = viewModel()
                LaunchedEffect(Unit) { vm.load(initial) }
                RequestListScreen(vm, callbacks)
            }
        }
    }
}