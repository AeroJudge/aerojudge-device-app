(function () {
  'use strict';

  var busy = false;
  var originalIconHtml = null;
  var failsafeTimer = null;
  var styleInjected = false;

  function injectStyle() {
    if (styleInjected) {
      return;
    }
    var style = document.createElement('style');
    style.id = 'aerojudge-fab-sync-feedback-style';
    style.textContent = [
      '@keyframes aerojudge-fab-sync-spin {',
      '  from { transform: rotate(0deg); }',
      '  to { transform: rotate(360deg); }',
      '}',
      '.aerojudge-fab-sync-busy .aerojudge-fab-sync-spinner {',
      '  animation: aerojudge-fab-sync-spin 1s linear infinite;',
      '  display: inline-block;',
      '}'
    ].join('\n');
    document.head.appendChild(style);
    styleInjected = true;
  }

  function getMainButton() {
    var fab = document.querySelector('.fixed-action-btn');
    if (!fab) {
      return null;
    }
    return fab.querySelector('a.btn-floating.btn-large');
  }

  function closeFab() {
    var fab = document.querySelector('.fixed-action-btn');
    if (!fab || !window.M || !M.FloatingActionButton ||
        typeof M.FloatingActionButton.getInstance !== 'function') {
      return;
    }
    var instance = M.FloatingActionButton.getInstance(fab);
    if (instance && typeof instance.close === 'function') {
      instance.close();
    }
  }

  function clearFailsafe() {
    if (failsafeTimer) {
      window.clearTimeout(failsafeTimer);
      failsafeTimer = null;
    }
  }

  function start() {
    var button = getMainButton();
    if (!button) {
      return;
    }

    closeFab();
    injectStyle();

    if (!busy) {
      originalIconHtml = button.innerHTML;
      button.innerHTML = '<i class="large material-icons aerojudge-fab-sync-spinner">sync</i>';
      button.classList.add('aerojudge-fab-sync-busy');
      button.style.pointerEvents = 'none';
      button.setAttribute('aria-disabled', 'true');
      busy = true;
    }

    clearFailsafe();
    failsafeTimer = window.setTimeout(finish, 10000);
  }

  function finish() {
    var button = getMainButton();

    clearFailsafe();

    if (!busy) {
      return;
    }

    if (button) {
      button.innerHTML = originalIconHtml || '<i class="large material-icons">menu</i>';
      button.classList.remove('aerojudge-fab-sync-busy');
      button.style.pointerEvents = '';
      button.removeAttribute('aria-disabled');
    }

    originalIconHtml = null;
    busy = false;
  }

  window.AeroJudgeFabSyncFeedback = {
    start: start,
    finish: finish
  };
})();
