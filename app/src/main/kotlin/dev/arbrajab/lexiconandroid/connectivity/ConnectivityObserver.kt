package dev.arbrajab.lexiconandroid.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class ConnectivityState { ONLINE, OFFLINE }

interface ConnectivityObserver {
    fun observe(): Flow<ConnectivityState>
}

class NetworkConnectivityObserver(context: Context) : ConnectivityObserver {
    private val connectivityManager =
        context.applicationContext.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

    override fun observe(): Flow<ConnectivityState> = callbackFlow {
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(currentState())
                }

                override fun onLost(network: Network) {
                    trySend(currentState())
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities
                ) {
                    trySend(currentState())
                }
            }

        val request =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

        connectivityManager.registerNetworkCallback(request, callback)
        trySend(currentState())

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun currentState(): ConnectivityState {
        val network = connectivityManager.activeNetwork ?: return ConnectivityState.OFFLINE
        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return ConnectivityState.OFFLINE
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return if (hasInternet && validated) ConnectivityState.ONLINE else ConnectivityState.OFFLINE
    }
}
