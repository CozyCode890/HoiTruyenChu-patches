package app.hoitruyenchu.extension.itruyenchu.patches.speed;

import app.hoitruyenchu.extension.itruyenchu.patches.shared.JsScripts;

/**
 * The listening speed went back to 1x every time the app was restarted.
 * <p>
 * Root cause (app 2.1.0): the voice bar store useTTSVoiceStore (zustand persist, AsyncStorage key
 * {@code tts_voice_state}) keeps the speed in {@code speed}, but its partialize saves it under the
 * misspelled key {@code spead}: {@code {"state":{"spead":1.5,"isVoiceBarOpen":true},"version":0}}.
 * On start the store is hydrated with {@code spead}, nothing reads it, and {@code speed} keeps its
 * initial value.
 * <p>
 * A JS script ({@link #SCRIPT}) evaluated right after the app's bundle waits for the store to be
 * hydrated and calls {@code setSpeed(spead)}. The speed is one for all books, like the app intends,
 * and the value saved by the unpatched app before is used as well. Plain ES5, everything inside
 * try/catch: an exception escaping here would be a fatal JS error. Module id (app 2.1.0):
 * 2766 = useTTSVoiceStore.
 */
@SuppressWarnings("unused")
public final class RememberSpeedPatch {
    private static final String TAG = "HTC-Speed";
    private static final String FILE_NAME = "htc-remember-speed.js";

    private RememberSpeedPatch() {
    }

    /** End of ReactInstance.loadJSBundle(loader): the app bundle is scheduled, queue the script after it. */
    public static void afterMainBundle(Object reactInstance) {
        JsScripts.load(reactInstance, FILE_NAME, SCRIPT, TAG);
    }

    static final String SCRIPT = ""
            + "(function () {\n"
            + "  var TAG = '[HTC-Speed] ';\n"
            + "  function log(m) { try { console.log(TAG + m); } catch (e) {} }\n"
            + "  try {\n"
            + "    var g = typeof globalThis !== 'undefined' ? globalThis : this;\n"
            + "    if (typeof g.__r !== 'function') { log('no __r'); return; }\n"
            + "    var M = g.__r(2766);\n"
            + "    var V = M && M.useTTSVoiceStore;\n"
            + "    if (!V || typeof V.getState !== 'function' || typeof V.getState().setSpeed !== 'function'\n"
            + "        || !V.persist) { log('voice store not found'); return; }\n"
            + "    var done = false;\n"
            + "    function restore() {\n"
            + "      if (done) return;\n"
            + "      done = true;\n"
            + "      try {\n"
            + "        var s = V.getState();\n"
            + "        var saved = s.spead;\n"
            + "        if (typeof saved !== 'number' || !(saved >= 0.25 && saved <= 4)) { log('no saved speed'); return; }\n"
            + "        if (saved === s.speed) { log('speed ' + saved); return; }\n"
            + "        s.setSpeed(saved);\n"
            + "        log('speed restored ' + saved);\n"
            + "      } catch (e) { log('restore failed ' + e); }\n"
            + "    }\n"
            + "    var hydrated = false;\n"
            + "    try { hydrated = typeof V.persist.hasHydrated !== 'function' || V.persist.hasHydrated(); } catch (e) {}\n"
            + "    if (hydrated) restore();\n"
            + "    else if (typeof V.persist.onFinishHydration === 'function') {\n"
            + "      var unsub = V.persist.onFinishHydration(function () {\n"
            + "        restore();\n"
            + "        try { if (typeof unsub === 'function') unsub(); } catch (e) {}\n"
            + "      });\n"
            + "      // Hydration may end between the check and the subscription.\n"
            + "      try { if (V.persist.hasHydrated()) restore(); } catch (e) {}\n"
            + "    } else log('no onFinishHydration');\n"
            + "  } catch (e) { log('error ' + e); }\n"
            + "})();\n";
}
