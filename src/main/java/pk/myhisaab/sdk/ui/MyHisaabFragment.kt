package pk.myhisaab.sdk.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import pk.myhisaab.sdk.model.MyHisaabEnvironment
import pk.myhisaab.sdk.ui.MyHisaabFragment.Companion.newInstance

/**
 * Embeddable [Fragment] host for [MyHisaabView] — for View-based apps that place
 * the SDK inside an existing screen. Create with [newInstance].
 */
class MyHisaabFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        val customerId = arguments?.getString(ARG_CUSTOMER_ID).orEmpty()
        val environment = arguments?.getString(ARG_ENVIRONMENT)
            ?.let { runCatching { MyHisaabEnvironment.valueOf(it) }.getOrNull() }
            ?: MyHisaabEnvironment.PRODUCTION
        val fcmToken = arguments?.getString(ARG_FCM_TOKEN)

        setContent {
            MaterialTheme {
                MyHisaabView(
                    customerId = customerId,
                    environment = environment,
                    fcmToken = fcmToken,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    companion object {
        private const val ARG_CUSTOMER_ID = "customerId"
        private const val ARG_ENVIRONMENT = "environment"
        private const val ARG_FCM_TOKEN = "fcmToken"

        fun newInstance(
            customerId: String,
            environment: MyHisaabEnvironment = MyHisaabEnvironment.PRODUCTION,
            fcmToken: String? = null,
        ): MyHisaabFragment = MyHisaabFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_CUSTOMER_ID, customerId)
                putString(ARG_ENVIRONMENT, environment.name)
                fcmToken?.let { putString(ARG_FCM_TOKEN, it) }
            }
        }
    }
}
