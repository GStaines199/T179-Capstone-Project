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
import java.io.File
import java.security.MessageDigest
import java.util.function.Consumer

object DittoSdkBridge {
    private var sharedDitto: Ditto? = null
    private var sharedDatabaseId: String = ""
    private var sharedAuthUrl: String = ""
    private var sharedPersistenceDirectory: String = ""
    private var sharedUsers: Int = 0
    private var appContext: Context? = null

    @JvmStatic
    fun initialize(context: Context) {
        appContext = context.applicationContext ?: context
        AppInitializer.getInstance(appContext ?: context)
            .initializeComponent(DittoInitializer::class.java)
    }

    @JvmStatic
    @Synchronized
    fun createDitto(databaseId: String, authUrl: String): Ditto {
        val persistenceDirectory = persistenceDirectory(databaseId, authUrl)
        sharedDitto?.let { existing ->
            if (sharedUsers <= 0) {
                closeSharedDitto()
            } else if (sharedDatabaseId == databaseId && sharedAuthUrl == authUrl &&
                sharedPersistenceDirectory == persistenceDirectory
            ) {
                sharedUsers += 1
                return existing
            } else {
                closeSharedDitto()
            }
        }
        val config = DittoConfig(
            databaseId = databaseId,
            connect = DittoConfig.Connect.Server(authUrl),
            persistenceDirectory = persistenceDirectory
        )
        return try {
            DittoFactory.create(config)
        } catch (throwable: Throwable) {
            closeSharedDitto()
            DittoFactory.create(config)
        }.also {
            sharedDitto = it
            sharedDatabaseId = databaseId
            sharedAuthUrl = authUrl
            sharedPersistenceDirectory = persistenceDirectory
            sharedUsers = 1
        }
    }

    @JvmStatic
    fun setupAuth(ditto: Ditto, token: String) {
        setupAuthHandler(ditto, token)
        runBlocking {
            ditto.auth?.login(token, DittoAuthenticationProvider.development())
        }
    }

    @JvmStatic
    fun setupAuthHandler(ditto: Ditto, token: String) {
        ditto.auth?.expirationHandler = { dittoInstance, _ ->
            dittoInstance.auth?.login(token, DittoAuthenticationProvider.development())
        }
    }

    @JvmStatic
    fun loginAsync(ditto: Ditto, token: String, callback: Consumer<String>) {
        setupAuthHandler(ditto, token)
        Thread {
            try {
                runBlocking {
                    ditto.auth?.login(token, DittoAuthenticationProvider.development())
                }
                callback.accept("")
            } catch (throwable: Throwable) {
                callback.accept(describeFailure(throwable))
            }
        }.apply {
            name = "SARtak-Ditto-Auth"
            isDaemon = true
            start()
        }
    }

    @JvmStatic
    fun startSyncSafely(ditto: Ditto): String {
        return try {
            ditto.sync.start()
            ""
        } catch (throwable: Throwable) {
            describeFailure(throwable)
        }
    }

    @JvmStatic
    fun disableStrictModeSafely(ditto: Ditto): String {
        return try {
            disableStrictMode(ditto)
            ""
        } catch (throwable: Throwable) {
            describeFailure(throwable)
        }
    }

    @JvmStatic
    @Throws(DittoException::class)
    fun disableStrictMode(ditto: Ditto) {
        runBlocking {
            ditto.store.execute("ALTER SYSTEM SET DQL_STRICT_MODE=false")
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
    @Throws(DittoException::class)
    fun executeJsonArgument(
        ditto: Ditto,
        query: String,
        argumentName: String,
        json: String
    ) {
        runBlocking {
            ditto.store.execute(query, mapOf(argumentName to json))
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
    @Synchronized
    fun releaseDitto(ditto: Ditto) {
        if (sharedDitto === ditto) {
            sharedUsers = (sharedUsers - 1).coerceAtLeast(0)
            if (sharedUsers > 0) {
                return
            }
            closeSharedDitto()
            return
        }
        try {
            ditto.sync.stop()
        } catch (_: Throwable) {
        }
        try {
            ditto.close()
        } catch (_: Throwable) {
        }
    }

    @JvmStatic
    fun closeObserver(observer: DittoStoreObserver?) {
        try {
            observer?.close()
        } catch (_: Throwable) {
        }
    }

    @JvmStatic
    fun closeSubscription(subscription: DittoSyncSubscription?) {
        try {
            subscription?.close()
        } catch (_: Throwable) {
        }
    }

    @JvmStatic
    fun isSyncActive(ditto: Ditto): Boolean {
        return ditto.sync.isActive
    }

    @JvmStatic
    fun isAuthenticated(ditto: Ditto): Boolean {
        return ditto.auth?.status?.isAuthenticated == true
    }

    private fun closeSharedDitto() {
        sharedDitto?.let {
            try {
                it.sync.stop()
            } catch (_: Throwable) {
            }
            try {
                it.close()
            } catch (_: Throwable) {
            }
        }
        sharedDitto = null
        sharedDatabaseId = ""
        sharedAuthUrl = ""
        sharedPersistenceDirectory = ""
        sharedUsers = 0
    }

    private fun persistenceDirectory(databaseId: String, authUrl: String): String {
        val context = appContext
            ?: throw IllegalStateException("DittoSdkBridge.initialize must be called before createDitto")
        val root = File(context.filesDir, "sartak_ditto")
        val directory = File(root, stableDirectoryName(databaseId, authUrl))
        directory.mkdirs()
        return directory.absolutePath
    }

    private fun stableDirectoryName(databaseId: String, authUrl: String): String {
        val seed = "$databaseId|$authUrl"
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun describeFailure(throwable: Throwable?): String {
        if (throwable == null) {
            return "unknown"
        }
        val name = throwable::class.java.simpleName
        val message = throwable.message?.trim().orEmpty()
        return if (message.isEmpty()) name else "$name: $message"
    }
}
