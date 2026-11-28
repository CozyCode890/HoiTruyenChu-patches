package app.hoitruyenchu.patches.itruyenchu.misc.resume

import app.hoitruyenchu.patches.itruyenchu.misc.settings.enablePatchFlag
import app.hoitruyenchu.patches.itruyenchu.misc.settings.settingsPatch
import app.hoitruyenchu.patches.itruyenchu.audio.tts.ttsReliabilityPatch
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.COMPATIBILITY_ITRUYENCHU
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.EXTENSION_RESUME_LISTENING
import app.hoitruyenchu.patches.itruyenchu.shared.callAfterMainBundle
import app.morphe.patcher.patch.patch

@Suppress("unused")
val resumeListeningPatch = patch(
    name = "Resume listening position",
    description = "Returns to the last line you listened to when reopening a book from your bookshelf " +
            "or \"Đọc truyện\". Can highlight the line, ask before resuming, " +
            "or start reading automatically.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_ITRUYENCHU)

    // Also needs the TTS fix that drops the stray "Hết chương" of a re-opened reader.
    dependsOn(ttsReliabilityPatch, settingsPatch)

    execute {
        enablePatchFlag("resumeListening")

        callAfterMainBundle(EXTENSION_RESUME_LISTENING)
    }
}
