package app.hoitruyenchu.patches.itruyenchu.misc.login

import app.hoitruyenchu.patches.itruyenchu.misc.settings.enablePatchFlag
import app.hoitruyenchu.patches.itruyenchu.misc.settings.settingsPatch
import app.hoitruyenchu.patches.itruyenchu.misc.extension.sharedExtensionPatch
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.COMPATIBILITY_ITRUYENCHU
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.EXTENSION_FIX_GOOGLE_LOGIN
import app.hoitruyenchu.patches.itruyenchu.shared.callOnCreateClientBuilder
import app.morphe.patcher.patch.patch

@Suppress("unused")
val fixGoogleLoginPatch = patch(
    name = "Fix Google login",
    description = "Fixes \"Đăng nhập với Google\" failing with error 400: the server now expects the " +
            "Firebase ID token (like the website) instead of the email sent by this app version.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_ITRUYENCHU)

    dependsOn(sharedExtensionPatch, settingsPatch)

    execute {
        enablePatchFlag("googleLogin")

        callOnCreateClientBuilder(EXTENSION_FIX_GOOGLE_LOGIN)
    }
}
