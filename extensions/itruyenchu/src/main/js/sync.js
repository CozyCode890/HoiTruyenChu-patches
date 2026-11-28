// java: app/hoitruyenchu/extension/itruyenchu/patches/sync/SyncScript.java
(function () {
  var TAG = '[HTC-Sync] ';
  var EMPTY = 'Kh\u00f4ng c\u00f3 truy\u1ec7n \u0111\u1ec3 \u0111\u1ed3ng b\u1ed9';
  var log = globalThis.__htcLog(TAG), S = globalThis.__htcGet;
  function esDefault(m) { return m && m.__esModule ? m['default'] : m; }
  try {
    var g = typeof globalThis !== 'undefined' ? globalThis : this;
    if (typeof g.__r !== 'function') { log('no __r'); return; }
    var A = g.__htcR('auth') && g.__htcR('auth').useAuthStore;
    var R = g.__htcR('read') && g.__htcR('read').useReadStore;
    var Toast = esDefault(g.__htcR('toast'));
    var api = esDefault(g.__htcR('api'));
    if (!A || typeof A.getState !== 'function' || typeof A.subscribe !== 'function') { log('auth store not found'); return; }
    if (!R || typeof R.getState !== 'function' || typeof R.getState().addRecentRead !== 'function'
        || typeof R.getState().clearRecentReads !== 'function') { log('read store not found'); return; }
    if (!api || typeof api.post !== 'function') { log('api client not found'); return; }
    if (!Toast || typeof Toast.show !== 'function') { log('toast not found'); Toast = null; }

    // ---- Books deleted from the history (the X of the bookshelf): the server cannot forget them, so
    // they are kept in AsyncStorage htc_sync_deleted {slug: chapter when deleted}; AutoSyncPatch drops
    // them from what the server sends back. Reading the book again on this device ends the deletion.
    var DELETED_KEY = 'htc_sync_deleted';
    var storage = null;
    try { storage = A.persist.getOptions().storage; } catch (e) {}
    var deleted = {};
    var restoring = false;   // our own clear + add of the merged history
    function saveDeleted() {
      try {
        if (!storage) return;
        var p0 = storage.setItem(DELETED_KEY, deleted);
        if (p0 && typeof p0.then === 'function') p0.then(null, function () {});
      } catch (e) { log('save deleted failed ' + e); }
    }
    if (storage) {
      try {
        var r0 = storage.getItem(DELETED_KEY);
        var take = function (v) { if (v && typeof v === 'object') { for (var k0 in v) if (!(k0 in deleted)) deleted[k0] = v[k0]; } };
        if (r0 && typeof r0.then === 'function') r0.then(take, function () {}); else take(r0);
      } catch (e) {}
    }
    function slugOf(arg) { return typeof arg === 'string' ? arg : (arg && typeof arg.slug === 'string' ? arg.slug : null); }
    // fn wrapped so that before(arg) runs first (errors logged); marked so it is wrapped only once.
    function hook(fn, before) {
      if (typeof fn !== 'function' || fn.__htcSync) return fn;
      var w = function (arg) {
        try { before(arg); } catch (e) { log('history hook failed ' + e); }
        return fn.apply(this, arguments);
      };
      w.__htcSync = true;
      return w;
    }
    function onRemove(arg) {
      var slug = slugOf(arg);
      if (!slug) return;
      var list = R.getState().recentReads || [], chapter = 0;
      for (var i = 0; i < list.length; i++) if (list[i] && list[i].slug === slug) chapter = Number(list[i].currentChapter) || 0;
      deleted[slug] = chapter;
      saveDeleted();
      log('deleted ' + slug + ' at chapter ' + chapter + ': not pulled back from the server');
    }
    function onAdd(entry) {
      var slug = slugOf(entry);
      if (!restoring && slug && slug in deleted) {
        delete deleted[slug];
        saveDeleted();
        log('read again: ' + slug + ' synced again');
      }
    }
    function wrapStore(st) {
      var patch = {}, any = false;
      if (typeof st.removeRecentRead === 'function' && !st.removeRecentRead.__htcSync) { patch.removeRecentRead = hook(st.removeRecentRead, onRemove); any = true; }
      if (typeof st.addRecentRead === 'function' && !st.addRecentRead.__htcSync) { patch.addRecentRead = hook(st.addRecentRead, onAdd); any = true; }
      if (any) R.setState(patch);
    }
    try {
      wrapStore(R.getState());
      R.subscribe(function (st) { try { wrapStore(st); } catch (e) {} });
    } catch (e) { log('history hooks failed ' + e); }

    var busy = false;
    var originalShow = Toast ? Toast.show : null;
    function toast(options) {
      try { if (originalShow) originalShow.call(Toast, options); } catch (e) {}
    }
    function loggedIn() {
      try { return !!A.getState().accessToken; } catch (e) { return false; }
    }

    // Same as the bookshelf button. options: the app's toast object of a button press, or null (login:
    // toast only when books were added). A press during a running sync gets that sync's result.
    var pending = null;
    var ERROR = 'Không đồng bộ được, hãy thử lại';
    var DONE = 'Đồng bộ truyện thành công';
    function message(type, text, options) {
      return { type: type, text1: text, position: (options && options.position) || 'bottom' };
    }
    function key(item) { return item ? item.slug + '#' + item.currentChapter : ''; }
    function sync(reason, options) {
      if (!loggedIn()) return;
      if (busy) { if (options) pending = options; return; }
      busy = true;
      var local = [];
      try { local = (R.getState().recentReads || []).slice(); } catch (e) {}
      var before = {};
      for (var b = 0; b < local.length; b++) before[key(local[b])] = true;
      log(reason + ': syncing, local=' + local.length);
      function finish() {
        busy = false;
        var o = options || pending;
        pending = null;
        return o;
      }
      var p;
      try { p = api.post('/users/sync-books', { recentReads: local, htcOrder: 'oldest' }); } catch (e) { finish(); log('post failed ' + e); return; }
      p.then(function (res) {
        var opts = finish();
        try {
          var data = res && res.data;
          var list = data && data.recentReads;
          if (Object.prototype.toString.call(list) !== '[object Array]') {
            log(reason + ': unexpected answer');
            if (opts) toast(message('error', (data && data.message) || ERROR, opts));
            return;
          }
          if (list.length > 0) {
            // Entries read while the request was running are not in the answer: keep them on top.
            var fresh = [], freshSlugs = {};
            var now = R.getState().recentReads || [];
            for (var i = 0; i < now.length; i++) {
              if (now[i] && !before[key(now[i])]) { fresh.push(now[i]); freshSlugs[now[i].slug] = true; }
            }
            restoring = true;
            try {
              R.getState().clearRecentReads();
              // The answer is the merged history, oldest first; addRecentRead puts each entry on top.
              for (var j = 0; j < list.length; j++) {
                if (list[j] && !freshSlugs[list[j].slug]) R.getState().addRecentRead(list[j]);
              }
              for (var k = fresh.length - 1; k >= 0; k--) R.getState().addRecentRead(fresh[k]);
            } finally { restoring = false; }
            if (fresh.length) log(reason + ': kept ' + fresh.length + ' entries read meanwhile');
          }
          log(reason + ': ' + list.length + ' books');
          var mode = S('sync.toasts', 'all');
          var changed = list.length > local.length;
          if (opts) {
            // Button pressed: always answer.
            if (list.length === 0) toast(opts);
            else toast(message('success', (data && data.message) || DONE, opts));
          } else if (mode === 'all' || (mode === 'changes' && changed)) {
            toast(message('success', (data && data.message) || DONE, null));
          }
        } catch (e) { log(reason + ': apply failed ' + e); }
      }, function (e) {
        var opts = finish();
        log(reason + ': failed ' + e);
        if (opts || S('sync.toasts', 'all') !== 'none') {
          var msg = null;
          try { msg = e && e.response && e.response.data && e.response.data.message; } catch (x) {}
          toast(message('error', msg || ERROR, opts));
        }
      });
    }

    // 1. The bookshelf button with an empty bookshelf: sync instead of refusing.
    if (Toast) {
      Toast.show = function (options) {
        try {
          if (options && options.text1 === EMPTY && loggedIn() && S('sync.enabled', true)) {
            sync('button', options);
            return;
          }
        } catch (e) {}
        return originalShow.apply(this, arguments);
      };
    }

    // 2. Login after the app started (the startup sync ran without a token).
    var hydrated = function () {
      try { return !A.persist || typeof A.persist.hasHydrated !== 'function' || A.persist.hasHydrated(); } catch (e) { return true; }
    };
    A.subscribe(function (s, prev) {
      try {
        if (!s || !s.accessToken || (prev && prev.accessToken) || !hydrated()) return;
        if (!S('sync.enabled', true) || !S('sync.onLogin', true)) return;
        // Let the login finish (user, navigation) before syncing.
        setTimeout(function () { sync('login', null); }, 1500);
      } catch (e) { log('listener failed ' + e); }
    });

    // 3. Back to the app after more than 5 minutes in the background.
    try {
      var RN = g.__htcR('rn');
      var awayAt = 0;
      if (RN && RN.AppState && typeof RN.AppState.addEventListener === 'function') {
        RN.AppState.addEventListener('change', function (state) {
          try {
            if (state !== 'active') { if (!awayAt) awayAt = Date.now(); return; }
            var away = awayAt ? Date.now() - awayAt : 0;
            awayAt = 0;
            if (away > 5 * 60 * 1000 && S('sync.enabled', true) && S('sync.onResume', true)) {
              sync('resume', null);
            }
          } catch (e) { log('app state failed ' + e); }
        });
      }
    } catch (e) { log('no app state ' + e); }
    log('ready');
  } catch (e) {
    log('init failed ' + e);
  }
})();
