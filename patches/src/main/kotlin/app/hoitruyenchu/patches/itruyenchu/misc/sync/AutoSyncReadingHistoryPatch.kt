package app.hoitruyenchu.patches.itruyenchu.misc.sync

import app.hoitruyenchu.patches.itruyenchu.misc.settings.enablePatchFlag
import app.hoitruyenchu.patches.itruyenchu.misc.settings.settingsPatch
import app.hoitruyenchu.patches.itruyenchu.misc.extension.sharedExtensionPatch
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.COMPATIBILITY_ITRUYENCHU
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.EXTENSION_AUTO_SYNC
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.EXTENSION_SYNC_SCRIPT
import app.hoitruyenchu.patches.itruyenchu.shared.ASYNC_STORAGE_MODULE
import app.hoitruyenchu.patches.itruyenchu.shared.AsyncStorageConstructorFingerprint
import app.hoitruyenchu.patches.itruyenchu.shared.AsyncStorageMultiSetFingerprint
import app.hoitruyenchu.patches.itruyenchu.shared.READABLE_ARRAY
import app.hoitruyenchu.patches.itruyenchu.shared.callAfterMainBundle
import app.hoitruyenchu.patches.itruyenchu.shared.callBeforeStorageGet
import app.hoitruyenchu.patches.itruyenchu.shared.callOnCreateClientBuilder
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val autoSyncReadingHistoryPatch = patch(
    name = "Auto sync reading history",
    description = "Syncs the reading history with the iTruyenChu account without pressing the sync button: " +
            "free chapters 1-50, which the server never sees, are recorded too, and the progress " +
            "from other devices is merged in (with book titles) when the app starts and right after logging in. " +
            "The \"Đồng bộ\" button also works when the bookshelf is empty.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_ITRUYENCHU)

    dependsOn(sharedExtensionPatch, settingsPatch)

    execute {
        enablePatchFlag("autoSync")

        // 1. Module created: remember the context and start the startup sync in the background.
        AsyncStorageConstructorFingerprint.method.apply {
            if (implementation!!.registerCount - parameters.size - 1 < 1) {
                throw PatchException("AsyncStorageModule constructor has no free register")
            }
            val returnIndex = implementation!!.instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }
            addInstructions(
                returnIndex,
                """
                    invoke-virtual { p0 }, $ASYNC_STORAGE_MODULE->getReactApplicationContext()Lcom/facebook/react/bridge/ReactApplicationContext;
                    move-result-object v0
                    invoke-static { v0 }, $EXTENSION_AUTO_SYNC->onStorageCreated(Landroid/content/Context;)V
                """,
            )
        }

        // 2. Before the reading history is loaded, let the startup sync merge the server history in.
        //    The wait runs on the module's serial executor, so the JS thread is never blocked.
        callBeforeStorageGet(EXTENSION_AUTO_SYNC)

        // 3. Keep track of the login token.
        AsyncStorageMultiSetFingerprint.method.addInstructions(
            0,
            "invoke-static { p1 }, $EXTENSION_AUTO_SYNC->onStorageSet($READABLE_ARRAY)V",
        )

        // 4. Free chapters (1-50) are downloaded without the token: record them on the server.
        callOnCreateClientBuilder(EXTENSION_AUTO_SYNC)

        // 5. JS script: sync right after logging in, and let the bookshelf button sync an empty bookshelf.
        callAfterMainBundle(EXTENSION_SYNC_SCRIPT)
    }
}
