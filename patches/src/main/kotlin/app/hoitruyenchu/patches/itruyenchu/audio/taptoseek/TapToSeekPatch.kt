package app.hoitruyenchu.patches.itruyenchu.audio.taptoseek

import app.hoitruyenchu.patches.itruyenchu.misc.settings.enablePatchFlag
import app.hoitruyenchu.patches.itruyenchu.misc.settings.settingsPatch
import app.hoitruyenchu.patches.itruyenchu.misc.extension.sharedExtensionPatch
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.COMPATIBILITY_ITRUYENCHU
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.EXTENSION_TAP_TO_SEEK
import app.hoitruyenchu.patches.itruyenchu.shared.callAfterMainBundle
import app.morphe.patcher.patch.patch

@Suppress("unused")
val tapToSeekPatch = patch(
    name = "Tap a line to listen from it",
    description = "Lets you tap any line in vertical reading mode to jump the voice reading there, pause, " +
            "or start from that word. Scrolling manually pauses auto-follow and shows a " +
            "\"Về dòng đang đọc\" button, with customizable tap and scroll behaviors in settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_ITRUYENCHU)

    dependsOn(sharedExtensionPatch, settingsPatch)

    execute {
        enablePatchFlag("tapToSeek")

        // JS script evaluated after the app bundle.
        callAfterMainBundle(EXTENSION_TAP_TO_SEEK)
    }
}
