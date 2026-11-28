package app.hoitruyenchu.patches.itruyenchu.audio.backgroundreading

import app.hoitruyenchu.patches.itruyenchu.misc.settings.enablePatchFlag
import app.hoitruyenchu.patches.itruyenchu.misc.settings.settingsPatch
import app.hoitruyenchu.patches.itruyenchu.audio.tts.ttsReliabilityPatch
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.COMPATIBILITY_ITRUYENCHU
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.EXTENSION_TTS_CONTROLLER
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.smali.ExternalLabel

@Suppress("unused")
val backgroundReadingPatch = patch(
    name = "Background reading",
    description = "Keeps reading aloud and advancing to the next chapter when the screen is off or " +
            "the app is in the background.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_ITRUYENCHU)

    // The reading session state lives in TtsController.
    dependsOn(ttsReliabilityPatch, settingsPatch)

    execute {
        enablePatchFlag("backgroundReading")

        TimerManagerOnHostPauseFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p0 }, $EXTENSION_TTS_CONTROLLER->shouldDeferHostPause(Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :pause
                    return-void
                """,
                ExternalLabel("pause", getInstruction(0)),
            )
        }

        TimerManagerOnHostResumeFingerprint.method.addInstructions(
            0,
            "invoke-static { }, $EXTENSION_TTS_CONTROLLER->onHostResume()V",
        )
    }
}
