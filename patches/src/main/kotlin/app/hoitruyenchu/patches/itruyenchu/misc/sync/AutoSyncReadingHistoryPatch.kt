package app.hoitruyenchu.patches.itruyenchu.misc.sync

import app.hoitruyenchu.patches.itruyenchu.misc.extension.sharedExtensionPatch
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.COMPATIBILITY_ITRUYENCHU
import app.hoitruyenchu.patches.itruyenchu.shared.Constants.EXTENSION_AUTO_SYNC
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val autoSyncReadingHistoryPatch = patch(
    name = "Auto sync reading history",
    description = "Syncs the reading history with the iTruyenChu account automatically, " +
            "without pressing the sync button: every chapter change is uploaded " +
            "(including the free chapters 1-50 that the server never sees), " +
            "and history from other devices is merged in when the app starts.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_ITRUYENCHU)

    dependsOn(sharedExtensionPatch)

    execute {
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
        AsyncStorageMultiGetFingerprint.method.addInstructions(
            0,
            """
                iget-object v0, p0, $ASYNC_STORAGE_MODULE->executor:Lcom/reactnativecommunity/asyncstorage/SerialExecutor;
                invoke-static { p1, v0 }, $EXTENSION_AUTO_SYNC->beforeStorageGet(${READABLE_ARRAY}Ljava/util/concurrent/Executor;)V
            """,
        )

        // 3. Every write of the reading history (or of the login) schedules an upload.
        AsyncStorageMultiSetFingerprint.method.addInstructions(
            0,
            "invoke-static { p1 }, $EXTENSION_AUTO_SYNC->onStorageSet($READABLE_ARRAY)V",
        )
    }
}
