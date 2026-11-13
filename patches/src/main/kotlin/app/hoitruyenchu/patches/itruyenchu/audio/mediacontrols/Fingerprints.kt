package app.hoitruyenchu.patches.itruyenchu.audio.mediacontrols

import app.morphe.patcher.Fingerprint

internal const val AUDIO_CONTROLS_SERVICE = "Lexpo/modules/audio/service/AudioControlsService;"
internal const val MEDIA3_PLAYER = "Landroidx/media3/common/Player;"
internal const val MEDIA_SESSION_BUILDER = "Landroidx/media3/session/MediaSession\$Builder;"

/** expo-audio: private fun setActivePlayerInternal(player, metadata, options), builds the MediaSession. */
internal object SetActivePlayerInternalFingerprint : Fingerprint(
    definingClass = AUDIO_CONTROLS_SERVICE,
    name = "setActivePlayerInternal",
    returnType = "V",
    parameters = listOf(
        "Lexpo/modules/audio/AudioPlayer;",
        "Lexpo/modules/audio/Metadata;",
        "Lexpo/modules/audio/AudioLockScreenOptions;",
    ),
)

/** expo-audio: private fun postOrStartForegroundNotification(startInForeground: Boolean) */
internal object PostOrStartForegroundNotificationFingerprint : Fingerprint(
    definingClass = AUDIO_CONTROLS_SERVICE,
    name = "postOrStartForegroundNotification",
    returnType = "V",
    parameters = listOf("Z"),
)
