package com.atakmap.android.plugintemplate.runtime

import android.content.Context
import androidx.startup.AppInitializer
import com.ditto.kotlin.Ditto
import com.ditto.kotlin.DittoAuthenticationProvider
import com.ditto.kotlin.DittoConfig
import com.ditto.kotlin.DittoException
import com.ditto.kotlin.DittoFactory
import com.ditto.kotlin.DittoInitializer
import com.ditto.kotlin.DittoStoreObserver
import com.ditto.kotlin.DittoSyncSubscription
import kotlinx.coroutines.runBlocking
import java.util.function.Consumer

object DittoSdkBridge {
    private var sharedDitto: Ditto? = null
    private var sharedDatabaseId: String = ""
    private var sharedAuthUrl: String = ""
    private var sharedUsers: Int = 0

    @JvmStatic
    fun initialize(context: Context) {
        AppInitializer.getInstance(context.applicationContext ?: context)
            .initializeComponent(DittoInitializer::class.java)
    }

    @JvmStatic
    @Synchronized
    fun createDitto(databaseId: String, authUrl: String): Ditto {
        sharedDitto?.let { existing ->
            if (sharedDatabaseId == databaseId && sharedAuthUrl == authUrl) {
                sharedUsers += 1
                return existing
            }
        }
        val config = DittoConfig(
            databaseId = databaseId,
            connect = DittoConfig.Connect.Server(authUrl)
        )
        return DittoFactory.create(config).also {
            sharedDitto = it
            sharedDatabaseId = databaseId
            sharedAuthUrl = authUrl
            sharedUsers = 1
        }
    }

    @JvmStatic
    fun setupAuth(ditto: Ditto, token: String) {
        ditto.auth?.expirationHandler = { dittoInstance, _ ->
            dittoInstance.auth?.login(token, DittoAuthenticationProvider.development())
        }
    }

    @JvmStatic
    @Throws(DittoException::class)
    fun execute(ditto: Ditto, query: String, args: Map<String, Any?>) {
        runBlocking {
            ditto.store.execute(query, args)
        }
    }

    @JvmStatic
    fun registerSubscription(ditto: Ditto, query: String): DittoSyncSubscription {
        return ditto.sync.registerSubscription(query)
    }

    @JvmStatic
    fun registerJsonObserver(
        ditto: Ditto,
        query: String,
        callback: Consumer<List<String>>
    ): DittoStoreObserver {
        return ditto.store.registerObserver(query) { result ->
            callback.accept(result.items.map { item -> item.jsonString() })
        }
    }

    @JvmStatic
    fun startSync(ditto: Ditto) {
        ditto.sync.start()
    }

    @JvmStatic
    @Synchronized
    fun stopSync(ditto: Ditto) {
        if (sharedDitto === ditto) {
            sharedUsers = (sharedUsers - 1).coerceAtLeast(0)
            if (sharedUsers > 0) {
                return
            }
        }
        ditto.sync.stop()
    }

    @JvmStatic
    fun isSyncActive(ditto: Ditto): Boolean {
        return ditto.sync.isActive
    }
}
