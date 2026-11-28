package app.hoitruyenchu.patches.itruyenchu.audio.speed

import app.hoitruyenchu.patches.itruyenchu.misc.settings.enablePatchFlag
import app.hoitruyenchu.patches.itruyenchu.misc.settings.settingsPatch
import app.hoitruyenchu.patches.itruyenchu.misc.extension.sharedExtensionPatch
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.COMPATIBILITY_ITRUYENCHU
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.EXTENSION_REMEMBER_SPEED
import app.hoitruyenchu.patches.itruyenchu.shared.callAfterMainBundle
import app.morphe.patcher.patch.patch

@Suppress("unused")
val rememberSpeedPatch = patch(
    name = "Remember listening speed",
    description = "Remembers your chosen reading speed across app restarts instead of resetting to 1x, " +
            "using one speed for all books. Replaces the speed menu with a YouTube-style slider and " +
            "customizable quick-select buttons.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_ITRUYENCHU)

    dependsOn(sharedExtensionPatch, settingsPatch)

    execute {
        enablePatchFlag("rememberSpeed")

        // JS script evaluated after the app bundle.
        callAfterMainBundle(EXTENSION_REMEMBER_SPEED)
    }
}
