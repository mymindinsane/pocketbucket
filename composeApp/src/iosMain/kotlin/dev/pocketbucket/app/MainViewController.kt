@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.pocketbucket.app

import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketbucket.data.BitbucketApi
import dev.pocketbucket.data.RestBitbucketRepository
import io.ktor.client.engine.darwin.Darwin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    val model = viewModel {
        PocketBucketViewModel(IosCredentialStore()) { connection ->
            val api = BitbucketApi(Darwin.create {
                configureSession {
                    setHTTPShouldSetCookies(false)
                    setHTTPCookieStorage(null)
                    setURLCache(null)
                    setURLCredentialStorage(null)
                }
            }, connection)
            Session(RestBitbucketRepository(api), api::close)
        }
    }
    App(model)
}
