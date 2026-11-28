package app.hoitruyenchu.patches.itruyenchu.misc.titles

import app.hoitruyenchu.patches.itruyenchu.misc.settings.enablePatchFlag
import app.hoitruyenchu.patches.itruyenchu.misc.settings.settingsPatch
import app.hoitruyenchu.patches.itruyenchu.misc.extension.sharedExtensionPatch
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.COMPATIBILITY_ITRUYENCHU
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.EXTENSION_CHAPTER_TITLES
import app.hoitruyenchu.patches.itruyenchu.shared.callAfterMainBundle
import app.hoitruyenchu.patches.itruyenchu.shared.callBeforeStorageGet
import app.hoitruyenchu.patches.itruyenchu.shared.callOnCreateClientBuilder
import app.morphe.patcher.patch.patch

@Suppress("unused")
val showChapterTitlesPatch = patch(
    name = "Show chapter titles",
    description = "Shows real chapter titles instead of \"Vô Đề Mục\" in the chapter list, reader, and TTS " +
            "by reading the title from each chapter's opening line. Titles are cached on your device and " +
            "do not require logging in.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_ITRUYENCHU)

    dependsOn(sharedExtensionPatch, settingsPatch)

    execute {
        enablePatchFlag("chapterTitles")

        // 1. The chapter list downloaded by the app gets the real titles.
        callOnCreateClientBuilder(EXTENSION_CHAPTER_TITLES)

        // 2. So does the copy app 2.1.0 keeps in AsyncStorage (refreshed only every hour).
        callBeforeStorageGet(EXTENSION_CHAPTER_TITLES)

        // 3. App 3.0.0 keeps it in memory: a JS script makes it download the list again for new titles.
        callAfterMainBundle(EXTENSION_CHAPTER_TITLES)
    }
}
