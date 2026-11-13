package app.hoitruyenchu.patches.itruyenchu.misc.sync

import app.morphe.patcher.Fingerprint

internal const val ASYNC_STORAGE_MODULE = "Lcom/reactnativecommunity/asyncstorage/AsyncStorageModule;"
internal const val READABLE_ARRAY = "Lcom/facebook/react/bridge/ReadableArray;"
private const val CALLBACK = "Lcom/facebook/react/bridge/Callback;"

/** AsyncStorageModule(ReactApplicationContext context, Executor executor) */
internal object AsyncStorageConstructorFingerprint : Fingerprint(
    definingClass = ASYNC_STORAGE_MODULE,
    name = "<init>",
    returnType = "V",
    parameters = listOf("Lcom/facebook/react/bridge/ReactApplicationContext;", "Ljava/util/concurrent/Executor;"),
)

/** @ReactMethod multiGet(ReadableArray keys, Callback callback) */
internal object AsyncStorageMultiGetFingerprint : Fingerprint(
    definingClass = ASYNC_STORAGE_MODULE,
    name = "multiGet",
    returnType = "V",
    parameters = listOf(READABLE_ARRAY, CALLBACK),
)

/** @ReactMethod multiSet(ReadableArray keyValueArray, Callback callback) */
internal object AsyncStorageMultiSetFingerprint : Fingerprint(
    definingClass = ASYNC_STORAGE_MODULE,
    name = "multiSet",
    returnType = "V",
    parameters = listOf(READABLE_ARRAY, CALLBACK),
)
