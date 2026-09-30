/**
 * ============================================================
 *  Cloud Storage — Aplicación de Página Única (SPA)
 * ============================================================
 *  Lógica completa del lado del cliente:
 *    • Autenticación (registro / inicio de sesión)
 *    • Gestión de archivos (subida, descarga, eliminación)
 *    • Notificaciones toast
 *    • Arrastrar y soltar archivos
 *    • Seguimiento de progreso de subida
 * ============================================================
 */
document.addEventListener('DOMContentLoaded', () => {
  'use strict';

  // ──────────────────────────────────────────────
  //  Constantes y referencias al DOM
  // ──────────────────────────────────────────────

  const API_BASE = '/api';

  // Secciones principales
  const authSection      = document.getElementById('auth-section');
  const dashboardSection = document.getElementById('dashboard-section');

  // Pestañas de autenticación
  const authTabs = document.querySelectorAll('.auth-tab');

  // Formularios
  const loginForm    = document.getElementById('login-form');
  const registerForm = document.getElementById('register-form');

  // Inputs de inicio de sesión
  const loginEmail    = document.getElementById('login-email');
  const loginPassword = document.getElementById('login-password');

  // Inputs de registro
  const registerName     = document.getElementById('register-name');
  const registerEmail    = document.getElementById('register-email');
  const registerPassword = document.getElementById('register-password');

  // Dashboard
  const userGreeting  = document.getElementById('user-greeting');
  const logoutBtn     = document.getElementById('logout-btn');
  const uploadZone    = document.getElementById('upload-zone');
  const fileInput     = document.getElementById('file-input');
  const uploadProgress = document.getElementById('upload-progress');
  const progressFill  = document.querySelector('.progress-fill');
  const progressText  = document.querySelector('.progress-text');
  const filesGrid     = document.getElementById('files-grid');
  const emptyState    = document.getElementById('empty-state');
  const fileCount     = document.getElementById('file-count');
  const toastContainer = document.getElementById('toast-container');
  const previewModal   = document.getElementById('preview-modal');
  const previewClose   = document.getElementById('preview-close');
  const previewTitle   = document.getElementById('preview-title');
  const previewBody    = document.getElementById('preview-body');
  const publicUrlContainer = document.getElementById('public-url-container');
  const publicUrlText      = document.getElementById('public-url-text');

  // Elementos de Carpetas y Cuota
  const newFolderBtn         = document.getElementById('new-folder-btn');
  const breadcrumbsContainer  = document.getElementById('breadcrumbs-container');
  const storageUsedText      = document.getElementById('storage-used-text');
  const storageQuotaText      = document.getElementById('storage-quota-text');
  const storagePercentText    = document.getElementById('storage-percent-text');
  const storageQuotaFill      = document.getElementById('storage-quota-fill');

  // Modal de Crear Carpeta
  const folderModal          = document.getElementById('folder-modal');
  const folderModalClose     = document.getElementById('folder-modal-close');
  const createFolderForm     = document.getElementById('create-folder-form');
  const folderNameInput      = document.getElementById('folder-name-input');

  // Estado de navegación
  let currentFolderId = null;

  // ──────────────────────────────────────────────
  //  1. Gestión de Estado (localStorage)
  // ──────────────────────────────────────────────

  /**
   * Guarda los datos de sesión en localStorage.
   * @param {Object} data - Objeto con token, userId, name y email.
   */
  function saveSession(data) {
    localStorage.setItem('token', data.token);
    localStorage.setItem('userId', data.userId);
    localStorage.setItem('userName', data.name);
    localStorage.setItem('userEmail', data.email);
  }

  /** Elimina todos los datos de sesión de localStorage. */
  function clearSession() {
    localStorage.removeItem('token');
    localStorage.removeItem('userId');
    localStorage.removeItem('userName');
    localStorage.removeItem('userEmail');
  }

  /** @returns {string|null} El token JWT almacenado o null. */
  function getToken() {
    return localStorage.getItem('token');
  }

  /** @returns {boolean} true si hay un token guardado. */
  function isAuthenticated() {
    return !!getToken();
  }

  // ──────────────────────────────────────────────
  //  2. Sistema de Notificaciones Toast
  // ──────────────────────────────────────────────

  /**
   * Muestra una notificación toast temporal.
   * @param {string} message  - Texto del mensaje.
   * @param {'success'|'error'|'info'} type - Tipo de notificación.
   */
  function showToast(message, type = 'info') {
    // Íconos según el tipo
    const icons = {
      success: '✅',
      error:   '❌',
      info:    'ℹ️'
    };

    // Crear el elemento toast
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;

    // Ícono
    const iconSpan = document.createElement('span');
    iconSpan.className = 'toast-icon';
    iconSpan.textContent = icons[type] || icons.info;

    // Mensaje
    const msgSpan = document.createElement('span');
    msgSpan.className = 'toast-message';
    msgSpan.textContent = message;

    // Botón de cierre
    const closeBtn = document.createElement('button');
    closeBtn.className = 'toast-close';
    closeBtn.textContent = '×';
    closeBtn.addEventListener('click', () => removeToast(toast));

    toast.appendChild(iconSpan);
    toast.appendChild(msgSpan);
    toast.appendChild(closeBtn);

    toastContainer.appendChild(toast);

    // Auto-eliminar después de 4 segundos con animación de salida
    const autoRemoveTimer = setTimeout(() => removeToast(toast), 4000);

    // Si se cierra manualmente, cancelar el temporizador automático
    closeBtn.addEventListener('click', () => clearTimeout(autoRemoveTimer), { once: true });
  }

  /**
   * Elimina un toast con animación de desvanecimiento.
   * @param {HTMLElement} toast - El elemento toast a eliminar.
   */
  function removeToast(toast) {
    if (!toast || !toast.parentNode) return;
    toast.style.animation = 'fadeOut 0.3s ease forwards';
    toast.addEventListener('animationend', () => {
      if (toast.parentNode) toast.parentNode.removeChild(toast);
    });
  }

  // ──────────────────────────────────────────────
  //  3. Cambio de Pestañas de Autenticación
  // ──────────────────────────────────────────────

  authTabs.forEach(tab => {
    tab.addEventListener('click', () => {
      const targetTab = tab.dataset.tab; // 'login' o 'register'

      // Actualizar clase activa en las pestañas
      authTabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');

      // Alternar visibilidad de formularios
      if (targetTab === 'login') {
        loginForm.classList.remove('hidden');
        registerForm.classList.add('hidden');
      } else {
        loginForm.classList.add('hidden');
        registerForm.classList.remove('hidden');
      }
    });
  });

  // ──────────────────────────────────────────────
  //  4. Registro de Usuario
  // ──────────────────────────────────────────────

  registerForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const name     = registerName.value.trim();
    const email    = registerEmail.value.trim();
    const password = registerPassword.value;

    if (!name || !email || !password) {
      showToast('Por favor, completa todos los campos', 'error');
      return;
    }

    const submitBtn = registerForm.querySelector('.btn-primary');
    setButtonLoading(submitBtn, true);

    try {
      const data = await apiRequest(`${API_BASE}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, email, password })
      });

      // Guardar sesión y navegar al dashboard
      saveSession(data);
      showToast('¡Cuenta creada exitosamente!', 'success');
      showDashboard();
      loadFiles();

      // Limpiar formulario
      registerForm.reset();
    } catch (error) {
      showToast(error.message || 'Error al crear la cuenta', 'error');
    } finally {
      setButtonLoading(submitBtn, false);
    }
  });

  // ──────────────────────────────────────────────
  //  5. Inicio de Sesión
  // ──────────────────────────────────────────────

  loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const email    = loginEmail.value.trim();
    const password = loginPassword.value;

    if (!email || !password) {
      showToast('Por favor, completa todos los campos', 'error');
      return;
    }

    const submitBtn = loginForm.querySelector('.btn-primary');
    setButtonLoading(submitBtn, true);

    try {
      const data = await apiRequest(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
      });

      // Guardar sesión y navegar al dashboard
      saveSession(data);
      showToast('¡Bienvenido de vuelta!', 'success');
      showDashboard();
      loadFiles();

      // Limpiar formulario
      loginForm.reset();
    } catch (error) {
      showToast(error.message || 'Error al iniciar sesión', 'error');
    } finally {
      setButtonLoading(submitBtn, false);
    }
  });

  // ──────────────────────────────────────────────
  //  6. Cierre de Sesión
  // ──────────────────────────────────────────────

  logoutBtn.addEventListener('click', () => {
    clearSession();
    showAuthSection();
    showToast('Sesión cerrada', 'info');
  });

  // ──────────────────────────────────────────────
  //  7. Cargar Archivos y Carpetas (Contenidos)
  // ──────────────────────────────────────────────

  /**
   * Obtiene el listado de archivos y carpetas del directorio actual.
   */
  async function loadFiles() {
    try {
      const url = `${API_BASE}/folders/contents` + (currentFolderId ? `?folderId=${currentFolderId}` : '');
      const contents = await apiRequest(url);

      renderFolderContents(contents);
    } catch (error) {
      if (error.status !== 401) {
        showToast(error.message || 'Error al cargar el directorio', 'error');
      }
    }
  }

  /**
   * Renderiza el contenido del directorio actual en el grid.
   */
  function renderFolderContents(contents) {
    const { folders, files, breadcrumbs, storageUsed, storageQuota } = contents;

    // Limpiar el grid
    filesGrid.innerHTML = '';

    // Actualizar elementos totales
    const totalElements = folders.length + files.length;
    fileCount.textContent = `${totalElements} elemento${totalElements !== 1 ? 's' : ''}`;

    // Actualizar cuota de espacio
    updateQuotaIndicator(storageUsed, storageQuota);

    // Actualizar breadcrumbs
    renderBreadcrumbs(breadcrumbs);

    if (totalElements === 0) {
      emptyState.classList.remove('hidden');
      return;
    } else {
      emptyState.classList.add('hidden');
    }

    // 1. Renderizar Carpetas
    folders.forEach(folder => {
      const safeName = escapeHtml(folder.name);
      const folderHTML = `
        <div class="file-card folder-card" data-id="${folder.id}">
          <div class="file-card-header">
            <div class="file-icon">📁</div>
            <div class="file-info">
              <span class="file-name" title="${safeName}">${safeName}</span>
              <span class="file-meta">Carpeta</span>
            </div>
          </div>
          <div class="file-card-actions">
            <button class="btn-action btn-delete btn-delete-folder" title="Eliminar Carpeta" data-id="${folder.id}">🗑️ Eliminar</button>
          </div>
        </div>
      `;
      filesGrid.insertAdjacentHTML('beforeend', folderHTML);
    });

    // 2. Renderizar Archivos
    files.forEach(file => {
      const icon          = getFileIcon(file.contentType, file.originalName);
      const formattedSize = formatFileSize(file.fileSize);
      const formattedDate = formatDate(file.uploadedAt);
      const safeName      = escapeHtml(file.originalName);

      const cardHTML = `
        <div class="file-card file-card-item" data-id="${file.id}">
          <div class="file-card-header">
            <div class="file-icon">${icon}</div>
            <div class="file-info">
              <span class="file-name" title="${safeName}">${safeName}</span>
              <span class="file-meta">${formattedSize} · ${formattedDate}</span>
            </div>
          </div>
          <div class="file-card-actions">
            <button class="btn-action btn-download" title="Descargar" data-id="${file.id}" data-name="${safeName}">⬇️ Descargar</button>
            <button class="btn-action btn-delete btn-delete-file" title="Eliminar" data-id="${file.id}">🗑️ Eliminar</button>
          </div>
        </div>
      `;
      filesGrid.insertAdjacentHTML('beforeend', cardHTML);
    });
  }

  /**
   * Renderiza el camino de navegación en la interfaz.
   */
  function renderBreadcrumbs(breadcrumbs) {
    breadcrumbsContainer.innerHTML = '';

    // Añadir "Inicio" como raíz
    const rootSpan = document.createElement('span');
    rootSpan.className = `breadcrumb-item ${!currentFolderId ? 'active' : ''}`;
    rootSpan.textContent = 'Inicio';
    rootSpan.addEventListener('click', () => {
      if (currentFolderId) {
        currentFolderId = null;
        loadFiles();
      }
    });
    breadcrumbsContainer.appendChild(rootSpan);

    breadcrumbs.forEach((bc, idx) => {
      const separator = document.createElement('span');
      separator.className = 'breadcrumb-separator';
      separator.textContent = ' / ';
      breadcrumbsContainer.appendChild(separator);

      const itemSpan = document.createElement('span');
      const isActive = (idx === breadcrumbs.length - 1);
      itemSpan.className = `breadcrumb-item ${isActive ? 'active' : ''}`;
      itemSpan.textContent = bc.name;

      if (!isActive) {
        itemSpan.addEventListener('click', () => {
          currentFolderId = bc.id;
          loadFiles();
        });
      }
      breadcrumbsContainer.appendChild(itemSpan);
    });
  }

  /**
   * Actualiza el porcentaje y colores de la barra de almacenamiento.
   */
  function updateQuotaIndicator(used, quota) {
    const pct = quota > 0 ? (used / quota) * 100 : 0;

    storageUsedText.textContent = formatFileSize(used);
    storageQuotaText.textContent = formatFileSize(quota);
    storagePercentText.textContent = `${pct.toFixed(1)}% usado`;

    storageQuotaFill.style.width = `${Math.min(pct, 100)}%`;

    // Clases de color basadas en uso
    storageQuotaFill.classList.remove('warning', 'danger');
    if (pct >= 90) {
      storageQuotaFill.classList.add('danger');
    } else if (pct >= 70) {
      storageQuotaFill.classList.add('warning');
    }
  }

  // ──────────────────────────────────────────────
  //  8. Subida de Archivos (arrastrar & soltar + clic)
  // ──────────────────────────────────────────────

  // Clic en la zona de subida abre el selector de archivos
  uploadZone.addEventListener('click', () => {
    fileInput.click();
  });

  // Al seleccionar un archivo mediante el input
  fileInput.addEventListener('change', () => {
    if (fileInput.files && fileInput.files.length > 0) {
      // Copiar la lista antes de resetear el input (al resetearlo se vacía)
      const files = [...fileInput.files];
      // Resetear el input para permitir subir el mismo archivo otra vez
      fileInput.value = '';
      uploadFiles(files);
    }
  });

  // ── Eventos de arrastrar y soltar ──

  uploadZone.addEventListener('dragenter', (e) => {
    e.preventDefault();
    e.stopPropagation();
    uploadZone.classList.add('drag-over');
  });

  uploadZone.addEventListener('dragover', (e) => {
    e.preventDefault();
    e.stopPropagation();
    uploadZone.classList.add('drag-over');
  });

  uploadZone.addEventListener('dragleave', (e) => {
    e.preventDefault();
    e.stopPropagation();
    uploadZone.classList.remove('drag-over');
  });

  uploadZone.addEventListener('drop', (e) => {
    e.preventDefault();
    e.stopPropagation();
    uploadZone.classList.remove('drag-over');

    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      uploadFiles([...e.dataTransfer.files]);
    }
  });

  /**
   * Sube varios archivos uno detrás de otro (comparten la barra de progreso).
   * @param {File[]} files - Los archivos a subir.
   */
  async function uploadFiles(files) {
    for (const file of files) {
      const ok = await uploadFile(file);
      if (!ok && !getToken()) break; // sesión caducada: no seguir intentando
    }
  }

  /**
   * Sube un archivo al servidor usando XMLHttpRequest para
   * poder rastrear el progreso de la subida.
   * @param {File} file - El archivo a subir.
   * @returns {Promise<boolean>} true si la subida terminó bien.
   */
  function uploadFile(file) {
    return new Promise((resolve) => {
      const token = getToken();
      if (!token) {
        autoLogout();
        resolve(false);
        return;
      }

      // Mostrar barra de progreso
      uploadProgress.classList.remove('hidden');
      updateProgress(0);

      const formData = new FormData();
      formData.append('file', file);
      if (currentFolderId) {
        formData.append('folderId', currentFolderId);
      }

      const xhr = new XMLHttpRequest();

      // Seguimiento del progreso de subida
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
          const percent = Math.round((e.loaded / e.total) * 100);
          updateProgress(percent);
        }
      };

      // Subida completada
      xhr.onload = () => {
        uploadProgress.classList.add('hidden');

        if (xhr.status >= 200 && xhr.status < 300) {
          showToast(`${file.name} subido correctamente`, 'success');
          loadFiles();
          resolve(true);
          return;
        }
        if (xhr.status === 401) {
          autoLogout();
        } else {
          let errorMsg = 'Error al subir el archivo';
          try {
            const resp = JSON.parse(xhr.responseText);
            if (resp.error) errorMsg = resp.error;
          } catch (_) { /* ignorar error de parseo */ }
          showToast(`${file.name}: ${errorMsg}`, 'error');
        }
        resolve(false);
      };

      // Error de red
      xhr.onerror = () => {
        uploadProgress.classList.add('hidden');
        showToast(`Error de conexión al subir ${file.name}`, 'error');
        resolve(false);
      };

      xhr.open('POST', `${API_BASE}/files/upload`);
      xhr.setRequestHeader('Authorization', `Bearer ${token}`);
      xhr.send(formData);
    });
  }

  /**
   * Actualiza la barra de progreso y el texto de porcentaje.
   * @param {number} percent - Porcentaje completado (0-100).
   */
  function updateProgress(percent) {
    if (progressFill) progressFill.style.width = `${percent}%`;
    if (progressText) progressText.textContent = `${percent}%`;
  }

  // ──────────────────────────────────────────────
  //  9. Descarga de Archivos (delegación de eventos)
  // ──────────────────────────────────────────────

  filesGrid.addEventListener('click', async (e) => {
    const downloadBtn = e.target.closest('.btn-download');
    if (!downloadBtn) return;

    const fileId   = downloadBtn.dataset.id;
    const fileName = downloadBtn.dataset.name;

    try {
      const token = getToken();
      if (!token) {
        autoLogout();
        return;
      }

      const response = await fetch(`${API_BASE}/files/download/${fileId}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });

      if (response.status === 401) {
        autoLogout();
        return;
      }

      if (!response.ok) {
        throw new Error('Error al descargar el archivo');
      }

      // Convertir la respuesta a blob y descargar
      const blob = await response.blob();
      const url  = URL.createObjectURL(blob);

      const a    = document.createElement('a');
      a.href     = url;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);

      // Liberar la URL del objeto
      URL.revokeObjectURL(url);

      showToast('Descarga iniciada', 'success');
    } catch (error) {
      showToast(error.message || 'Error al descargar el archivo', 'error');
    }
  });

  // ──────────────────────────────────────────────
  //  10. Eliminación de Archivos y Carpetas (delegación de eventos)
  // ──────────────────────────────────────────────

  filesGrid.addEventListener('click', async (e) => {
    // 1. Borrar Archivo
    const deleteFileBtn = e.target.closest('.btn-delete-file');
    if (deleteFileBtn) {
      const fileId = deleteFileBtn.dataset.id;

      const confirmed = confirm('¿Estás seguro de que deseas eliminar este archivo?');
      if (!confirmed) return;

      try {
        await apiRequest(`${API_BASE}/files/${fileId}`, {
          method: 'DELETE'
        });

        showToast('Archivo eliminado', 'success');
        loadFiles();
      } catch (error) {
        if (error.status !== 401) {
          showToast(error.message || 'Error al eliminar el archivo', 'error');
        }
      }
      return;
    }

    // 2. Borrar Carpeta
    const deleteFolderBtn = e.target.closest('.btn-delete-folder');
    if (deleteFolderBtn) {
      const folderId = deleteFolderBtn.dataset.id;

      const confirmed = confirm('¿Estás seguro de que deseas eliminar esta carpeta y todo su contenido de forma permanente?');
      if (!confirmed) return;

      try {
        await apiRequest(`${API_BASE}/folders/${folderId}`, {
          method: 'DELETE'
        });

        showToast('Carpeta eliminada', 'success');
        loadFiles();
      } catch (error) {
        if (error.status !== 401) {
          showToast(error.message || 'Error al eliminar la carpeta', 'error');
        }
      }
    }
  });

  // ──────────────────────────────────────────────
  //  11. Funciones Auxiliares
  // ──────────────────────────────────────────────

  /**
   * Formatea un tamaño en bytes a una cadena legible.
   * @param {number} bytes - Tamaño en bytes.
   * @returns {string} Tamaño formateado (ej: "1.5 MB").
   */
  function formatFileSize(bytes) {
    if (bytes === 0) return '0 B';

    const units = ['B', 'KB', 'MB', 'GB'];
    const k     = 1024;
    // Determinar el índice de la unidad apropiada
    const i     = Math.floor(Math.log(bytes) / Math.log(k));
    const index = Math.min(i, units.length - 1);
    const size  = bytes / Math.pow(k, index);

    // Usar 0 decimales para bytes, 1-2 para el resto
    if (index === 0) return `${size} ${units[index]}`;
    return `${size.toFixed(size >= 100 ? 1 : 2)} ${units[index]}`;
  }

  /**
   * Formatea una fecha ISO a un formato legible en español.
   * @param {string} dateString - Fecha en formato ISO 8601.
   * @returns {string} Fecha formateada (ej: "2 jul 2026, 14:30").
   */
  function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString('es-ES', {
      day:    'numeric',
      month:  'short',
      year:   'numeric',
      hour:   '2-digit',
      minute: '2-digit'
    });
  }

  /**
   * Devuelve un emoji representativo según el tipo de archivo.
   * @param {string} contentType - MIME type del archivo.
   * @param {string} fileName    - Nombre original del archivo.
   * @returns {string} Emoji correspondiente.
   */
  function getFileIcon(contentType, fileName) {
    // Normalizar para facilitar la comparación
    const mime = (contentType || '').toLowerCase();
    const ext  = (fileName || '').split('.').pop().toLowerCase();

    // PDF
    if (mime === 'application/pdf' || ext === 'pdf') return '📄';

    // Imágenes
    if (mime.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'svg', 'webp', 'bmp'].includes(ext)) return '🖼️';

    // Vídeos
    if (mime.startsWith('video/') || ['mp4', 'avi', 'mov', 'mkv', 'webm'].includes(ext)) return '🎬';

    // Audio
    if (mime.startsWith('audio/') || ['mp3', 'wav', 'ogg', 'flac', 'aac'].includes(ext)) return '🎵';

    // Archivos comprimidos
    if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext) ||
        mime === 'application/zip' ||
        mime === 'application/x-rar-compressed' ||
        mime === 'application/x-7z-compressed') return '📦';

    // Hojas de cálculo
    if (['xls', 'xlsx', 'csv'].includes(ext) ||
        mime === 'application/vnd.ms-excel' ||
        mime === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet') return '📊';

    // Documentos de texto enriquecido
    if (['doc', 'docx'].includes(ext) ||
        mime === 'application/msword' ||
        mime === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document') return '📝';

    // Presentaciones
    if (['ppt', 'pptx'].includes(ext) ||
        mime === 'application/vnd.ms-powerpoint' ||
        mime === 'application/vnd.openxmlformats-officedocument.presentationml.presentation') return '📽️';

    // Texto plano
    if (ext === 'txt' || mime === 'text/plain') return '📃';

    // Por defecto
    return '📎';
  }

  /**
   * Wrapper para fetch que añade automáticamente el header
   * de autorización y gestiona respuestas 401 (auto-logout).
   * @param {string} url     - URL del endpoint.
   * @param {Object} options - Opciones de fetch (method, headers, body, etc.).
   * @returns {Promise<any>} Los datos de la respuesta parseados como JSON.
   * @throws {Object} Objeto con message y status en caso de error.
   */
  async function apiRequest(url, options = {}) {
    const token = getToken();

    // Preparar headers con autorización solo si hay token
    // (antes se enviaba "Bearer null" en login, registro y /api/info)
    const headers = {
        ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
        'Content-Type': 'application/json',
        ...(options.headers || {})
    };

    const response = await fetch(url, {
      ...options,
      headers
    });

    // Manejar 401: sesión expirada. En login/registro un 401 significa credenciales
    // incorrectas, así que se trata como un error normal más abajo.
    const isAuthEndpoint = url.includes('/auth/');
    if (response.status === 401 && !isAuthEndpoint) {
      autoLogout();
      const error = new Error('Sesión expirada. Por favor, inicia sesión de nuevo.');
      error.status = 401;
      throw error;
    }

    // Para respuestas sin contenido (ej: 204 No Content)
    if (response.status === 204) {
      return null;
    }

    // Intentar parsear la respuesta como JSON
    if (!response.ok) {
      let errorMessage = 'Ha ocurrido un error inesperado';
      try {
        const errorData = await response.json();
        // El backend envía el mensaje en "error" (ver GlobalExceptionHandler)
        if (errorData.error) errorMessage = errorData.error;
        else if (errorData.message) errorMessage = errorData.message;
      } catch (_) { /* la respuesta no era JSON */ }

      const error = new Error(errorMessage);
      error.status = response.status;
      throw error;
    }

    return response.json();
  }

  /**
   * Escapa caracteres HTML especiales para prevenir XSS.
   * @param {string} str - Cadena a escapar.
   * @returns {string} Cadena con caracteres HTML escapados.
   */
  function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  // ──────────────────────────────────────────────
  //  12. Funciones de Navegación y UI
  // ──────────────────────────────────────────────

  /** Muestra el dashboard y oculta la sección de autenticación. */
  function showDashboard() {
    authSection.classList.add('hidden');
    dashboardSection.classList.remove('hidden');

    // Actualizar el saludo del usuario
    const name = localStorage.getItem('userName') || 'Usuario';
    userGreeting.textContent = `Hola, ${name}`;
  }

  /** Muestra la sección de autenticación y oculta el dashboard. */
  function showAuthSection() {
    dashboardSection.classList.add('hidden');
    authSection.classList.remove('hidden');
  }

  /**
   * Cierra la sesión automáticamente (401 o token inválido).
   * Limpia el almacenamiento y navega a la pantalla de auth.
   */
  function autoLogout() {
    clearSession();
    showAuthSection();
  }

  /**
   * Activa o desactiva el estado de carga de un botón.
   * @param {HTMLElement} button  - El botón a modificar.
   * @param {boolean}     loading - true para activar carga, false para restaurar.
   */
  function setButtonLoading(button, loading) {
    if (!button) return;

    if (loading) {
      // Guardar el texto original
      button.dataset.originalText = button.textContent;
      button.disabled = true;

      // Crear spinner
      const spinner = document.createElement('span');
      spinner.className = 'spinner';
      spinner.textContent = '';

      button.textContent = '';
      button.appendChild(spinner);
      button.insertAdjacentText('beforeend', ' Cargando...');
    } else {
      button.disabled = false;
      button.textContent = button.dataset.originalText || 'Enviar';
    }
  }

  // ──────────────────────────────────────────────
  //  13. Previsualización de Archivos
  // ──────────────────────────────────────────────

  let currentPreviewUrl = null;

  function closePreview() {
    previewModal.classList.add('hidden');
    previewBody.innerHTML = '';
    if (currentPreviewUrl) {
      URL.revokeObjectURL(currentPreviewUrl);
      currentPreviewUrl = null;
    }
  }

  previewClose.addEventListener('click', closePreview);
  previewModal.addEventListener('click', (e) => {
    if (e.target === previewModal) closePreview();
  });

  filesGrid.addEventListener('click', async (e) => {
    // 1. Navegación al hacer clic en el encabezado de una carpeta
    const folderHeader = e.target.closest('.folder-card .file-card-header');
    if (folderHeader) {
      const card = folderHeader.closest('.folder-card');
      currentFolderId = card.dataset.id;
      loadFiles();
      return;
    }

    // 2. Previsualización de un archivo
    const fileHeader = e.target.closest('.file-card-item .file-card-header');
    if (!fileHeader) return;

    const card = fileHeader.closest('.file-card');
    const fileId = card.dataset.id;
    const fileName = card.querySelector('.file-name').textContent;
    
    await previewFile(fileId, fileName);
  });

  async function previewFile(fileId, fileName) {
    try {
      const token = getToken();
      if (!token) return autoLogout();

      previewTitle.textContent = fileName;
      previewBody.innerHTML = '<div class="spinner"></div><p style="color: white; margin-left: 1rem;">Cargando...</p>';
      previewModal.classList.remove('hidden');

      const response = await fetch(`${API_BASE}/files/download/${fileId}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });

      if (response.status === 401) return autoLogout();
      if (!response.ok) throw new Error('Error al cargar la previsualización');

      const blob = await response.blob();
      currentPreviewUrl = URL.createObjectURL(blob);
      const mime = blob.type.toLowerCase();
      const ext = fileName.split('.').pop().toLowerCase();

      let contentHtml = '';

      if (mime.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'svg', 'webp', 'bmp'].includes(ext)) {
        contentHtml = `<img src="${currentPreviewUrl}" alt="${escapeHtml(fileName)}">`;
      } else if (mime.startsWith('video/') || ['mp4', 'webm', 'ogg', 'mov'].includes(ext)) {
        contentHtml = `<video controls autoplay src="${currentPreviewUrl}"></video>`;
      } else if (mime.startsWith('audio/') || ['mp3', 'wav', 'ogg', 'aac'].includes(ext)) {
        contentHtml = `<audio controls autoplay src="${currentPreviewUrl}"></audio>`;
      } else if (mime === 'application/pdf' || ext === 'pdf') {
        contentHtml = `<iframe src="${currentPreviewUrl}"></iframe>`;
      } else if (mime.startsWith('text/') || ['txt', 'csv', 'json', 'xml', 'md', 'js', 'html', 'css'].includes(ext)) {
        const text = await blob.text();
        contentHtml = `<pre>${escapeHtml(text)}</pre>`;
      } else {
        contentHtml = `<div style="text-align: center; color: white;">
          <p>La previsualización no está disponible para este tipo de archivo.</p>
          <p style="font-size: 3rem; margin: 1rem 0;">${getFileIcon(mime, fileName)}</p>
        </div>`;
      }

      previewBody.innerHTML = contentHtml;

    } catch (error) {
      previewBody.innerHTML = `<p style="color: #ff6b6b;">${error.message}</p>`;
      showToast(error.message, 'error');
    }
  }

  // ──────────────────────────────────────────────
  //  14. Indicador de URL Pública
  // ──────────────────────────────────────────────

  /** Obtiene la URL pública del backend y la muestra en la UI */
  async function loadPublicUrl() {
    try {
      const response = await fetch('/api/info');
      if (response.ok) {
        const info = await response.json();
        if (info.publicUrl) {
          publicUrlContainer.classList.remove('hidden');
          publicUrlText.textContent = info.publicUrl;
          publicUrlText.dataset.url = info.publicUrl;
        } else {
          // Reintentar en 5 segundos si el túnel aún está levantándose
          setTimeout(loadPublicUrl, 5000);
        }
      }
    } catch (e) {
      // Reintentar en caso de fallo de red
      setTimeout(loadPublicUrl, 5000);
    }
  }

  // Copiar al portapapeles al hacer clic
  publicUrlContainer.addEventListener('click', async () => {
    const url = publicUrlText.dataset.url;
    if (!url) return;

    try {
      await navigator.clipboard.writeText(url);
      
      // Feedback visual de copiado
      publicUrlContainer.classList.add('copied');
      publicUrlText.textContent = '¡Enlace copiado!';
      
      setTimeout(() => {
        publicUrlContainer.classList.remove('copied');
        publicUrlText.textContent = url;
      }, 1500);
      
      showToast('Enlace de acceso público copiado al portapapeles', 'success');
    } catch (err) {
      showToast('No se pudo copiar automáticamente', 'error');
    }
  });

  // ── Controles del Modal de Crear Carpeta ──
  newFolderBtn.addEventListener('click', () => {
    folderModal.classList.remove('hidden');
    folderNameInput.focus();
  });

  folderModalClose.addEventListener('click', () => {
    folderModal.classList.add('hidden');
    createFolderForm.reset();
  });

  folderModal.addEventListener('click', (e) => {
    if (e.target === folderModal) {
      folderModal.classList.add('hidden');
      createFolderForm.reset();
    }
  });

  createFolderForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = folderNameInput.value.trim();
    if (!name) return;

    const submitBtn = createFolderForm.querySelector('.btn-primary');
    setButtonLoading(submitBtn, true);

    try {
      await apiRequest(`${API_BASE}/folders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, parentId: currentFolderId })
      });

      showToast('Carpeta creada exitosamente', 'success');
      folderModal.classList.add('hidden');
      createFolderForm.reset();
      loadFiles();
    } catch (error) {
      showToast(error.message || 'Error al crear la carpeta', 'error');
    } finally {
      setButtonLoading(submitBtn, false);
    }
  });

  // ──────────────────────────────────────────────
  //  Sección de Configuración B2B Admin
  // ──────────────────────────────────────────────

  const b2bSettingsBtn     = document.getElementById('b2b-settings-btn');
  const b2bLoginModal      = document.getElementById('b2b-login-modal');
  const b2bLoginClose      = document.getElementById('b2b-login-close');
  const b2bLoginForm       = document.getElementById('b2b-login-form');
  const b2bAdminPassword   = document.getElementById('b2b-admin-password');
  
  const b2bConfigModal     = document.getElementById('b2b-config-modal');
  const b2bConfigClose     = document.getElementById('b2b-config-close');
  const b2bConfigForm      = document.getElementById('b2b-config-form');
  
  const b2bHostStorage     = document.getElementById('b2b-host-storage');
  const b2bHostDb          = document.getElementById('b2b-host-db');
  const b2bLicenseKey      = document.getElementById('b2b-license-key');
  const b2bNewPassword     = document.getElementById('b2b-new-password');
  const b2bConfirmPassword = document.getElementById('b2b-confirm-password');

  let verifiedB2bPassword = '';

  // Abrir login B2B
  b2bSettingsBtn.addEventListener('click', () => {
    b2bLoginModal.classList.remove('hidden');
    b2bAdminPassword.value = '';
    b2bAdminPassword.focus();
  });

  // Cerrar login B2B
  b2bLoginClose.addEventListener('click', () => {
    b2bLoginModal.classList.add('hidden');
  });

  b2bLoginModal.addEventListener('click', (e) => {
    if (e.target === b2bLoginModal) {
      b2bLoginModal.classList.add('hidden');
    }
  });

  // Enviar verificación de contraseña B2B
  b2bLoginForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const password = b2bAdminPassword.value;
    if (!password) return;

    const submitBtn = b2bLoginForm.querySelector('.btn-primary');
    setButtonLoading(submitBtn, true);

    try {
      const response = await apiRequest(`${API_BASE}/admin/config/verify`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password })
      });

      if (response.success) {
        verifiedB2bPassword = password;
        b2bLoginModal.classList.add('hidden');
        b2bConfigModal.classList.remove('hidden');
        await loadB2bConfig();
      }
    } catch (error) {
      showToast(error.message || 'Contraseña de administrador B2B incorrecta', 'error');
    } finally {
      setButtonLoading(submitBtn, false);
    }
  });

  // Cerrar panel de ajustes B2B
  b2bConfigClose.addEventListener('click', () => {
    b2bConfigModal.classList.add('hidden');
    b2bConfigForm.reset();
  });

  b2bConfigModal.addEventListener('click', (e) => {
    if (e.target === b2bConfigModal) {
      b2bConfigModal.classList.add('hidden');
      b2bConfigForm.reset();
    }
  });

  // Cargar configuración de .env en los campos
  async function loadB2bConfig() {
    try {
      const config = await apiRequest(`${API_BASE}/admin/config`, {
        method: 'GET',
        headers: {
          'X-B2B-Admin-Password': verifiedB2bPassword
        }
      });

      b2bHostStorage.value = config.HOST_STORAGE_PATH || '';
      b2bHostDb.value      = config.HOST_DB_PATH || '';
      b2bLicenseKey.value  = config.APP_LICENSE_KEY || '';
    } catch (error) {
      showToast('Error al cargar la configuración: ' + error.message, 'error');
      b2bConfigModal.classList.add('hidden');
    }
  }

  // Guardar configuración del panel B2B
  b2bConfigForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const licenseKey  = b2bLicenseKey.value.trim();

    const newPass     = b2bNewPassword.value;
    const confirmPass = b2bConfirmPassword.value;

    if (newPass) {
      if (newPass !== confirmPass) {
        showToast('La nueva contraseña y la confirmación no coinciden', 'error');
        return;
      }
    }

    const submitBtn = b2bConfigForm.querySelector('.btn-primary');
    setButtonLoading(submitBtn, true);

    try {
      const response = await apiRequest(`${API_BASE}/admin/config`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-B2B-Admin-Password': verifiedB2bPassword
        },
        body: JSON.stringify({
          licenseKey: licenseKey,
          newAdminPassword: newPass || null
        })
      });

      if (response.success) {
        showToast('¡Configuración B2B guardada exitosamente!', 'success');
        if (newPass) {
          verifiedB2bPassword = newPass;
        }
        b2bConfigModal.classList.add('hidden');
        b2bConfigForm.reset();

        // Recargar los archivos por si cambió la cuota autorizada en vivo
        loadFiles();
      }
    } catch (error) {
      showToast(error.message || 'Error al guardar los cambios', 'error');
    } finally {
      setButtonLoading(submitBtn, false);
    }
  });

  // ──────────────────────────────────────────────
  //  Inicialización al cargar la página
  // ──────────────────────────────────────────────

  /**
   * Punto de entrada principal: verifica si el usuario tiene
   * una sesión activa y muestra la vista correspondiente.
   */
  function init() {
    loadPublicUrl();
    if (isAuthenticated()) {
      showDashboard();
      loadFiles();
    } else {
      showAuthSection();
    }
  }

  init();
});
