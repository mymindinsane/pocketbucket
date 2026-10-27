package dev.pocketbucket.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketbucket.data.BitbucketApi
import dev.pocketbucket.data.RestBitbucketRepository
import io.ktor.client.engine.okhttp.OkHttp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val model = viewModel {
                PocketBucketViewModel(AndroidCredentialStore(applicationContext)) { connection ->
                    val api = BitbucketApi(OkHttp.create {
                        config {
                            followRedirects(false)
                            followSslRedirects(false)
                            retryOnConnectionFailure(false)
                            // No cache, cookie jar, interceptors or custom TLS configuration.
                        }
                    }, connection)
                    Session(RestBitbucketRepository(api), api::close)
                }
            }
            val state by model.state.collectAsStateWithLifecycle()
            BackHandler(state.route != Route.Connection && state.route != Route.Projects) { model.back() }
            App(model)
        }
    }
}
