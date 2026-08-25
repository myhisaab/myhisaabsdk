package pk.myhisaab.sdk.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import pk.myhisaab.sdk.model.MyHisaabEnvironment
import pk.myhisaab.sdk.ui.MyHisaabActivity.Companion.launch

/**
 * Full-screen host Activity for [MyHisaabView] — the View-based entry point.
 *
 * Launch it with [launch]; the SDK's manifest already declares this Activity, so
 * the host app does not need to register anything.
 */
class MyHisaabActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val customerId = intent.getStringExtra(EXTRA_CUSTOMER_ID).orEmpty()
        val environment = intent.getStringExtra(EXTRA_ENVIRONMENT)
            ?.let { runCatching { MyHisaabEnvironment.valueOf(it) }.getOrNull() }
            ?: MyHisaabEnvironment.PRODUCTION
        val fcmToken = intent.getStringExtra(EXTRA_FCM_TOKEN)

        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MyHisaabView(
                        customerId = customerId,
                        environment = environment,
                        fcmToken = fcmToken,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_CUSTOMER_ID = "pk.myhisaab.sdk.EXTRA_CUSTOMER_ID"
        private const val EXTRA_ENVIRONMENT = "pk.myhisaab.sdk.EXTRA_ENVIRONMENT"
        private const val EXTRA_FCM_TOKEN = "pk.myhisaab.sdk.EXTRA_FCM_TOKEN"

        /**
         * Starts [MyHisaabActivity].
         *
         * @param fcmToken optional; if the host obtains/rotates the token later,
         *   prefer embedding [MyHisaabView] directly so you can update it live.
         */
        fun launch(
            context: Context,
            customerId: String,
            environment: MyHisaabEnvironment = MyHisaabEnvironment.PRODUCTION,
            fcmToken: String? = null,
        ) {
            val intent = Intent(context, MyHisaabActivity::class.java).apply {
                putExtra(EXTRA_CUSTOMER_ID, customerId)
                putExtra(EXTRA_ENVIRONMENT, environment.name)
                fcmToken?.let { putExtra(EXTRA_FCM_TOKEN, it) }
            }
            context.startActivity(intent)
        }
    }
}
