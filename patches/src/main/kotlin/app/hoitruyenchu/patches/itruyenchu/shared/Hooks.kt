package app.hoitruyenchu.patches.itruyenchu.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

// Hooks shared by several patches. Call them in execute; extensionClass is a type descriptor
// ("Lapp/.../Foo;") of an extension class with the static method named below.

/** extensionClass.afterMainBundle(Object reactInstance) before every return of ReactInstance.loadJSBundle. */
internal fun callAfterMainBundle(extensionClass: String) {
    ReactInstanceLoadJSBundleFingerprint.method.apply {
        val instructions = implementation!!.instructions.toList()
        instructions.indices.reversed()
            .filter { instructions[it].opcode == Opcode.RETURN_VOID }
            .forEach { index ->
                addInstruction(index, "invoke-static { p0 }, $extensionClass->afterMainBundle(Ljava/lang/Object;)V")
            }
    }
}

/** extensionClass.onCreateClientBuilder(OkHttpClient.Builder) before every return of createClientBuilder(). */
internal fun callOnCreateClientBuilder(extensionClass: String) {
    CreateClientBuilderFingerprint.method.apply {
        val instructions = implementation!!.instructions.toList()
        instructions.indices.reversed()
            .filter { instructions[it].opcode == Opcode.RETURN_OBJECT }
            .forEach { index ->
                val register = getInstruction<OneRegisterInstruction>(index).registerA
                addInstruction(
                    index,
                    "invoke-static { v$register }, $extensionClass->onCreateClientBuilder($OKHTTP_CLIENT_BUILDER)V",
                )
            }
    }
}

/**
 * extensionClass.beforeStorageGet(ReadableArray keys, Executor moduleExecutor) at the start of
 * AsyncStorageModule.multiGet: the work can wait on the module's serial executor, never on the JS thread.
 */
internal fun callBeforeStorageGet(extensionClass: String) {
    AsyncStorageMultiGetFingerprint.method.addInstructions(
        0,
        """
            iget-object v0, p0, $ASYNC_STORAGE_MODULE->executor:Lcom/reactnativecommunity/asyncstorage/SerialExecutor;
            invoke-static { p1, v0 }, $extensionClass->beforeStorageGet(${READABLE_ARRAY}Ljava/util/concurrent/Executor;)V
        """,
    )
}
