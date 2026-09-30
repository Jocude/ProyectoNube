/**
 * ============================================================
 *  Cloud Storage — Aplicación de Página Única (SPA)
 * ============================================================
 *  • Sesión con cookie HttpOnly (el JavaScript nunca ve el token)
 *  • Archivos y carpetas: subir, descargar, vista previa, renombrar,
 *    mover, buscar, ordenar y vista de cuadrícula o lista
 *  • Enlaces compartidos y papelera
 *  • Panel B2B (solo administradores)
 *
 *  Todo el HTML dinámico se construye con createElement (función h):
 *  los nombres de archivo nunca se interpretan como HTML.
 * ============================================================
 */
document.addEventListener('DOMContentLoaded', () => {
  'use strict';

  const API = '/api';
  const $ = (id) => document.getElementById(id);

  // ──────────────────────────────────────────────
  //  Referencias al DOM
  // ──────────────────────────────────────────────

  const el = {
    toasts: $('toast-container'),
    // Autenticación
    authSection: $('auth-section'),
    authTabs: document.querySelectorAll('.auth-tab'),
    loginForm: $('login-form'),
    registerForm: $('register-form'),
    // Panel
    dashboard: $('dashboard-section'),
    greeting: $('user-greeting'),
    gearBtn: $('b2b-settings-btn'),
    logoutBtn: $('logout-btn'),
    viewTabs: document.querySelectorAll('.view-tab'),
    trashBadge: $('trash-count'),
    // Vista de archivos
    filesView: $('files-view'),
    filesTitle: $('files-title'),
    fileCount: $('file-count'),
    uploadZone: $('upload-zone'),
    uploadBtn: $('upload-btn'),
    fileInput: $('file-input'),
    uploadProgress: $('upload-progress'),
    progressFill: document.querySelector('#upload-progress .progress-fill'),
    progressText: document.querySelector('#upload-progress .progress-text'),
    newFolderBtn: $('new-folder-btn'),
    searchInput: $('search-input'),
    sortSelect: $('sort-select'),
    layoutBtns: document.querySelectorAll('.view-toggle-btn'),
    breadcrumbs: $('breadcrumbs-container'),
    grid: $('files-grid'),
    emptyState: $('empty-state'),
    emptyIcon: $('empty-icon'),
    emptyTitle: $('empty-title'),
    emptyText: $('empty-text'),
    loading: $('loading-state'),
    storageUsed: $('storage-used-text'),
    storageQuota: $('storage-quota-text'),
    storagePercent: $('storage-percent-text'),
    storageFill: $('storage-quota-fill'),
    // Compartidos y papelera
    sharesView: $('shares-view'),
    sharesList: $('shares-list'),
    sharesEmpty: $('shares-empty'),
    sharesCount: $('shares-count'),
    trashView: $('trash-view'),
    trashList: $('trash-list'),
    trashEmpty: $('trash-empty'),
    trashTotal: $('trash-total'),
    emptyTrashBtn: $('empty-trash-btn'),
    // Modales
    previewModal: $('preview-modal'),
    previewTitle: $('preview-title'),
    previewBody: $('preview-body'),
    dialogModal: $('dialog-modal'),
    dialogForm: $('dialog-form'),
    dialogTitle: $('dialog-title'),
    dialogMessage: $('dialog-message'),
    dialogInputGroup: $('dialog-input-group'),
    dialogInputLabel: $('dialog-input-label'),
    dialogInput: $('dialog-input'),
    dialogConfirm: $('dialog-confirm'),
    shareModal: $('share-modal'),
    shareForm: $('share-form'),
    shareFileName: $('share-file-name'),
    shareExpiration: $('share-expiration'),
    shareMaxDownloads: $('share-max-downloads'),
    shareResult: $('share-result'),
    shareUrl: $('share-url'),
    shareCopy: $('share-copy'),
    shareCreate: $('share-create'),
    moveModal: $('move-modal'),
    moveFileName: $('move-file-name'),
    moveFolderList: $('move-folder-list'),
    // Panel B2B
    b2bLoginModal: $('b2b-login-modal'),
    b2bLoginForm: $('b2b-login-form'),
    b2bAdminPassword: $('b2b-admin-password'),
    b2bConfigModal: $('b2b-config-modal'),
    b2bConfigForm: $('b2b-config-form'),
    b2bHostStorage: $('b2b-host-storage'),
    b2bHostDb: $('b2b-host-db'),
    b2bLicenseKey: $('b2b-license-key'),
    b2bNewPassword: $('b2b-new-password'),
    b2bConfirmPassword: $('b2b-confirm-password'),
    // URL pública
    publicUrlContainer: $('public-url-container'),
    publicUrlText: $('public-url-text'),
  };

  // ──────────────────────────────────────────────
  //  Estado
  // ──────────────────────────────────────────────

  const state = {
    user: null,
    view: 'files',
    folderId: null,
    contents: null, // última respuesta de /folders/contents
    searchResults: null, // resultados de búsqueda (o null si no se busca)
    search: '',
    sort: loadPref('sort', 'name-asc'),
    layout: loadPref('layout', 'grid'),
    publicUrl: null,
    items: new Map(), // id → archivo/carpeta mostrado (para las acciones)
    b2bPassword: '',
  };

  // ──────────────────────────────────────────────
  //  Utilidades
  // ──────────────────────────────────────────────

  /**
   * Crea un elemento DOM. Los hijos de tipo texto se insertan como texto, nunca como HTML.
   * @example h('button', {class: 'btn', dataset: {id: 1}}, 'Aceptar')
   */
  function h(tag, attrs = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(attrs)) {
      if (value === null || value === undefined || value === false) continue;
      if (key === 'class') node.className = value;
      else if (key === 'dataset') Object.assign(node.dataset, value);
      else node.setAttribute(key, value === true ? '' : value);
    }
    for (const child of children.flat()) {
      if (child === null || child === undefined || child === false) continue;
      node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return node;
  }

  /** Preferencias de interfaz en localStorage (puede no estar disponible). */
  function loadPref(key, fallback) {
    try {
      return localStorage.getItem(`pref.${key}`) || fallback;
    } catch (_) {
      return fallback;
    }
  }

  function savePref(key, value) {
    try {
      localStorage.setItem(`pref.${key}`, value);
    } catch (_) { /* sin almacenamiento: la preferencia no se recuerda */ }
  }

  function formatFileSize(bytes) {
    if (!bytes) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
    const size = bytes / Math.pow(1024, i);
    return i === 0 ? `${size} B` : `${size.toFixed(size >= 100 ? 0 : 1).replace('.', ',')} ${units[i]}`;
  }

  function formatDate(value) {
    return new Date(value).toLocaleString('es-ES', {
      day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  }

  /** Porcentaje legible: "< 0,1 %" en vez de "0.0 %" cuando se usa muy poco. */
  function formatPercent(pct) {
    if (pct <= 0) return '0 %';
    if (pct < 0.1) return '< 0,1 %';
    return `${pct.toFixed(1).replace('.', ',')} %`;
  }

  function getFileIcon(contentType, fileName) {
    const mime = (contentType || '').toLowerCase();
    const ext = (fileName || '').split('.').pop().toLowerCase();
    if (mime === 'application/pdf' || ext === 'pdf') return '📄';
    if (mime.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'svg', 'webp', 'bmp'].includes(ext)) return '🖼️';
    if (mime.startsWith('video/') || ['mp4', 'avi', 'mov', 'mkv', 'webm'].includes(ext)) return '🎬';
    if (mime.startsWith('audio/') || ['mp3', 'wav', 'ogg', 'flac', 'aac'].includes(ext)) return '🎵';
    if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return '📦';
    if (['xls', 'xlsx', 'csv', 'ods'].includes(ext)) return '📊';
    if (['doc', 'docx', 'odt'].includes(ext)) return '📝';
    if (['ppt', 'pptx', 'odp'].includes(ext)) return '📽️';
    if (ext === 'txt' || mime === 'text/plain') return '📃';
    return '📎';
  }

  function setButtonLoading(button, loading) {
    if (!button) return;
    if (loading) {
      button.dataset.originalText = button.textContent;
      button.disabled = true;
      button.replaceChildren(h('span', { class: 'spinner', 'aria-hidden': 'true' }), ' Cargando…');
    } else {
      button.disabled = false;
      button.textContent = button.dataset.originalText || button.textContent;
    }
  }

  function debounce(fn, ms) {
    let timer;
    return (...args) => {
      clearTimeout(timer);
      timer = setTimeout(() => fn(...args), ms);
    };
  }

  async function copyToClipboard(text) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch (_) {
      return false; // p. ej. en HTTP sin localhost el navegador no permite el portapapeles
    }
  }

  // ──────────────────────────────────────────────
  //  Notificaciones (toasts)
  // ──────────────────────────────────────────────

  function showToast(message, type = 'info') {
    const icons = { success: '✅', error: '❌', info: 'ℹ️' };
    const closeBtn = h('button', { class: 'toast-close', 'aria-label': 'Cerrar aviso' }, '×');
    const toast = h('div', { class: `toast toast-${type}`, role: type === 'error' ? 'alert' : 'status' },
      h('span', { class: 'toast-icon', 'aria-hidden': 'true' }, icons[type] || icons.info),
      h('span', { class: 'toast-message' }, message),
      closeBtn);
    el.toasts.appendChild(toast);

    const remove = () => {
      if (!toast.parentNode) return;
      toast.style.animation = 'fadeOut 0.3s ease forwards';
      toast.addEventListener('animationend', () => toast.remove());
    };
    const timer = setTimeout(remove, type === 'error' ? 6000 : 4000);
    closeBtn.addEventListener('click', () => { clearTimeout(timer); remove(); });
  }

  // ──────────────────────────────────────────────
  //  Comunicación con la API
  // ──────────────────────────────────────────────

  class ApiError extends Error {
    constructor(message, status) {
      super(message);
      this.status = status;
    }
  }

  /** Cabeceras comunes: X-Requested-With es la protección anti-CSRF que exige el backend. */
  const BASE_HEADERS = { 'X-Requested-With': 'XMLHttpRequest' };

  async function readError(response) {
    try {
      const data = await response.json();
      return data.error || data.message || 'Ha ocurrido un error inesperado';
    } catch (_) {
      return 'Ha ocurrido un error inesperado';
    }
  }

  /**
   * Petición a la API. La cookie de sesión la envía el navegador automáticamente.
   * @returns {Promise<any>} el JSON de respuesta, o null si no hay contenido
   */
  async function api(path, { method = 'GET', body, headers = {} } = {}) {
    const options = { method, credentials: 'same-origin', headers: { ...BASE_HEADERS, ...headers } };
    if (body instanceof FormData) {
      options.body = body;
    } else if (body !== undefined) {
      options.headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body);
    }

    const response = await fetch(API + path, options);
    // En /auth un 401 significa credenciales incorrectas, no sesión caducada
    if (response.status === 401 && !path.startsWith('/auth/')) {
      onSessionExpired();
      throw new ApiError('Tu sesión ha caducado. Inicia sesión de nuevo.', 401);
    }
    if (!response.ok) throw new ApiError(await readError(response), response.status);
    if (response.status === 204) return null;
    return response.json();
  }

  /** Descarga el contenido de un archivo como Blob. */
  async function apiBlob(path) {
    const response = await fetch(API + path, { credentials: 'same-origin', headers: BASE_HEADERS });
    if (response.status === 401) {
      onSessionExpired();
      throw new ApiError('Tu sesión ha caducado. Inicia sesión de nuevo.', 401);
    }
    if (!response.ok) throw new ApiError(await readError(response), response.status);
    return response.blob();
  }

  /** Muestra el error salvo que sea una sesión caducada (ya se avisa en onSessionExpired). */
  function reportError(error, fallback) {
    if (error.status !== 401) showToast(error.message || fallback, 'error');
  }

  // ──────────────────────────────────────────────
  //  Modales: Escape, clic en el fondo y foco
  // ──────────────────────────────────────────────

  const openModals = [];

  function openModal(modal, { onClose } = {}) {
    modal.classList.remove('hidden');
    openModals.push({ modal, onClose, returnFocus: document.activeElement });
    const focusTarget = modal.querySelector('input:not([type=hidden]):not([readonly]), select, textarea, button[type=submit]')
      || modal.querySelector('button');
    if (focusTarget) setTimeout(() => focusTarget.focus(), 50);
  }

  function closeModal(modal) {
    const index = openModals.findIndex((m) => m.modal === modal);
    if (index === -1) return;
    const [entry] = openModals.splice(index, 1);
    modal.classList.add('hidden');
    if (entry.onClose) entry.onClose();
    if (entry.returnFocus && document.contains(entry.returnFocus)) entry.returnFocus.focus();
  }

  function closeAllModals() {
    [...openModals].reverse().forEach(({ modal }) => closeModal(modal));
  }

  // Todos los modales: clic en el fondo o en un botón de cierre los cierra
  document.querySelectorAll('.preview-modal').forEach((modal) => {
    modal.addEventListener('click', (e) => {
      if (e.target === modal || e.target.closest('[data-close], .btn-close')) closeModal(modal);
    });
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && openModals.length > 0) {
      e.preventDefault();
      closeModal(openModals[openModals.length - 1].modal);
    }
  });

  // ── Diálogo genérico (sustituye a confirm() y prompt() del navegador) ──

  let dialogResolve = null;

  /**
   * Muestra un diálogo de confirmación o de texto.
   * @returns {Promise<boolean|string|null>} true / el texto introducido si se acepta; null si se cancela
   */
  function dialog({ title, message = '', confirmText = 'Aceptar', danger = false, input = null }) {
    el.dialogTitle.textContent = title;
    el.dialogMessage.textContent = message;
    el.dialogMessage.classList.toggle('hidden', !message);
    el.dialogConfirm.textContent = confirmText;
    el.dialogConfirm.classList.toggle('btn-danger', danger);
    el.dialogConfirm.classList.toggle('btn-primary', !danger);
    el.dialogInputGroup.classList.toggle('hidden', !input);
    el.dialogInput.required = !!input;
    if (input) {
      el.dialogInputLabel.textContent = input.label;
      el.dialogInput.value = input.value || '';
      el.dialogInput.placeholder = input.placeholder || '';
    }

    return new Promise((resolve) => {
      dialogResolve = resolve;
      openModal(el.dialogModal, {
        onClose: () => {
          if (dialogResolve) dialogResolve(null);
          dialogResolve = null;
        },
      });
      if (input) {
        // Seleccionar el nombre sin la extensión, como hacen los exploradores de archivos
        setTimeout(() => {
          const dot = el.dialogInput.value.lastIndexOf('.');
          el.dialogInput.setSelectionRange(0, dot > 0 ? dot : el.dialogInput.value.length);
        }, 60);
      }
    });
  }

  el.dialogForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const value = el.dialogInputGroup.classList.contains('hidden') ? true : el.dialogInput.value.trim();
    if (value === '') return;
    const resolve = dialogResolve;
    dialogResolve = null;
    closeModal(el.dialogModal);
    if (resolve) resolve(value);
  });

  // ──────────────────────────────────────────────
  //  Sesión y autenticación
  // ──────────────────────────────────────────────

  // Mostrar u ocultar contraseña
  document.querySelectorAll('.password-toggle').forEach((btn) => {
    btn.addEventListener('click', () => {
      const input = $(btn.dataset.target);
      const show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      btn.textContent = show ? '🙈' : '👁️';
      const label = show ? 'Ocultar contraseña' : 'Mostrar contraseña';
      btn.setAttribute('aria-label', label);
      btn.title = label;
    });
  });

  el.authTabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      el.authTabs.forEach((t) => t.classList.toggle('active', t === tab));
      el.loginForm.classList.toggle('hidden', tab.dataset.tab !== 'login');
      el.registerForm.classList.toggle('hidden', tab.dataset.tab !== 'register');
    });
  });

  async function submitAuth(form, path, body, welcome) {
    const button = form.querySelector('.btn-primary');
    setButtonLoading(button, true);
    try {
      const user = await api(path, { method: 'POST', body });
      form.reset();
      enterApp(user);
      showToast(welcome(user), 'success');
    } catch (error) {
      showToast(error.message, 'error');
    } finally {
      setButtonLoading(button, false);
    }
  }

  el.loginForm.addEventListener('submit', (e) => {
    e.preventDefault();
    submitAuth(el.loginForm, '/auth/login', {
      email: $('login-email').value.trim(),
      password: $('login-password').value,
    }, (user) => `¡Hola de nuevo, ${user.name}!`);
  });

  el.registerForm.addEventListener('submit', (e) => {
    e.preventDefault();
    submitAuth(el.registerForm, '/auth/register', {
      name: $('register-name').value.trim(),
      email: $('register-email').value.trim(),
      password: $('register-password').value,
    }, (user) => (user.role === 'ROLE_ADMIN'
      ? '¡Cuenta creada! Eres el administrador de este servidor.'
      : '¡Cuenta creada correctamente!'));
  });

  el.logoutBtn.addEventListener('click', async () => {
    try {
      await api('/auth/logout', { method: 'POST' });
    } catch (_) { /* aunque falle, se cierra la sesión en la interfaz */ }
    leaveApp();
    showToast('Sesión cerrada', 'info');
  });

  function enterApp(user) {
    state.user = user;
    el.greeting.textContent = `Hola, ${user.name}`;
    // El panel B2B solo existe para el administrador (el backend también lo exige)
    el.gearBtn.classList.toggle('hidden', user.role !== 'ROLE_ADMIN');
    el.authSection.classList.add('hidden');
    el.dashboard.classList.remove('hidden');
    state.folderId = null;
    state.search = '';
    el.searchInput.value = '';
    switchView('files');
    refreshTrashBadge();
  }

  function leaveApp() {
    state.user = null;
    state.b2bPassword = '';
    closeAllModals();
    el.dashboard.classList.add('hidden');
    el.authSection.classList.remove('hidden');
  }

  let sessionExpiredNotified = false;
  function onSessionExpired() {
    if (!state.user) return;
    leaveApp();
    if (!sessionExpiredNotified) {
      sessionExpiredNotified = true;
      showToast('Tu sesión ha caducado. Inicia sesión de nuevo.', 'info');
      setTimeout(() => { sessionExpiredNotified = false; }, 3000);
    }
  }

  // ──────────────────────────────────────────────
  //  Navegación entre vistas
  // ──────────────────────────────────────────────

  const views = { files: el.filesView, shares: el.sharesView, trash: el.trashView };

  function switchView(view) {
    state.view = view;
    el.viewTabs.forEach((tab) => {
      const active = tab.dataset.view === view;
      tab.classList.toggle('active', active);
      tab.setAttribute('aria-current', active ? 'page' : 'false');
    });
    Object.entries(views).forEach(([name, section]) => section.classList.toggle('hidden', name !== view));
    if (view === 'files') loadFiles();
    else if (view === 'shares') loadShares();
    else loadTrash();
  }

  el.viewTabs.forEach((tab) => tab.addEventListener('click', () => switchView(tab.dataset.view)));

  // ──────────────────────────────────────────────
  //  Vista: Mis archivos
  // ──────────────────────────────────────────────

  async function loadFiles() {
    el.loading.classList.remove('hidden');
    try {
      if (state.search) {
        const page = await api(`/files?size=100&search=${encodeURIComponent(state.search)}`);
        state.searchResults = page.content;
      } else {
        state.searchResults = null;
        state.contents = await api(`/folders/contents${state.folderId ? `?folderId=${state.folderId}` : ''}`);
        updateQuota(state.contents.storageUsed, state.contents.storageQuota);
      }
      renderFiles();
    } catch (error) {
      reportError(error, 'Error al cargar los archivos');
      // Si la carpeta ya no existe, volver a la raíz
      if (error.status === 404 && state.folderId) {
        state.folderId = null;
        loadFiles();
      }
    } finally {
      el.loading.classList.add('hidden');
    }
  }

  function updateQuota(used, quota) {
    const pct = quota > 0 ? (used / quota) * 100 : 0;
    el.storageUsed.textContent = formatFileSize(used);
    el.storageQuota.textContent = formatFileSize(quota);
    el.storagePercent.textContent = `${formatPercent(pct)} usado`;
    // Con algo ocupado, la barra siempre se ve aunque sea muy poco
    el.storageFill.style.width = used > 0 ? `${Math.min(Math.max(pct, 0.5), 100)}%` : '0';
    el.storageFill.classList.toggle('warning', pct >= 70 && pct < 90);
    el.storageFill.classList.toggle('danger', pct >= 90);
  }

  const SORTERS = {
    'name-asc': (a, b) => nameOf(a).localeCompare(nameOf(b), 'es', { numeric: true }),
    'name-desc': (a, b) => nameOf(b).localeCompare(nameOf(a), 'es', { numeric: true }),
    'date-desc': (a, b) => new Date(b.uploadedAt) - new Date(a.uploadedAt),
    'date-asc': (a, b) => new Date(a.uploadedAt) - new Date(b.uploadedAt),
    'size-desc': (a, b) => b.fileSize - a.fileSize,
    'size-asc': (a, b) => a.fileSize - b.fileSize,
  };

  function nameOf(item) {
    return item.originalName || item.name || '';
  }

  function renderFiles() {
    const searching = state.searchResults !== null;
    const folders = searching ? [] : [...state.contents.folders].sort(SORTERS['name-asc']);
    const files = [...(searching ? state.searchResults : state.contents.files)].sort(SORTERS[state.sort]);

    state.items.clear();
    folders.forEach((f) => state.items.set(f.id, { ...f, kind: 'folder' }));
    files.forEach((f) => state.items.set(f.id, { ...f, kind: 'file' }));

    const total = folders.length + files.length;
    el.fileCount.textContent = `${total} elemento${total === 1 ? '' : 's'}`;
    el.filesTitle.textContent = searching ? 'Resultados de búsqueda' : 'Mis archivos';
    el.grid.classList.toggle('list-view', state.layout === 'list');
    renderBreadcrumbs(searching);

    el.grid.replaceChildren(...folders.map(folderCard), ...files.map(fileCard));

    el.emptyState.classList.toggle('hidden', total > 0);
    if (total === 0) {
      if (searching) setEmpty('🔎', 'Sin resultados', `Ningún archivo contiene «${state.search}» en su nombre.`);
      else if (state.folderId) setEmpty('📂', 'Esta carpeta está vacía', 'Sube archivos aquí o arrástralos a la zona de subida.');
      else setEmpty('📭', 'No hay archivos aún', 'Sube tu primer archivo con el botón «Subir» o arrastrándolo a la zona de subida.');
    }
  }

  function setEmpty(icon, title, text) {
    el.emptyIcon.textContent = icon;
    el.emptyTitle.textContent = title;
    el.emptyText.textContent = text;
  }

  function actionButton(action, icon, label, extraClass = '') {
    return h('button', {
      class: `btn-action btn-icon ${extraClass}`.trim(),
      dataset: { action },
      title: label,
      'aria-label': label,
    }, icon);
  }

  function folderCard(folder) {
    return h('div', {
      class: 'file-card folder-card',
      tabindex: '0',
      dataset: { id: folder.id },
      'aria-label': `Abrir carpeta ${folder.name}`,
    },
    h('div', { class: 'file-card-header' },
      h('div', { class: 'file-icon', 'aria-hidden': 'true' }, '📁'),
      h('div', { class: 'file-info' },
        h('span', { class: 'file-name', title: folder.name }, folder.name),
        h('span', { class: 'file-meta' }, 'Carpeta'))),
    h('div', { class: 'file-card-actions' },
      actionButton('rename', '✏️', 'Renombrar'),
      actionButton('delete-folder', '🗑️', 'Eliminar carpeta', 'btn-delete')));
  }

  function fileCard(file) {
    return h('div', {
      class: 'file-card file-card-item',
      tabindex: '0',
      dataset: { id: file.id },
      'aria-label': `Vista previa de ${file.originalName}`,
    },
    h('div', { class: 'file-card-header' },
      h('div', { class: 'file-icon', 'aria-hidden': 'true' }, getFileIcon(file.contentType, file.originalName)),
      h('div', { class: 'file-info' },
        h('span', { class: 'file-name', title: file.originalName }, file.originalName),
        h('span', { class: 'file-meta' }, `${formatFileSize(file.fileSize)} · ${formatDate(file.uploadedAt)}`))),
    h('div', { class: 'file-card-actions' },
      actionButton('download', '⬇️', 'Descargar'),
      actionButton('share', '🔗', 'Compartir'),
      actionButton('rename', '✏️', 'Renombrar'),
      actionButton('move', '📂', 'Mover'),
      actionButton('trash', '🗑️', 'Mover a la papelera', 'btn-delete')));
  }

  function renderBreadcrumbs(searching) {
    const crumbs = [];
    if (searching) {
      crumbs.push(h('span', { class: 'breadcrumb-item', dataset: { clearSearch: '1' }, tabindex: '0', role: 'button' }, '← Volver a mis archivos'));
    } else {
      const trail = state.contents.breadcrumbs || [];
      crumbs.push(h('span', {
        class: `breadcrumb-item${trail.length === 0 ? ' active' : ''}`,
        dataset: { folderId: '' }, tabindex: '0', role: 'button',
      }, 'Inicio'));
      trail.forEach((bc, i) => {
        const last = i === trail.length - 1;
        crumbs.push(h('span', { class: 'breadcrumb-separator', 'aria-hidden': 'true' }, '/'));
        crumbs.push(h('span', {
          class: `breadcrumb-item${last ? ' active' : ''}`,
          dataset: { folderId: bc.id }, tabindex: '0', role: 'button',
        }, bc.name));
      });
    }
    el.breadcrumbs.replaceChildren(...crumbs);
  }

  function activateBreadcrumb(target) {
    const crumb = target.closest('.breadcrumb-item');
    if (!crumb || crumb.classList.contains('active')) return;
    if (crumb.dataset.clearSearch) {
      clearSearch();
      return;
    }
    state.folderId = crumb.dataset.folderId || null;
    loadFiles();
  }

  el.breadcrumbs.addEventListener('click', (e) => activateBreadcrumb(e.target));
  el.breadcrumbs.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      activateBreadcrumb(e.target);
    }
  });

  // Toda la tarjeta es clicable; los botones de acción tienen prioridad
  el.grid.addEventListener('click', (e) => {
    const card = e.target.closest('.file-card');
    if (!card) return;
    const item = state.items.get(card.dataset.id);
    const actionBtn = e.target.closest('[data-action]');
    if (actionBtn) {
      runAction(actionBtn.dataset.action, item);
    } else {
      openItem(item);
    }
  });

  el.grid.addEventListener('keydown', (e) => {
    if ((e.key === 'Enter' || e.key === ' ') && e.target.classList.contains('file-card')) {
      e.preventDefault();
      openItem(state.items.get(e.target.dataset.id));
    }
  });

  function openItem(item) {
    if (!item) return;
    if (item.kind === 'folder') {
      state.folderId = item.id;
      loadFiles();
    } else {
      previewFile(item);
    }
  }

  function runAction(action, item) {
    const actions = {
      download: () => downloadFile(item),
      share: () => openShareModal(item),
      rename: () => renameItem(item),
      move: () => openMoveModal(item),
      trash: () => trashFile(item),
      'delete-folder': () => deleteFolder(item),
    };
    if (item && actions[action]) actions[action]();
  }

  // ── Orden, vista y búsqueda ──

  el.sortSelect.value = state.sort;
  el.sortSelect.addEventListener('change', () => {
    state.sort = el.sortSelect.value;
    savePref('sort', state.sort);
    if (state.contents || state.searchResults) renderFiles();
  });

  function applyLayout() {
    el.layoutBtns.forEach((btn) => {
      const active = btn.dataset.layout === state.layout;
      btn.classList.toggle('active', active);
      btn.setAttribute('aria-pressed', String(active));
    });
    el.grid.classList.toggle('list-view', state.layout === 'list');
  }
  el.layoutBtns.forEach((btn) => btn.addEventListener('click', () => {
    state.layout = btn.dataset.layout;
    savePref('layout', state.layout);
    applyLayout();
  }));
  applyLayout();

  const runSearch = debounce(() => {
    state.search = el.searchInput.value.trim();
    loadFiles();
  }, 300);
  el.searchInput.addEventListener('input', runSearch);
  el.searchInput.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && el.searchInput.value) {
      e.stopPropagation();
      clearSearch();
    }
  });

  function clearSearch() {
    el.searchInput.value = '';
    state.search = '';
    loadFiles();
  }

  // ── Subida de archivos ──

  el.uploadZone.addEventListener('click', () => el.fileInput.click());
  el.uploadBtn.addEventListener('click', () => el.fileInput.click());
  el.fileInput.addEventListener('change', () => {
    const files = [...el.fileInput.files];
    el.fileInput.value = ''; // permitir volver a subir el mismo archivo
    if (files.length) uploadFiles(files);
  });

  ['dragenter', 'dragover'].forEach((type) => el.uploadZone.addEventListener(type, (e) => {
    e.preventDefault();
    el.uploadZone.classList.add('drag-over');
  }));
  ['dragleave', 'drop'].forEach((type) => el.uploadZone.addEventListener(type, (e) => {
    e.preventDefault();
    el.uploadZone.classList.remove('drag-over');
  }));
  el.uploadZone.addEventListener('drop', (e) => {
    const files = [...(e.dataTransfer.files || [])];
    if (files.length) uploadFiles(files);
  });

  /** Sube los archivos uno detrás de otro (comparten la barra de progreso). */
  async function uploadFiles(files) {
    const uploaded = [];
    for (const file of files) {
      const result = await uploadFile(file);
      if (result === 'ok') uploaded.push(file.name);
      if (result === 'session') return;
    }
    if (uploaded.length > 0) {
      showToast(uploaded.length === 1 ? `${uploaded[0]} subido correctamente` : `${uploaded.length} archivos subidos`, 'success');
      if (state.view === 'files') loadFiles();
    }
  }

  /** Sube un archivo con XMLHttpRequest para poder mostrar el progreso. */
  function uploadFile(file) {
    return new Promise((resolve) => {
      const form = new FormData();
      form.append('file', file);
      if (state.folderId && !state.search) form.append('folderId', state.folderId);

      el.uploadProgress.classList.remove('hidden');
      updateProgress(0, file.name);

      const xhr = new XMLHttpRequest();
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) updateProgress(Math.round((e.loaded / e.total) * 100), file.name);
      };
      xhr.onload = () => {
        el.uploadProgress.classList.add('hidden');
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve('ok');
          return;
        }
        if (xhr.status === 401) {
          onSessionExpired();
          resolve('session');
          return;
        }
        let message = 'Error al subir el archivo';
        try {
          message = JSON.parse(xhr.responseText).error || message;
        } catch (_) { /* respuesta no JSON (p. ej. límite del proxy) */ }
        if (xhr.status === 413) message = 'El archivo supera el tamaño máximo permitido';
        showToast(`${file.name}: ${message}`, 'error');
        resolve('error');
      };
      xhr.onerror = () => {
        el.uploadProgress.classList.add('hidden');
        showToast(`Error de conexión al subir ${file.name}`, 'error');
        resolve('error');
      };
      xhr.open('POST', `${API}/files/upload`);
      xhr.withCredentials = true;
      xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
      xhr.send(form);
    });
  }

  function updateProgress(percent, name) {
    el.progressFill.style.width = `${percent}%`;
    el.progressText.textContent = `${name} — ${percent}%`;
  }

  // ── Acciones sobre archivos y carpetas ──

  async function downloadFile(file) {
    try {
      const blob = await apiBlob(`/files/download/${file.id}`);
      const url = URL.createObjectURL(blob);
      const link = h('a', { href: url, download: file.originalName });
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      reportError(error, 'Error al descargar el archivo');
    }
  }

  async function renameItem(item) {
    const isFolder = item.kind === 'folder';
    const current = nameOf(item);
    const name = await dialog({
      title: isFolder ? 'Renombrar carpeta' : 'Renombrar archivo',
      confirmText: 'Renombrar',
      input: { label: 'Nuevo nombre', value: current },
    });
    if (!name || name === current) return;
    try {
      await api(`/${isFolder ? 'folders' : 'files'}/${item.id}/rename`, { method: 'PATCH', body: { name } });
      showToast('Nombre cambiado', 'success');
      loadFiles();
    } catch (error) {
      reportError(error, 'No se pudo renombrar');
    }
  }

  async function trashFile(file) {
    const ok = await dialog({
      title: 'Mover a la papelera',
      message: `«${file.originalName}» irá a la papelera. Podrás restaurarlo desde allí.`,
      confirmText: 'Mover a la papelera',
      danger: true,
    });
    if (!ok) return;
    try {
      await api(`/files/${file.id}`, { method: 'DELETE' });
      showToast('Archivo movido a la papelera', 'success');
      loadFiles();
      refreshTrashBadge();
    } catch (error) {
      reportError(error, 'No se pudo mover a la papelera');
    }
  }

  async function deleteFolder(folder) {
    const ok = await dialog({
      title: 'Eliminar carpeta',
      message: `Se eliminará «${folder.name}» y sus subcarpetas. Los archivos que contienen irán a la papelera.`,
      confirmText: 'Eliminar carpeta',
      danger: true,
    });
    if (!ok) return;
    try {
      await api(`/folders/${folder.id}`, { method: 'DELETE' });
      showToast('Carpeta eliminada; sus archivos están en la papelera', 'success');
      loadFiles();
      refreshTrashBadge();
    } catch (error) {
      reportError(error, 'No se pudo eliminar la carpeta');
    }
  }

  el.newFolderBtn.addEventListener('click', async () => {
    const name = await dialog({
      title: 'Nueva carpeta',
      confirmText: 'Crear carpeta',
      input: { label: 'Nombre de la carpeta', placeholder: 'Nueva carpeta' },
    });
    if (!name) return;
    try {
      await api('/folders', { method: 'POST', body: { name, parentId: state.folderId } });
      showToast('Carpeta creada', 'success');
      if (state.search) clearSearch(); else loadFiles();
    } catch (error) {
      reportError(error, 'No se pudo crear la carpeta');
    }
  });

  // ── Vista previa ──

  let previewUrl = null;

  async function previewFile(file) {
    el.previewTitle.textContent = file.originalName;
    el.previewBody.replaceChildren(h('div', { class: 'spinner' }), h('p', { class: 'preview-loading' }, 'Cargando…'));
    openModal(el.previewModal, {
      onClose: () => {
        el.previewBody.replaceChildren();
        if (previewUrl) URL.revokeObjectURL(previewUrl);
        previewUrl = null;
      },
    });

    try {
      const blob = await apiBlob(`/files/preview/${file.id}`);
      if (el.previewModal.classList.contains('hidden')) return; // se cerró mientras cargaba
      previewUrl = URL.createObjectURL(blob);
      const mime = (blob.type || file.contentType || '').toLowerCase();
      const ext = file.originalName.split('.').pop().toLowerCase();

      let content;
      if (mime.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'svg', 'webp', 'bmp'].includes(ext)) {
        content = h('img', { src: previewUrl, alt: file.originalName });
      } else if (mime.startsWith('video/') || ['mp4', 'webm', 'mov'].includes(ext)) {
        content = h('video', { src: previewUrl, controls: true });
      } else if (mime.startsWith('audio/') || ['mp3', 'wav', 'ogg', 'aac'].includes(ext)) {
        content = h('audio', { src: previewUrl, controls: true });
      } else if (mime === 'application/pdf' || ext === 'pdf') {
        content = h('iframe', { src: previewUrl, title: file.originalName });
      } else if (mime.startsWith('text/') || ['txt', 'csv', 'json', 'xml', 'md', 'js', 'html', 'css', 'log'].includes(ext)) {
        content = h('pre', {}, await blob.text()); // como texto: el HTML no se interpreta
      } else {
        content = h('div', { class: 'preview-unavailable' },
          h('p', { class: 'preview-unavailable-icon' }, getFileIcon(mime, file.originalName)),
          h('p', {}, 'No hay vista previa para este tipo de archivo.'),
          h('button', { class: 'btn btn-primary', type: 'button', dataset: { previewDownload: '1' } }, '⬇️ Descargar'));
      }
      el.previewBody.replaceChildren(content);
      const dl = el.previewBody.querySelector('[data-preview-download]');
      if (dl) dl.addEventListener('click', () => downloadFile(file));
    } catch (error) {
      el.previewBody.replaceChildren(h('p', { class: 'preview-error' }, error.message));
    }
  }

  // ── Compartir ──

  let shareTarget = null;

  function openShareModal(file) {
    shareTarget = file;
    el.shareFileName.textContent = file.originalName;
    el.shareForm.reset();
    el.shareResult.classList.add('hidden');
    el.shareCreate.classList.remove('hidden');
    openModal(el.shareModal);
  }

  function shareLink(token) {
    // Con el túnel activo se usa la URL pública; si no, la dirección actual
    const base = (state.publicUrl || window.location.origin).replace(/\/$/, '');
    return `${base}${API}/share/${token}`;
  }

  el.shareForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!shareTarget) return;
    const maxDownloads = el.shareMaxDownloads.value ? Number(el.shareMaxDownloads.value) : null;
    setButtonLoading(el.shareCreate, true);
    try {
      const share = await api(`/share/files/${shareTarget.id}`, {
        method: 'POST',
        body: { expirationHours: Number(el.shareExpiration.value), maxDownloads },
      });
      el.shareUrl.value = shareLink(share.token);
      el.shareResult.classList.remove('hidden');
      el.shareCreate.classList.add('hidden');
      el.shareUrl.focus();
      el.shareUrl.select();
      if (await copyToClipboard(el.shareUrl.value)) showToast('Enlace creado y copiado al portapapeles', 'success');
      else showToast('Enlace creado', 'success');
    } catch (error) {
      reportError(error, 'No se pudo crear el enlace');
    } finally {
      setButtonLoading(el.shareCreate, false);
    }
  });

  el.shareCopy.addEventListener('click', async () => {
    el.shareUrl.select();
    showToast(await copyToClipboard(el.shareUrl.value) ? 'Enlace copiado' : 'Selecciona el enlace y cópialo con Ctrl+C', 'info');
  });

  // ── Mover ──

  async function openMoveModal(file) {
    el.moveFileName.textContent = file.originalName;
    el.moveFolderList.replaceChildren(h('div', { class: 'spinner' }));
    openModal(el.moveModal);
    try {
      const folders = await api('/folders');
      const byId = new Map(folders.map((f) => [f.id, f]));
      const pathOf = (folder) => {
        const parts = [];
        for (let f = folder, guard = 0; f && guard < 50; f = byId.get(f.parentId), guard++) parts.unshift(f.name);
        return parts.join(' / ');
      };
      const options = [{ id: null, label: '🏠 Inicio' },
        ...folders.map((f) => ({ id: f.id, label: `📁 ${pathOf(f)}` }))
          .sort((a, b) => a.label.localeCompare(b.label, 'es', { numeric: true }))];
      el.moveFolderList.replaceChildren(...options.map((option) => {
        const current = (option.id || null) === (file.folderId || null);
        return h('button', {
          type: 'button',
          class: `folder-option${current ? ' current' : ''}`,
          disabled: current,
          dataset: { folderId: option.id || '' },
        }, option.label, current ? ' (ubicación actual)' : '');
      }));
    } catch (error) {
      closeModal(el.moveModal);
      reportError(error, 'No se pudieron cargar las carpetas');
    }
    el.moveFolderList.onclick = async (e) => {
      const option = e.target.closest('.folder-option');
      if (!option || option.disabled) return;
      try {
        await api(`/files/${file.id}/move`, { method: 'PATCH', body: { folderId: option.dataset.folderId || null } });
        closeModal(el.moveModal);
        showToast('Archivo movido', 'success');
        loadFiles();
      } catch (error) {
        reportError(error, 'No se pudo mover el archivo');
      }
    };
  }

  // ──────────────────────────────────────────────
  //  Vista: Compartidos
  // ──────────────────────────────────────────────

  async function loadShares() {
    try {
      const shares = await api('/share');
      el.sharesCount.textContent = `${shares.length} enlace${shares.length === 1 ? '' : 's'}`;
      el.sharesEmpty.classList.toggle('hidden', shares.length > 0);
      el.sharesList.replaceChildren(...shares.map(shareRow));
    } catch (error) {
      reportError(error, 'Error al cargar los enlaces');
    }
  }

  function shareRow(share) {
    const downloads = share.maxDownloads ? `${share.downloadCount}/${share.maxDownloads}` : `${share.downloadCount}`;
    const row = h('div', { class: 'list-row' },
      h('div', { class: 'file-icon', 'aria-hidden': 'true' }, '🔗'),
      h('div', { class: 'file-info' },
        h('span', { class: 'file-name', title: share.fileName }, share.fileName),
        h('span', { class: 'file-meta' },
          `Caduca: ${formatDate(share.expiresAt)} · Descargas: ${downloads}`)),
      h('span', { class: `status-badge ${share.valid ? 'ok' : 'expired'}` }, share.valid ? 'Activo' : 'Caducado'),
      h('div', { class: 'row-actions' },
        share.valid ? h('button', { class: 'btn-action', dataset: { copy: '1' } }, '📋 Copiar') : null,
        h('button', { class: 'btn-action btn-delete', dataset: { revoke: '1' } }, 'Revocar')));

    row.addEventListener('click', async (e) => {
      if (e.target.closest('[data-copy]')) {
        showToast(await copyToClipboard(shareLink(share.token)) ? 'Enlace copiado' : shareLink(share.token), 'info');
      } else if (e.target.closest('[data-revoke]')) {
        const ok = await dialog({
          title: 'Revocar enlace',
          message: `El enlace de «${share.fileName}» dejará de funcionar inmediatamente.`,
          confirmText: 'Revocar',
          danger: true,
        });
        if (!ok) return;
        try {
          await api(`/share/${share.id}`, { method: 'DELETE' });
          showToast('Enlace revocado', 'success');
          loadShares();
        } catch (error) {
          reportError(error, 'No se pudo revocar el enlace');
        }
      }
    });
    return row;
  }

  // ──────────────────────────────────────────────
  //  Vista: Papelera
  // ──────────────────────────────────────────────

  async function refreshTrashBadge() {
    try {
      const trash = await api('/trash');
      setTrashBadge(trash.length);
    } catch (_) { /* el contador es informativo */ }
  }

  function setTrashBadge(count) {
    el.trashBadge.textContent = String(count);
    el.trashBadge.classList.toggle('hidden', count === 0);
  }

  async function loadTrash() {
    try {
      const trash = await api('/trash');
      setTrashBadge(trash.length);
      const bytes = trash.reduce((sum, f) => sum + (f.fileSize || 0), 0);
      el.trashTotal.textContent = `${trash.length} archivo${trash.length === 1 ? '' : 's'} · ${formatFileSize(bytes)}`;
      el.emptyTrashBtn.disabled = trash.length === 0;
      el.trashEmpty.classList.toggle('hidden', trash.length > 0);
      el.trashList.replaceChildren(...trash.map(trashRow));
    } catch (error) {
      reportError(error, 'Error al cargar la papelera');
    }
  }

  function trashRow(file) {
    const row = h('div', { class: 'list-row' },
      h('div', { class: 'file-icon', 'aria-hidden': 'true' }, getFileIcon(file.contentType, file.originalName)),
      h('div', { class: 'file-info' },
        h('span', { class: 'file-name', title: file.originalName }, file.originalName),
        h('span', { class: 'file-meta' }, `${formatFileSize(file.fileSize)} · En la papelera desde ${formatDate(file.deletedAt)}`)),
      h('div', { class: 'row-actions' },
        h('button', { class: 'btn-action', dataset: { restore: '1' } }, '↩️ Restaurar'),
        h('button', { class: 'btn-action btn-delete', dataset: { destroy: '1' } }, 'Eliminar')));

    row.addEventListener('click', async (e) => {
      if (e.target.closest('[data-restore]')) {
        try {
          await api(`/trash/${file.id}/restore`, { method: 'POST' });
          showToast(`«${file.originalName}» restaurado`, 'success');
          loadTrash();
        } catch (error) {
          reportError(error, 'No se pudo restaurar');
        }
      } else if (e.target.closest('[data-destroy]')) {
        const ok = await dialog({
          title: 'Eliminar definitivamente',
          message: `«${file.originalName}» se borrará para siempre. Esta acción no se puede deshacer.`,
          confirmText: 'Eliminar para siempre',
          danger: true,
        });
        if (!ok) return;
        try {
          await api(`/trash/${file.id}`, { method: 'DELETE' });
          showToast('Archivo eliminado definitivamente', 'success');
          loadTrash();
        } catch (error) {
          reportError(error, 'No se pudo eliminar');
        }
      }
    });
    return row;
  }

  el.emptyTrashBtn.addEventListener('click', async () => {
    const ok = await dialog({
      title: 'Vaciar papelera',
      message: 'Todos los archivos de la papelera se borrarán para siempre y liberarán espacio. Esta acción no se puede deshacer.',
      confirmText: 'Vaciar papelera',
      danger: true,
    });
    if (!ok) return;
    setButtonLoading(el.emptyTrashBtn, true);
    try {
      const result = await api('/trash', { method: 'DELETE' });
      showToast(`${result.deleted} archivo${result.deleted === 1 ? '' : 's'} eliminado${result.deleted === 1 ? '' : 's'}`, 'success');
    } catch (error) {
      reportError(error, 'No se pudo vaciar la papelera');
    } finally {
      setButtonLoading(el.emptyTrashBtn, false);
      loadTrash();
    }
  });

  // ──────────────────────────────────────────────
  //  Panel B2B (solo administradores)
  // ──────────────────────────────────────────────

  el.gearBtn.addEventListener('click', () => {
    el.b2bAdminPassword.value = '';
    openModal(el.b2bLoginModal);
  });

  el.b2bLoginForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const password = el.b2bAdminPassword.value;
    if (!password) return;
    const button = el.b2bLoginForm.querySelector('.btn-primary');
    setButtonLoading(button, true);
    try {
      await api('/admin/config/verify', { method: 'POST', body: { password } });
      state.b2bPassword = password;
      closeModal(el.b2bLoginModal);
      const config = await api('/admin/config', { headers: { 'X-B2B-Admin-Password': password } });
      el.b2bHostStorage.value = config.HOST_STORAGE_PATH || '';
      el.b2bHostDb.value = config.HOST_DB_PATH || '';
      el.b2bLicenseKey.value = config.APP_LICENSE_KEY || '';
      openModal(el.b2bConfigModal, { onClose: () => el.b2bConfigForm.reset() });
    } catch (error) {
      reportError(error, 'Contraseña de administrador incorrecta');
    } finally {
      setButtonLoading(button, false);
    }
  });

  el.b2bConfigForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const newPass = el.b2bNewPassword.value;
    if (newPass && newPass !== el.b2bConfirmPassword.value) {
      showToast('La nueva contraseña y la confirmación no coinciden', 'error');
      return;
    }
    const button = el.b2bConfigForm.querySelector('.btn-primary');
    setButtonLoading(button, true);
    try {
      await api('/admin/config', {
        method: 'POST',
        headers: { 'X-B2B-Admin-Password': state.b2bPassword },
        body: { licenseKey: el.b2bLicenseKey.value.trim(), newAdminPassword: newPass || null },
      });
      if (newPass) state.b2bPassword = newPass;
      closeModal(el.b2bConfigModal);
      showToast('Configuración guardada', 'success');
      if (state.view === 'files') loadFiles(); // la cuota puede haber cambiado con la licencia
    } catch (error) {
      reportError(error, 'Error al guardar los cambios');
    } finally {
      setButtonLoading(button, false);
    }
  });

  // ──────────────────────────────────────────────
  //  URL pública del túnel
  // ──────────────────────────────────────────────

  let publicUrlAttempts = 0;
  async function loadPublicUrl() {
    try {
      const response = await fetch(`${API}/info`);
      const info = response.ok ? await response.json() : {};
      if (info.publicUrl) {
        state.publicUrl = info.publicUrl;
        el.publicUrlText.textContent = info.publicUrl;
        el.publicUrlContainer.classList.remove('hidden');
        return;
      }
    } catch (_) { /* sin conexión: se reintenta */ }
    // El túnel puede tardar en arrancar: reintentar durante un minuto como mucho
    if (++publicUrlAttempts < 12) setTimeout(loadPublicUrl, 5000);
  }

  el.publicUrlContainer.addEventListener('click', async () => {
    if (!state.publicUrl) return;
    if (await copyToClipboard(state.publicUrl)) {
      el.publicUrlContainer.classList.add('copied');
      el.publicUrlText.textContent = '¡Enlace copiado!';
      setTimeout(() => {
        el.publicUrlContainer.classList.remove('copied');
        el.publicUrlText.textContent = state.publicUrl;
      }, 1500);
    }
  });

  // ──────────────────────────────────────────────
  //  Arranque
  // ──────────────────────────────────────────────

  async function init() {
    // Versiones anteriores guardaban el token en localStorage: se elimina
    try {
      ['token', 'userId', 'userName', 'userEmail'].forEach((key) => localStorage.removeItem(key));
    } catch (_) { /* sin almacenamiento */ }

    loadPublicUrl();
    try {
      // La cookie HttpOnly no se puede leer desde JS: se pregunta al servidor
      const user = await api('/auth/me');
      enterApp(user);
    } catch (_) {
      el.authSection.classList.remove('hidden');
    }
  }

  init();
});
