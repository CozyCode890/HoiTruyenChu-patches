package app.hoitruyenchu.patches.itruyenchu.misc.login

import app.morphe.patcher.Fingerprint

internal const val OKHTTP_CLIENT_BUILDER = "Lokhttp3/OkHttpClient\$Builder;"

/** React Native's OkHttpClientProvider.createClientBuilder(): base builder of the networking client. */
internal object CreateClientBuilderFingerprint : Fingerprint(
    definingClass = "Lcom/facebook/react/modules/network/OkHttpClientProvider;",
    name = "createClientBuilder",
    returnType = OKHTTP_CLIENT_BUILDER,
    parameters = listOf(),
)
