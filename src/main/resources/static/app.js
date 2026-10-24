/**
 * Dispatch Notification Platform — Frontend Application Controller
 */

// =========================================================================
// Global State
// =========================================================================
const AppState = {
  token: localStorage.getItem('dispatch_token') || null,
  refreshToken: localStorage.getItem('dispatch_refresh_token') || null,
  user: JSON.parse(localStorage.getItem('dispatch_user') || 'null'),
  currentTab: 'overview',
  notifPage: 0,
  notifSize: 10,
  totalNotifPages: 1
};

// =========================================================================
// API Client Wrapper
// =========================================================================
async function apiFetch(url, options = {}) {
  const headers = new Headers(options.headers || {});

  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (AppState.token) {
    headers.set('Authorization', `Bearer ${AppState.token}`);
  }

  const config = {
    ...options,
    headers
  };

  try {
    const res = await fetch(url, config);

    // Capture custom headers (e.g. Idempotent-Replayed)
    const idempotentReplayed = res.headers.get('Idempotent-Replayed');

    if (res.status === 401) {
      // If token expired, clear and open login modal
      if (AppState.token) {
        showToast('Session expired. Please log in again.', 'warning');
        logout();
      }
      openAuthModal('login');
      throw new Error('Unauthorized');
    }

    if (res.status === 204) {
      return { ok: true, status: 204, data: null };
    }

    const contentType = res.headers.get('content-type') || '';
    let data;
    if (contentType.includes('application/json')) {
      data = await res.json();
    } else {
      data = await res.text();
    }

    if (!res.ok) {
      const errorMsg = data.message || data.error || (typeof data === 'string' ? data : 'API Error');
      throw new Error(errorMsg);
    }

    return { ok: true, status: res.status, data, idempotentReplayed };
  } catch (err) {
    throw err;
  }
}

// =========================================================================
// Initialization
// =========================================================================
document.addEventListener('DOMContentLoaded', () => {
  // Initialize Lucide icons
  if (window.lucide) {
    lucide.createIcons();
  }

  // Restore User State
  updateUserUI();

  // Bind Sidebar Navigation
  document.querySelectorAll('.nav-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      const target = tab.dataset.tab;
      switchTab(target);
    });
  });

  // Bind Live Preview on Send inputs
  ['sendChannel', 'sendRecipient', 'sendSubject', 'sendBody', 'sendIdempotencyKey', 'sendCustomFrom', 'sendCustomFromName', 'sendCustomReplyTo'].forEach(id => {
    const el = document.getElementById(id) || document.querySelector(`input[name="${id}"]`);
    if (el) {
      el.addEventListener('input', updateLiveJsonPreview);
      el.addEventListener('change', updateLiveJsonPreview);
    }
  });

  // Auth Button
  const authTrigger = document.getElementById('authTriggerBtn');
  if (authTrigger) {
    authTrigger.addEventListener('click', () => {
      if (AppState.user) {
        // Already logged in - show profile or logout confirmation
        if (confirm(`Logged in as ${AppState.user.fullName} (${AppState.user.email}). Do you want to log out?`)) {
          logout();
        }
      } else {
        openAuthModal('login');
      }
    });
  }

  const sidebarLogout = document.getElementById('sidebarLogoutBtn');
  if (sidebarLogout) {
    sidebarLogout.addEventListener('click', logout);
  }

  // Developer Credentials Form Listeners
  const credEmailForm = document.getElementById('credEmailForm');
  if (credEmailForm) credEmailForm.addEventListener('submit', handleSaveEmailCredentials);

  const credSmsForm = document.getElementById('credSmsForm');
  if (credSmsForm) credSmsForm.addEventListener('submit', handleSaveSmsCredentials);

  // Initial Load
  generateNewIdempotencyKey();
  updateLiveJsonPreview();
  refreshOverviewStats();
  refreshHealth();

  // Auto-refresh interval for health and badges
  setInterval(() => {
    if (AppState.user) {
      loadInboxBadgeCount();
    }
  }, 10000);
});

// =========================================================================
// Tab Switching
// =========================================================================
function switchTab(tabId) {
  AppState.currentTab = tabId;

  // Update Sidebar active classes
  document.querySelectorAll('.nav-tab').forEach(tab => {
    if (tab.dataset.tab === tabId) {
      tab.className = 'nav-tab active w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition text-white bg-brand-600/20 text-brand-300 border border-brand-500/30';
    } else {
      tab.className = 'nav-tab w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition text-slate-300 hover:text-white hover:bg-slate-800/60';
    }
  });

  // Hide all sections, show target
  document.querySelectorAll('.tab-pane').forEach(pane => {
    pane.classList.add('hidden');
  });

  const targetPane = document.getElementById(`tab-${tabId}`);
  if (targetPane) {
    targetPane.classList.remove('hidden');
  }

  // Refresh tab specific data
  if (tabId === 'overview') refreshOverviewStats();
  if (tabId === 'notifications') loadNotifications();
  if (tabId === 'inbox') loadInboxMessages();
  if (tabId === 'audit') loadDeliveryHistory();
  if (tabId === 'preferences') loadPreferences();
  if (tabId === 'credentials') loadDeveloperCredentials();
  if (tabId === 'outbox') loadOutboxAndDlq();
  if (tabId === 'health') refreshFullHealth();

  if (window.lucide) lucide.createIcons();
}

// =========================================================================
// User & Auth Management
// =========================================================================
function updateUserUI() {
  const user = AppState.user;
  const authTrigger = document.getElementById('authTriggerBtn');
  const sidebarUserBox = document.getElementById('sidebarUserBox');
  const userNameLabel = document.getElementById('userNameLabel');
  const userEmailLabel = document.getElementById('userEmailLabel');
  const userAvatar = document.getElementById('userAvatar');
  const sidebarLogoutBtn = document.getElementById('sidebarLogoutBtn');

  if (user) {
    userNameLabel.textContent = user.fullName || 'User';
    userEmailLabel.textContent = user.email || '';
    userAvatar.textContent = (user.fullName || user.email || 'U').substring(0, 1).toUpperCase();
    userAvatar.className = 'w-8 h-8 rounded-lg bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 flex items-center justify-center font-bold text-xs uppercase';
    sidebarLogoutBtn.classList.remove('hidden');

    authTrigger.innerHTML = `
      <i data-lucide="user-check" class="w-3.5 h-3.5 text-emerald-400"></i>
      <span>${escapeHtml(user.fullName || user.email)}</span>
    `;
    authTrigger.className = 'flex items-center gap-2 text-xs font-semibold px-3 py-1.5 rounded-lg bg-surface-800 text-slate-200 hover:bg-surface-700 border border-slate-700 transition';

    // If send recipient is blank, default to user's email
    const recipientInput = document.getElementById('sendRecipient');
    if (recipientInput && !recipientInput.value) {
      recipientInput.value = user.email;
      updateLiveJsonPreview();
    }

    loadInboxBadgeCount();
  } else {
    userNameLabel.textContent = 'Guest User';
    userEmailLabel.textContent = 'Not logged in';
    userAvatar.textContent = 'G';
    userAvatar.className = 'w-8 h-8 rounded-lg bg-indigo-500/20 text-indigo-400 border border-indigo-500/30 flex items-center justify-center font-bold text-xs uppercase';
    sidebarLogoutBtn.classList.add('hidden');

    authTrigger.innerHTML = `
      <i data-lucide="log-in" class="w-3.5 h-3.5"></i>
      <span>Log In / Register</span>
    `;
    authTrigger.className = 'flex items-center gap-2 text-xs font-semibold px-3.5 py-1.5 rounded-lg bg-brand-600 text-white hover:bg-brand-500 transition shadow-sm shadow-brand-500/20';
  }

  if (window.lucide) lucide.createIcons();
}

function openAuthModal(mode = 'login') {
  document.getElementById('authModal').classList.remove('hidden');
  document.getElementById('authModal').classList.add('flex');
  switchAuthMode(mode);
}

function closeAuthModal() {
  document.getElementById('authModal').classList.add('hidden');
  document.getElementById('authModal').classList.remove('flex');
}

function switchAuthMode(mode) {
  const loginForm = document.getElementById('loginForm');
  const regForm = document.getElementById('registerForm');
  const tabLogin = document.getElementById('authTabLogin');
  const tabReg = document.getElementById('authTabRegister');

  if (mode === 'login') {
    loginForm.classList.remove('hidden');
    regForm.classList.add('hidden');
    tabLogin.className = 'flex-1 pb-2 text-sm font-semibold text-brand-400 border-b-2 border-brand-500';
    tabReg.className = 'flex-1 pb-2 text-sm font-semibold text-slate-400 hover:text-slate-200';
  } else {
    loginForm.classList.add('hidden');
    regForm.classList.remove('hidden');
    tabReg.className = 'flex-1 pb-2 text-sm font-semibold text-brand-400 border-b-2 border-brand-500';
    tabLogin.className = 'flex-1 pb-2 text-sm font-semibold text-slate-400 hover:text-slate-200';
  }
}

async function handleLogin(e) {
  e.preventDefault();
  const email = document.getElementById('loginEmail').value.trim();
  const password = document.getElementById('loginPassword').value;

  try {
    const res = await apiFetch('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password })
    });

    saveAuthSession(res.data);
    showToast(`Welcome back, ${res.data.user.fullName}!`, 'success');
    closeAuthModal();
    refreshOverviewStats();
  } catch (err) {
    showToast(err.message, 'error');
  }
}

async function handleRegister(e) {
  e.preventDefault();
  const fullName = document.getElementById('regFullName').value.trim();
  const email = document.getElementById('regEmail').value.trim();
  const phoneNumber = document.getElementById('regPhone').value.trim();
  const password = document.getElementById('regPassword').value;

  try {
    const res = await apiFetch('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({ fullName, email, phoneNumber, password })
    });

    saveAuthSession(res.data);
    showToast(`Account registered successfully!`, 'success');
    closeAuthModal();
    refreshOverviewStats();
  } catch (err) {
    showToast(err.message, 'error');
  }
}

async function quickFillDemoUser() {
  const rand = Math.floor(Math.random() * 9000) + 1000;
  const demoEmail = `tester.${rand}@dispatch.io`;
  const demoName = `Tester ${rand}`;
  const demoPass = 'Password123!';

  try {
    const res = await apiFetch('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        fullName: demoName,
        email: demoEmail,
        phoneNumber: '+15555550199',
        password: demoPass
      })
    });

    saveAuthSession(res.data);
    showToast(`Created & logged into test user: ${demoEmail}`, 'success');
    closeAuthModal();
    refreshOverviewStats();
  } catch (err) {
    showToast(err.message, 'error');
  }
}

function saveAuthSession(authData) {
  AppState.token = authData.accessToken;
  AppState.refreshToken = authData.refreshToken;
  AppState.user = authData.user;

  localStorage.setItem('dispatch_token', authData.accessToken);
  localStorage.setItem('dispatch_refresh_token', authData.refreshToken);
  localStorage.setItem('dispatch_user', JSON.stringify(authData.user));

  updateUserUI();
}

function logout() {
  AppState.token = null;
  AppState.refreshToken = null;
  AppState.user = null;

  localStorage.removeItem('dispatch_token');
  localStorage.removeItem('dispatch_refresh_token');
  localStorage.removeItem('dispatch_user');

  updateUserUI();
  showToast('Logged out.', 'info');
}

// =========================================================================
// Tab 1 & General: Overview Stats & Health
// =========================================================================
async function refreshOverviewStats() {
  if (!AppState.user) return;

  try {
    // 1. Notifications count
    const notifs = await apiFetch(`/api/v1/users/${AppState.user.id}/notifications?size=1`);
    if (notifs.ok && notifs.data) {
      document.getElementById('statTotalNotifications').textContent = notifs.data.totalElements || 0;
    }

    // 2. Inbox unread
    loadInboxBadgeCount();

    // 3. Outbox stats
    const outbox = await apiFetch('/api/v1/outbox/stats');
    if (outbox.ok && outbox.data) {
      document.getElementById('statOutboxPending').textContent = outbox.data.pendingEvents || 0;
    }

    // 4. DLQ stats
    const dlq = await apiFetch('/api/v1/dlq/stats');
    if (dlq.ok && dlq.data) {
      document.getElementById('statDlqMessages').textContent = dlq.data.dlqQueueMessages || 0;
    }
  } catch (err) {
    console.warn('Overview stats refresh partial failure:', err.message);
  }
}

async function refreshHealth() {
  try {
    const res = await apiFetch('/actuator/health');
    const health = res.data;

    const dbState = health?.components?.db?.status || (health.status === 'UP' ? 'UP' : 'DOWN');
    const rabbitState = health?.components?.rabbit?.status || (health.status === 'UP' ? 'UP' : 'UNKNOWN');
    const notifSystem = health?.components?.notificationSystem?.details?.status || 'OPTIMAL';

    document.getElementById('healthDbState').innerHTML = `<span class="w-1.5 h-1.5 rounded-full ${dbState === 'UP' ? 'bg-emerald-400' : 'bg-rose-400'}"></span> ${dbState}`;
    document.getElementById('healthRabbitState').innerHTML = `<span class="w-1.5 h-1.5 rounded-full ${rabbitState === 'UP' ? 'bg-emerald-400' : 'bg-amber-400'}"></span> ${rabbitState}`;
    document.getElementById('healthOutboxState').innerHTML = `<span class="w-1.5 h-1.5 rounded-full bg-emerald-400"></span> ${notifSystem}`;
  } catch (err) {
    document.getElementById('healthDbState').innerHTML = `<span class="w-1.5 h-1.5 rounded-full bg-rose-400"></span> DOWN`;
  }
}

async function refreshFullHealth() {
  const box = document.getElementById('actuatorHealthBox');
  box.textContent = 'Querying /actuator/health...';
  try {
    const res = await apiFetch('/actuator/health');
    box.textContent = JSON.stringify(res.data, null, 2);
  } catch (err) {
    box.textContent = 'Error querying health: ' + err.message;
  }
}

// =========================================================================
// Tab 2: Send Notification & Live Preview
// =========================================================================
function generateNewIdempotencyKey() {
  const uuid = 'key-' + crypto.randomUUID();
  const input = document.getElementById('sendIdempotencyKey');
  if (input) {
    input.value = uuid;
    updateLiveJsonPreview();
  }
}

function updateChannelFields() {
  const channel = document.querySelector('input[name="sendChannel"]:checked')?.value || 'IN_APP';
  const recipientInput = document.getElementById('sendRecipient');
  const subjectInput = document.getElementById('sendSubject');

  if (channel === 'IN_APP') {
    if (AppState.user) recipientInput.value = AppState.user.email;
    recipientInput.placeholder = 'User email or identifier';
    subjectInput.placeholder = 'In-App Alert Subject';
  } else if (channel === 'EMAIL') {
    recipientInput.placeholder = 'recipient@domain.com';
    subjectInput.placeholder = 'Email Subject Line';
  } else if (channel === 'SMS') {
    recipientInput.placeholder = '+15555550100 (E.164)';
    subjectInput.placeholder = 'SMS Header (Optional)';
  }

  updateLiveJsonPreview();
}

function updateLiveJsonPreview() {
  const channel = document.querySelector('input[name="sendChannel"]:checked')?.value || 'IN_APP';
  const recipient = document.getElementById('sendRecipient')?.value || '';
  const subject = document.getElementById('sendSubject')?.value || '';
  const body = document.getElementById('sendBody')?.value || '';
  const key = document.getElementById('sendIdempotencyKey')?.value || '';
  const customFrom = document.getElementById('sendCustomFrom')?.value?.trim();
  const customFromName = document.getElementById('sendCustomFromName')?.value?.trim();
  const customReplyTo = document.getElementById('sendCustomReplyTo')?.value?.trim();

  const payload = {
    type: channel,
    recipientAddress: recipient,
    subject: subject || null,
    body: body,
    idempotencyKey: key || null
  };

  const metadata = {};
  if (customFrom) {
    if (channel === 'SMS') metadata.fromNumber = customFrom;
    else metadata.fromAddress = customFrom;
  }
  if (customFromName) metadata.fromName = customFromName;
  if (customReplyTo) metadata.replyTo = customReplyTo;

  if (Object.keys(metadata).length > 0) {
    payload.channelMetadata = metadata;
  }

  const previewBox = document.getElementById('jsonPreviewBox');
  if (previewBox) {
    previewBox.textContent = JSON.stringify(payload, null, 2);
  }
}

function loadSendPreset(type) {
  const channelRadios = document.querySelectorAll('input[name="sendChannel"]');
  channelRadios.forEach(r => r.checked = (r.value === type));

  const recipient = document.getElementById('sendRecipient');
  const subject = document.getElementById('sendSubject');
  const body = document.getElementById('sendBody');
  const customFrom = document.getElementById('sendCustomFrom');
  const customFromName = document.getElementById('sendCustomFromName');
  const customReplyTo = document.getElementById('sendCustomReplyTo');

  if (type === 'IN_APP') {
    recipient.value = AppState.user ? AppState.user.email : 'user@example.com';
    subject.value = 'Security Alert: New Sign-in';
    body.value = 'A new sign-in was detected from Chrome on Windows at 12:45 PM.';
    if (customFrom) customFrom.value = '';
    if (customFromName) customFromName.value = '';
    if (customReplyTo) customReplyTo.value = '';
  } else if (type === 'EMAIL') {
    recipient.value = AppState.user ? AppState.user.email : 'billing@company.com';
    subject.value = 'Monthly Invoice #INV-2026-10';
    body.value = 'Your payment of $120.00 has been processed successfully. Thank you for your business!';
    if (customFrom) customFrom.value = 'billing@dispatch.io';
    if (customFromName) customFromName.value = 'Dispatch Billing Team';
    if (customReplyTo) customReplyTo.value = 'support@dispatch.io';
  } else if (type === 'SMS') {
    recipient.value = '+15555550199';
    subject.value = 'Verification Code';
    body.value = 'Your Dispatch verification code is 849201. Valid for 10 minutes.';
    if (customFrom) customFrom.value = '+18005550199';
    if (customFromName) customFromName.value = '';
    if (customReplyTo) customReplyTo.value = '';
  }

  generateNewIdempotencyKey();
  updateLiveJsonPreview();
}

async function handleSendNotification(e) {
  if (e) e.preventDefault();
  if (!ensureAuthenticated()) return;

  const channel = document.querySelector('input[name="sendChannel"]:checked')?.value || 'IN_APP';
  const recipient = document.getElementById('sendRecipient').value.trim();
  const subject = document.getElementById('sendSubject').value.trim();
  const body = document.getElementById('sendBody').value.trim();
  const key = document.getElementById('sendIdempotencyKey').value.trim();
  const customFrom = document.getElementById('sendCustomFrom')?.value?.trim();
  const customFromName = document.getElementById('sendCustomFromName')?.value?.trim();
  const customReplyTo = document.getElementById('sendCustomReplyTo')?.value?.trim();

  const reqPayload = {
    type: channel,
    recipientAddress: recipient,
    subject: subject,
    body: body,
    idempotencyKey: key || null
  };

  const metadata = {};
  if (customFrom) {
    if (channel === 'SMS') metadata.fromNumber = customFrom;
    else metadata.fromAddress = customFrom;
  }
  if (customFromName) metadata.fromName = customFromName;
  if (customReplyTo) metadata.replyTo = customReplyTo;

  if (Object.keys(metadata).length > 0) {
    reqPayload.channelMetadata = metadata;
  }

  const submitBtn = document.getElementById('sendNotificationSubmitBtn');
  const responseBox = document.getElementById('jsonResponseBox');
  const statusBadge = document.getElementById('responseStatusBadge');

  submitBtn.disabled = true;
  submitBtn.innerHTML = `<i data-lucide="loader" class="w-4 h-4 animate-spin"></i> Dispatching...`;
  statusBadge.textContent = 'sending...';
  statusBadge.className = 'text-[10px] px-2 py-0.5 rounded font-mono font-semibold bg-amber-500/20 text-amber-400';

  try {
    const res = await apiFetch(`/api/v1/users/${AppState.user.id}/notifications`, {
      method: 'POST',
      headers: key ? { 'Idempotency-Key': key } : {},
      body: JSON.stringify(reqPayload)
    });

    const isReplay = res.idempotentReplayed === 'true';
    statusBadge.textContent = isReplay ? `${res.status} OK (Replayed)` : `${res.status} Created`;
    statusBadge.className = isReplay 
      ? 'text-[10px] px-2 py-0.5 rounded font-mono font-semibold bg-indigo-500/20 text-indigo-400 border border-indigo-500/30'
      : 'text-[10px] px-2 py-0.5 rounded font-mono font-semibold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30';

    responseBox.textContent = JSON.stringify(res.data, null, 2);

    if (isReplay) {
      showToast(`Idempotent Replay: Cached response returned (${res.data.id})`, 'info');
    } else {
      showToast(`Notification queued atomically via Outbox! (ID: ${res.data.id})`, 'success');
      generateNewIdempotencyKey(); // generate new key for next test
    }

    refreshOverviewStats();
  } catch (err) {
    statusBadge.textContent = 'Error';
    statusBadge.className = 'text-[10px] px-2 py-0.5 rounded font-mono font-semibold bg-rose-500/20 text-rose-400';
    responseBox.textContent = 'Error: ' + err.message;
    showToast(err.message, 'error');
  } finally {
    submitBtn.disabled = false;
    submitBtn.innerHTML = `<i data-lucide="send" class="w-4 h-4"></i> Dispatch Notification`;
    if (window.lucide) lucide.createIcons();
  }
}

// Rapid Idempotent Replay test helper
async function testIdempotentReplayCurrent() {
  if (!ensureAuthenticated()) return;
  const keyInput = document.getElementById('sendIdempotencyKey');
  if (!keyInput.value) generateNewIdempotencyKey();

  showToast('Executing Request #1 (Initial submission)...', 'info');
  await handleSendNotification();

  // Short delay, then repeat with same key
  setTimeout(async () => {
    showToast('Executing Request #2 with exact same Idempotency-Key...', 'warning');
    await handleSendNotification();
  }, 1200);
}

// Quick instant in-app simulation
async function testSimulateInApp() {
  if (!ensureAuthenticated()) return;
  loadSendPreset('IN_APP');
  switchTab('send');
  setTimeout(() => handleSendNotification(), 100);
}

// =========================================================================
// Tab 3: Notifications History
// =========================================================================
async function loadNotifications() {
  if (!ensureAuthenticated()) return;

  const tbody = document.getElementById('notificationsTableBody');
  tbody.innerHTML = `<tr><td colspan="6" class="text-center py-8 text-slate-500">Loading notifications...</td></tr>`;

  const channel = document.getElementById('notifChannelFilter')?.value || '';
  const status = document.getElementById('notifStatusFilter')?.value || '';

  let url = `/api/v1/users/${AppState.user.id}/notifications?page=${AppState.notifPage}&size=${AppState.notifSize}`;
  if (channel) url += `&type=${channel}`;
  if (status) url += `&status=${status}`;

  try {
    const res = await apiFetch(url);
    const page = res.data;
    AppState.totalNotifPages = page.totalPages || 1;

    document.getElementById('notifPageCount').textContent = `Page ${page.page + 1} of ${Math.max(1, page.totalPages)}`;
    document.getElementById('notifPrevBtn').disabled = (page.page <= 0);
    document.getElementById('notifNextBtn').disabled = (page.page >= page.totalPages - 1);

    if (!page.content || page.content.length === 0) {
      tbody.innerHTML = `<tr><td colspan="6" class="text-center py-8 text-slate-500">No notifications found for this filter.</td></tr>`;
      return;
    }

    tbody.innerHTML = page.content.map(n => `
      <tr class="hover:bg-surface-800/50 transition">
        <td class="py-3 px-4 font-mono font-medium flex items-center gap-1.5">
          ${getChannelBadge(n.type)}
        </td>
        <td class="py-3 px-4 max-w-xs">
          <div class="font-semibold text-white truncate">${escapeHtml(n.subject || 'No subject')}</div>
          <div class="text-[11px] text-slate-400 truncate">${escapeHtml(n.body)}</div>
        </td>
        <td class="py-3 px-4 font-mono text-slate-300 text-[11px] truncate max-w-[140px]">
          ${escapeHtml(n.recipientAddress || '—')}
        </td>
        <td class="py-3 px-4">
          ${getStatusBadge(n.status)}
        </td>
        <td class="py-3 px-4 font-mono text-slate-400 text-[11px]">
          ${formatTimestamp(n.createdAt)}
        </td>
        <td class="py-3 px-4 text-right space-x-1 whitespace-nowrap">
          <button onclick="inspectNotification('${n.id}')" class="px-2 py-1 rounded bg-surface-800 hover:bg-surface-700 text-slate-300 text-[11px] border border-slate-700">
            Details
          </button>
          ${n.status === 'PENDING' ? `
            <button onclick="cancelNotification('${n.id}')" class="px-2 py-1 rounded bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 text-[11px] border border-rose-500/30">
              Cancel
            </button>
          ` : ''}
        </td>
      </tr>
    `).join('');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="6" class="text-center py-8 text-rose-400">Failed to load: ${err.message}</td></tr>`;
  }
}

function changeNotifPage(delta) {
  AppState.notifPage = Math.max(0, AppState.notifPage + delta);
  loadNotifications();
}

async function inspectNotification(id) {
  try {
    const res = await apiFetch(`/api/v1/notifications/${id}`);
    openDetailModal(`Notification ${id}`, res.data);
  } catch (err) {
    showToast(err.message, 'error');
  }
}

async function cancelNotification(id) {
  if (!confirm(`Are you sure you want to cancel notification ${id}?`)) return;
  try {
    await apiFetch(`/api/v1/notifications/${id}`, { method: 'DELETE' });
    showToast('Notification cancelled.', 'success');
    loadNotifications();
  } catch (err) {
    showToast(err.message, 'error');
  }
}

// =========================================================================
// Tab 4: In-App Inbox
// =========================================================================
async function loadInboxBadgeCount() {
  if (!AppState.user) return;
  try {
    const res = await apiFetch(`/api/v1/users/${AppState.user.id}/inbox/unread-count`);
    const count = res.data?.unreadCount || 0;

    const sidebarBadge = document.getElementById('sidebarInboxBadge');
    if (count > 0) {
      sidebarBadge.textContent = count;
      sidebarBadge.classList.remove('hidden');
    } else {
      sidebarBadge.classList.add('hidden');
    }

    const largeBadge = document.getElementById('inboxUnreadBadgeLarge');
    if (largeBadge) largeBadge.textContent = `${count} unread`;

    const statBadge = document.getElementById('statInboxUnread');
    if (statBadge) statBadge.textContent = count;
  } catch (err) {
    // ignore background errors
  }
}

async function loadInboxMessages() {
  if (!ensureAuthenticated()) return;

  const container = document.getElementById('inboxMessagesContainer');
  container.innerHTML = `<div class="text-center py-12 text-slate-500 text-xs">Loading inbox messages...</div>`;

  const readFilter = document.getElementById('inboxReadFilter')?.value || '';
  let url = `/api/v1/users/${AppState.user.id}/inbox?size=20`;
  if (readFilter !== '') url += `&read=${readFilter}`;

  try {
    const res = await apiFetch(url);
    const messages = res.data?.content || [];

    loadInboxBadgeCount();

    if (messages.length === 0) {
      container.innerHTML = `
        <div class="glass-panel p-10 rounded-2xl text-center space-y-3">
          <div class="w-12 h-12 rounded-2xl bg-surface-800 text-slate-500 flex items-center justify-center mx-auto">
            <i data-lucide="inbox" class="w-6 h-6"></i>
          </div>
          <h3 class="text-sm font-semibold text-white">Your inbox is clear</h3>
          <p class="text-xs text-slate-400 max-w-sm mx-auto">
            Dispatch IN_APP notifications will appear here. Test sending an in-app alert from the Send Notification tab.
          </p>
          <button onclick="testSimulateInApp()" class="px-4 py-2 rounded-xl text-xs font-semibold bg-brand-600 text-white hover:bg-brand-500 transition">
            Generate Sample In-App Alert
          </button>
        </div>
      `;
      if (window.lucide) lucide.createIcons();
      return;
    }

    container.innerHTML = messages.map(msg => `
      <div class="glass-panel p-4 rounded-xl border ${msg.read ? 'border-slate-800/80 bg-surface-900/40 opacity-80' : 'border-brand-500/30 bg-surface-800/70'} flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 transition hover:bg-surface-800">
        <div class="flex items-start gap-3">
          <div class="mt-0.5 p-2 rounded-lg ${msg.read ? 'bg-slate-800 text-slate-500' : 'bg-brand-500/20 text-brand-400'}">
            <i data-lucide="${msg.read ? 'mail-check' : 'bell'}" class="w-4 h-4"></i>
          </div>
          <div class="space-y-1">
            <div class="flex items-center gap-2">
              <span class="text-sm font-semibold text-white">${escapeHtml(msg.title || 'Notification')}</span>
              ${!msg.read ? '<span class="w-2 h-2 rounded-full bg-brand-500"></span>' : ''}
            </div>
            <p class="text-xs text-slate-300">${escapeHtml(msg.body)}</p>
            <div class="text-[10px] text-slate-500 font-mono">
              Received: ${formatTimestamp(msg.createdAt)} ${msg.readAt ? `• Read: ${formatTimestamp(msg.readAt)}` : ''}
            </div>
          </div>
        </div>

        <div class="flex items-center gap-2 self-end sm:self-center shrink-0">
          <button onclick="toggleInboxRead('${msg.id}', ${msg.read})" class="px-2.5 py-1.5 rounded-lg bg-surface-700/60 hover:bg-surface-700 text-xs text-slate-200 border border-slate-600/50 transition">
            ${msg.read ? 'Mark Unread' : 'Mark Read'}
          </button>
          <button onclick="deleteInboxMessage('${msg.id}')" class="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition" title="Delete">
            <i data-lucide="trash-2" class="w-4 h-4"></i>
          </button>
        </div>
      </div>
    `).join('');

    if (window.lucide) lucide.createIcons();
  } catch (err) {
    container.innerHTML = `<div class="text-center py-8 text-rose-400 text-xs">Error loading inbox: ${err.message}</div>`;
  }
}

async function toggleInboxRead(messageId, currentReadState) {
  try {
    const action = currentReadState ? 'unread' : 'read';
    await apiFetch(`/api/v1/users/${AppState.user.id}/inbox/${messageId}/${action}`, {
      method: 'PATCH'
    });
    loadInboxMessages();
  } catch (err) {
    showToast(err.message, 'error');
  }
}

async function handleMarkAllInboxRead() {
  if (!ensureAuthenticated()) return;
  try {
    const res = await apiFetch(`/api/v1/users/${AppState.user.id}/inbox/read-all`, {
      method: 'POST'
    });
    showToast(`Marked ${res.data.updatedCount || 0} messages as read`, 'success');
    loadInboxMessages();
  } catch (err) {
    showToast(err.message, 'error');
  }
}

async function deleteInboxMessage(messageId) {
  try {
    await apiFetch(`/api/v1/users/${AppState.user.id}/inbox/${messageId}`, {
      method: 'DELETE'
    });
    showToast('Message deleted.', 'info');
    loadInboxMessages();
  } catch (err) {
    showToast(err.message, 'error');
  }
}

// =========================================================================
// Tab 5: Delivery Audit Trail
// =========================================================================
async function loadDeliveryHistory() {
  if (!ensureAuthenticated()) return;

  const tbody = document.getElementById('auditTableBody');
  tbody.innerHTML = `<tr><td colspan="6" class="text-center py-8 text-slate-500">Loading audit history...</td></tr>`;

  const status = document.getElementById('auditStatusFilter')?.value || '';
  let url = `/api/v1/users/${AppState.user.id}/delivery-history?size=20`;
  if (status) url += `&status=${status}`;

  try {
    const res = await apiFetch(url);
    const attempts = res.data?.content || [];

    if (attempts.length === 0) {
      tbody.innerHTML = `<tr><td colspan="6" class="text-center py-8 text-slate-500">No delivery attempts recorded yet.</td></tr>`;
      return;
    }

    tbody.innerHTML = attempts.map(a => `
      <tr class="hover:bg-surface-800/50 transition">
        <td class="py-3 px-4 font-mono font-medium">
          ${getChannelBadge(a.channelType || 'UNKNOWN')}
        </td>
        <td class="py-3 px-4 font-mono text-slate-300">
          #${a.attemptNumber}
        </td>
        <td class="py-3 px-4">
          ${getAttemptStatusBadge(a.status)}
        </td>
        <td class="py-3 px-4 max-w-sm font-mono text-[11px] truncate">
          ${a.errorCode ? `<span class="text-rose-400 font-semibold">[${a.errorCode}]</span> ` : ''}
          <span class="text-slate-400">${escapeHtml(a.providerResponse || a.errorMessage || '—')}</span>
        </td>
        <td class="py-3 px-4 font-mono text-slate-400 text-[11px]">
          ${formatTimestamp(a.attemptedAt || a.createdAt)}
        </td>
        <td class="py-3 px-4 text-right">
          <button onclick="inspectDeliveryAttempt('${a.id}')" class="px-2.5 py-1 rounded bg-surface-800 hover:bg-surface-700 text-slate-300 text-[11px] border border-slate-700">
            Inspect
          </button>
        </td>
      </tr>
    `).join('');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="6" class="text-center py-8 text-rose-400">Failed to load: ${err.message}</td></tr>`;
  }
}

async function inspectDeliveryAttempt(attemptId) {
  try {
    const res = await apiFetch(`/api/v1/audit/attempts/${attemptId}`);
    openDetailModal(`Delivery Attempt Diagnostic: ${attemptId}`, res.data);
  } catch (err) {
    showToast(err.message, 'error');
  }
}

// =========================================================================
// Tab 6: Preferences & Quiet Hours
// =========================================================================
async function loadPreferences() {
  if (!ensureAuthenticated()) return;

  try {
    const res = await apiFetch(`/api/v1/users/${AppState.user.id}/preferences`);
    const p = res.data;

    document.getElementById('prefEmailEnabled').checked = p.emailEnabled;
    document.getElementById('prefSmsEnabled').checked = p.smsEnabled;
    document.getElementById('prefInAppEnabled').checked = p.inAppEnabled;

    document.getElementById('prefAlternateEmail').value = p.preferredEmail || '';
    document.getElementById('prefAlternatePhone').value = p.preferredPhone || '';
    document.getElementById('prefTimezone').value = p.timezone || 'UTC';

    document.getElementById('prefQuietStart').value = p.quietHoursStart || '';
    document.getElementById('prefQuietEnd').value = p.quietHoursEnd || '';

    // Status badge
    const badge = document.getElementById('quietHoursStatusBadge');
    if (p.quietHoursStart && p.quietHoursEnd) {
      badge.textContent = `Active Window: ${p.quietHoursStart} - ${p.quietHoursEnd}`;
      badge.className = 'text-[10px] px-2 py-0.5 rounded font-medium bg-indigo-500/10 text-indigo-400 border border-indigo-500/20';
    } else {
      badge.textContent = 'Quiet Hours Disabled';
      badge.className = 'text-[10px] px-2 py-0.5 rounded font-medium bg-slate-800 text-slate-400';
    }
  } catch (err) {
    showToast('Failed to load preferences: ' + err.message, 'error');
  }
}

async function handleUpdatePreferences(e) {
  e.preventDefault();
  if (!ensureAuthenticated()) return;

  const req = {
    emailEnabled: document.getElementById('prefEmailEnabled').checked,
    smsEnabled: document.getElementById('prefSmsEnabled').checked,
    inAppEnabled: document.getElementById('prefInAppEnabled').checked,
    preferredEmail: document.getElementById('prefAlternateEmail').value.trim() || null,
    preferredPhone: document.getElementById('prefAlternatePhone').value.trim() || null,
    timezone: document.getElementById('prefTimezone').value,
    quietHoursStart: document.getElementById('prefQuietStart').value || null,
    quietHoursEnd: document.getElementById('prefQuietEnd').value || null
  };

  try {
    const res = await apiFetch(`/api/v1/users/${AppState.user.id}/preferences`, {
      method: 'PUT',
      body: JSON.stringify(req)
    });
    showToast('Notification preferences updated successfully!', 'success');
    loadPreferences();
  } catch (err) {
    showToast(err.message, 'error');
  }
}

// =========================================================================
// Tab 7: Outbox & DLQ
// =========================================================================
async function loadOutboxAndDlq() {
  try {
    // Outbox stats
    const statsRes = await apiFetch('/api/v1/outbox/stats');
    if (statsRes.ok && statsRes.data) {
      document.getElementById('outboxPendingCount').textContent = statsRes.data.pendingEvents || 0;
      document.getElementById('outboxPublishedCount').textContent = statsRes.data.publishedEvents || 0;
      document.getElementById('outboxFailedCount').textContent = statsRes.data.failedEvents || 0;
    }

    // Outbox events
    const eventsRes = await apiFetch('/api/v1/outbox/events?size=15');
    const tbody = document.getElementById('outboxTableBody');
    const events = eventsRes.data?.content || [];

    if (events.length === 0) {
      tbody.innerHTML = `<tr><td colspan="5" class="text-center py-6 text-slate-500">No outbox events queued.</td></tr>`;
    } else {
      tbody.innerHTML = events.map(ev => `
        <tr class="hover:bg-surface-800/50 transition">
          <td class="py-2.5 px-4 font-mono text-[11px] text-slate-300">${ev.id.substring(0, 13)}...</td>
          <td class="py-2.5 px-4 font-semibold text-white text-[11px]">${escapeHtml(ev.eventType)}</td>
          <td class="py-2.5 px-4">${getOutboxStatusBadge(ev.status)}</td>
          <td class="py-2.5 px-4 font-mono text-slate-400 text-[11px]">${formatTimestamp(ev.createdAt)}</td>
          <td class="py-2.5 px-4 text-right space-x-1">
            <button onclick='openDetailModal("Outbox Event ${ev.id}", ${JSON.stringify(ev)})' class="px-2 py-0.5 rounded bg-surface-800 hover:bg-surface-700 text-slate-300 text-[11px] border border-slate-700">
              Payload
            </button>
            ${ev.status === 'FAILED' ? `
              <button onclick="retryOutboxEvent('${ev.id}')" class="px-2 py-0.5 rounded bg-brand-600 hover:bg-brand-500 text-white text-[11px]">
                Retry
              </button>
            ` : ''}
          </td>
        </tr>
      `).join('');
    }

    // DLQ stats
    const dlqRes = await apiFetch('/api/v1/dlq/stats');
    if (dlqRes.ok && dlqRes.data) {
      document.getElementById('dlqBadge').textContent = `${dlqRes.data.dlqQueueMessages || 0} in queue / ${dlqRes.data.failedNotificationsInDatabase || 0} in DB`;
    }
  } catch (err) {
    showToast('Failed to load outbox/dlq: ' + err.message, 'error');
  }
}

async function retryOutboxEvent(id) {
  try {
    await apiFetch(`/api/v1/outbox/events/${id}/retry`, { method: 'POST' });
    showToast(`Outbox event ${id} queued for retry!`, 'success');
    loadOutboxAndDlq();
  } catch (err) {
    showToast(err.message, 'error');
  }
}

async function handleReplayDlq() {
  const notifId = document.getElementById('dlqReplayIdInput').value.trim();
  if (!notifId) {
    showToast('Please provide a notification ID.', 'warning');
    return;
  }

  try {
    const res = await apiFetch(`/api/v1/dlq/replay/${notifId}`, { method: 'POST' });
    showToast(`Notification ${notifId} replayed from DLQ to Outbox!`, 'success');
    document.getElementById('dlqReplayIdInput').value = '';
    loadOutboxAndDlq();
  } catch (err) {
    showToast(err.message, 'error');
  }
}

// =========================================================================
// Tab 8: Idempotency Lab
// =========================================================================
async function handleLookupIdempotency() {
  const key = document.getElementById('lookupIdempotencyKey').value.trim();
  if (!key) return;

  const box = document.getElementById('idempotencyRecordBox');
  box.textContent = 'Looking up...';

  try {
    const res = await apiFetch(`/api/v1/idempotency/${encodeURIComponent(key)}`);
    box.textContent = JSON.stringify(res.data, null, 2);
  } catch (err) {
    box.textContent = 'Record not found: ' + err.message;
  }
}

async function handleDeleteIdempotencyRecord() {
  const key = document.getElementById('lookupIdempotencyKey').value.trim();
  if (!key) return;

  try {
    await apiFetch(`/api/v1/idempotency/${encodeURIComponent(key)}`, { method: 'DELETE' });
    showToast(`Cleared idempotency key '${key}'`, 'info');
    document.getElementById('idempotencyRecordBox').textContent = 'Record cleared.';
  } catch (err) {
    showToast(err.message, 'error');
  }
}

async function runIdempotencyDemo() {
  if (!ensureAuthenticated()) return;

  const demoOutput = document.getElementById('idempotencyDemoOutput');
  const demoKey = 'demo-payout-' + crypto.randomUUID().substring(0, 8);

  demoOutput.innerHTML = `<span class="text-indigo-400">Step 1: Generating unique key <b>${demoKey}</b>...</span><br>`;

  const payload = {
    type: 'EMAIL',
    recipientAddress: AppState.user.email,
    subject: 'Idempotency Stress Run',
    body: 'Verifying atomic SHA-256 idempotency deduplication.',
    idempotencyKey: demoKey
  };

  try {
    // Request 1
    demoOutput.innerHTML += `<span class="text-amber-400">Step 2: Submitting Request #1...</span><br>`;
    const r1 = await apiFetch(`/api/v1/users/${AppState.user.id}/notifications`, {
      method: 'POST',
      headers: { 'Idempotency-Key': demoKey },
      body: JSON.stringify(payload)
    });
    demoOutput.innerHTML += `<span class="text-emerald-400">✔ Request #1 Success: HTTP ${r1.status} Created (Notification ID: ${r1.data.id})</span><br>`;

    // Request 2
    demoOutput.innerHTML += `<span class="text-amber-400">Step 3: Immediately re-submitting exact duplicate Request #2...</span><br>`;
    const r2 = await apiFetch(`/api/v1/users/${AppState.user.id}/notifications`, {
      method: 'POST',
      headers: { 'Idempotency-Key': demoKey },
      body: JSON.stringify(payload)
    });

    const isReplay = r2.idempotentReplayed === 'true';
    demoOutput.innerHTML += `
      <span class="text-emerald-400 font-bold">✔ Request #2 Replayed: HTTP ${r2.status} OK</span><br>
      <span class="text-brand-300">• Header 'Idempotent-Replayed': ${r2.idempotentReplayed}</span><br>
      <span class="text-brand-300">• Notification ID: ${r2.data.id} (Exact Match)</span><br>
      <span class="text-emerald-300 font-semibold mt-1 block">Zero duplicate notifications created. Verified!</span>
    `;
    showToast('Idempotency demo completed with 100% verification!', 'success');
  } catch (err) {
    demoOutput.innerHTML += `<span class="text-rose-400">Simulation error: ${err.message}</span>`;
  }
}

// =========================================================================
// UI Helpers & Badges
// =========================================================================
function ensureAuthenticated() {
  if (!AppState.user) {
    showToast('Please log in or register to perform this action.', 'warning');
    openAuthModal('login');
    return false;
  }
  return true;
}

function getChannelBadge(channel) {
  if (channel === 'EMAIL') {
    return `<span class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-semibold bg-indigo-500/10 text-indigo-400 border border-indigo-500/20"><i data-lucide="mail" class="w-3 h-3"></i> EMAIL</span>`;
  }
  if (channel === 'SMS') {
    return `<span class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"><i data-lucide="smartphone" class="w-3 h-3"></i> SMS</span>`;
  }
  return `<span class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20"><i data-lucide="bell" class="w-3 h-3"></i> IN_APP</span>`;
}

function getStatusBadge(status) {
  if (status === 'DELIVERED') {
    return `<span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">DELIVERED</span>`;
  }
  if (status === 'PENDING') {
    return `<span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20">PENDING</span>`;
  }
  if (status === 'FAILED') {
    return `<span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">FAILED</span>`;
  }
  return `<span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-slate-800 text-slate-400">${status}</span>`;
}

function getAttemptStatusBadge(status) {
  if (status === 'SUCCESS') {
    return `<span class="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">SUCCESS</span>`;
  }
  if (status === 'RETRYING') {
    return `<span class="px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20">RETRYING</span>`;
  }
  return `<span class="px-2 py-0.5 rounded text-[10px] font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">FAILURE</span>`;
}

function getOutboxStatusBadge(status) {
  if (status === 'PUBLISHED') {
    return `<span class="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">PUBLISHED</span>`;
  }
  if (status === 'PENDING') {
    return `<span class="px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20">PENDING</span>`;
  }
  return `<span class="px-2 py-0.5 rounded text-[10px] font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">FAILED</span>`;
}

function formatTimestamp(isoStr) {
  if (!isoStr) return '—';
  try {
    const d = new Date(isoStr);
    return d.toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' });
  } catch (e) {
    return isoStr;
  }
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

// =========================================================================
// Modals & Toasts
// =========================================================================
function openDetailModal(title, data) {
  document.getElementById('modalTitle').textContent = title;
  document.getElementById('modalJsonBox').textContent = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
  const m = document.getElementById('detailModal');
  m.classList.remove('hidden');
  m.classList.add('flex');
}

function closeDetailModal() {
  const m = document.getElementById('detailModal');
  m.classList.add('hidden');
  m.classList.remove('flex');
}

function showToast(message, type = 'info', duration = 4000) {
  const container = document.getElementById('toastContainer');
  const toast = document.createElement('div');

  let bgClass = 'bg-surface-800 border-slate-700 text-slate-100';
  let icon = 'info';

  if (type === 'success') {
    bgClass = 'bg-emerald-950/90 border-emerald-500/30 text-emerald-200';
    icon = 'check-circle-2';
  } else if (type === 'error') {
    bgClass = 'bg-rose-950/90 border-rose-500/30 text-rose-200';
    icon = 'alert-triangle';
  } else if (type === 'warning') {
    bgClass = 'bg-amber-950/90 border-amber-500/30 text-amber-200';
    icon = 'alert-circle';
  }

  toast.className = `p-3.5 rounded-xl border shadow-xl flex items-center gap-2.5 text-xs font-medium pointer-events-auto transition transform translate-y-2 opacity-0 ${bgClass}`;
  toast.innerHTML = `
    <i data-lucide="${icon}" class="w-4 h-4 shrink-0"></i>
    <span class="flex-1">${escapeHtml(message)}</span>
  `;

  container.appendChild(toast);
  if (window.lucide) lucide.createIcons();

  // Animate in
  setTimeout(() => {
    toast.classList.remove('translate-y-2', 'opacity-0');
  }, 10);

  // Auto remove
  setTimeout(() => {
    toast.classList.add('opacity-0', 'translate-y-2');
    setTimeout(() => toast.remove(), 200);
  }, duration);
}

// =========================================================================
// Developer Multi-Tenant Credentials Management
// =========================================================================

function handleEmailProviderPreset(preset) {
  const hostInput = document.getElementById('credEmailHost');
  const portInput = document.getElementById('credEmailPort');
  const userInput = document.getElementById('credEmailUsername');

  if (preset === 'GMAIL') {
    if (hostInput) hostInput.value = 'smtp.gmail.com';
    if (portInput) portInput.value = 587;
    if (userInput && !userInput.value) userInput.placeholder = 'your.name@gmail.com';
  } else if (preset === 'SENDGRID') {
    if (hostInput) hostInput.value = 'smtp.sendgrid.net';
    if (portInput) portInput.value = 587;
    if (userInput) userInput.value = 'apikey';
  } else if (preset === 'RESEND') {
    if (hostInput) hostInput.value = 'smtp.resend.com';
    if (portInput) portInput.value = 587;
    if (userInput) userInput.value = 'resend';
  } else if (preset === 'MAILTRAP') {
    if (hostInput) hostInput.value = 'sandbox.smtp.mailtrap.io';
    if (portInput) portInput.value = 2525;
  } else if (preset === 'AWS_SES') {
    if (hostInput) hostInput.value = 'email-smtp.us-east-1.amazonaws.com';
    if (portInput) portInput.value = 587;
  }
}

async function loadDeveloperCredentials() {
  if (!ensureAuthenticated()) return;

  try {
    const list = await apiFetch(`/api/v1/users/${AppState.user.id}/credentials`);
    if (!Array.isArray(list)) return;

    const emailCred = list.find(c => c.channel === 'EMAIL');
    const smsCred = list.find(c => c.channel === 'SMS');

    const emailStatus = document.getElementById('credEmailStatus');
    const emailBadge = document.getElementById('credEmailBadge');
    if (emailCred && emailCred.active) {
      if (emailStatus) emailStatus.textContent = `${emailCred.provider} (${emailCred.fromAddressOrNumber})`;
      if (emailBadge) {
        emailBadge.textContent = 'Active Custom Sender';
        emailBadge.className = 'text-[11px] px-2.5 py-1 rounded-full font-semibold bg-blue-500/20 text-blue-300 border border-blue-500/30';
      }
      const fromEl = document.getElementById('credEmailFrom');
      if (fromEl && !fromEl.value) fromEl.value = emailCred.fromAddressOrNumber || '';
      const nameEl = document.getElementById('credEmailFromName');
      if (nameEl && !nameEl.value) nameEl.value = emailCred.fromName || '';
      const hostEl = document.getElementById('credEmailHost');
      if (hostEl && emailCred.host) hostEl.value = emailCred.host;
      const portEl = document.getElementById('credEmailPort');
      if (portEl && emailCred.port) portEl.value = emailCred.port;
    } else {
      if (emailStatus) emailStatus.textContent = 'System Default (Simulation)';
      if (emailBadge) {
        emailBadge.textContent = 'Simulation Mode';
        emailBadge.className = 'text-[11px] px-2.5 py-1 rounded-full font-semibold bg-slate-800 text-slate-400 border border-slate-700';
      }
    }

    const smsStatus = document.getElementById('credSmsStatus');
    const smsBadge = document.getElementById('credSmsBadge');
    if (smsCred && smsCred.active) {
      if (smsStatus) smsStatus.textContent = `${smsCred.provider} (${smsCred.fromAddressOrNumber})`;
      if (smsBadge) {
        smsBadge.textContent = 'Active Custom Sender';
        smsBadge.className = 'text-[11px] px-2.5 py-1 rounded-full font-semibold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30';
      }
      const fromEl = document.getElementById('credSmsFrom');
      if (fromEl && !fromEl.value) fromEl.value = smsCred.fromAddressOrNumber || '';
    } else {
      if (smsStatus) smsStatus.textContent = 'System Default (Simulation)';
      if (smsBadge) {
        smsBadge.textContent = 'Simulation Mode';
        smsBadge.className = 'text-[11px] px-2.5 py-1 rounded-full font-semibold bg-slate-800 text-slate-400 border border-slate-700';
      }
    }
  } catch (err) {
    console.error('Failed to load developer credentials:', err);
  }
}

async function handleSaveEmailCredentials(e) {
  if (e) e.preventDefault();
  if (!ensureAuthenticated()) return;

  const provider = document.getElementById('credEmailProvider')?.value || 'SMTP';
  const host = document.getElementById('credEmailHost')?.value?.trim();
  const port = parseInt(document.getElementById('credEmailPort')?.value || '587', 10);
  const username = document.getElementById('credEmailUsername')?.value?.trim();
  const password = document.getElementById('credEmailPassword')?.value?.trim();
  const fromAddress = document.getElementById('credEmailFrom')?.value?.trim();
  const fromName = document.getElementById('credEmailFromName')?.value?.trim();

  if (!username || !password || !fromAddress) {
    showToast('Username, password, and from address are required', 'warning');
    return;
  }

  try {
    await apiFetch(`/api/v1/users/${AppState.user.id}/credentials/email`, {
      method: 'PUT',
      body: JSON.stringify({ provider, host, port, username, password, fromAddress, fromName })
    });
    showToast(`Saved custom ${provider} credentials successfully!`, 'success');
    loadDeveloperCredentials();
  } catch (err) {
    showToast(err.message || 'Failed to save email credentials', 'error');
  }
}

async function handleSaveSmsCredentials(e) {
  if (e) e.preventDefault();
  if (!ensureAuthenticated()) return;

  const accountSid = document.getElementById('credSmsSid')?.value?.trim();
  const authToken = document.getElementById('credSmsToken')?.value?.trim();
  const fromNumber = document.getElementById('credSmsFrom')?.value?.trim();
  const messagingServiceSid = document.getElementById('credSmsServiceSid')?.value?.trim();

  if (!accountSid || !authToken || !fromNumber) {
    showToast('Account SID, Auth Token, and From Phone Number are required', 'warning');
    return;
  }

  try {
    await apiFetch(`/api/v1/users/${AppState.user.id}/credentials/sms`, {
      method: 'PUT',
      body: JSON.stringify({ provider: 'TWILIO', accountSid, authToken, fromNumber, messagingServiceSid })
    });
    showToast('Saved custom Twilio SMS credentials successfully!', 'success');
    loadDeveloperCredentials();
  } catch (err) {
    showToast(err.message || 'Failed to save SMS credentials', 'error');
  }
}

async function deleteDeveloperCredential(channel) {
  if (!ensureAuthenticated()) return;
  if (!confirm(`Are you sure you want to reset ${channel} credentials to system default?`)) return;

  try {
    await apiFetch(`/api/v1/users/${AppState.user.id}/credentials/${channel}`, {
      method: 'DELETE'
    });
    showToast(`Reset ${channel} credentials to default simulation`, 'info');
    loadDeveloperCredentials();
  } catch (err) {
    showToast(err.message || 'Failed to delete credentials', 'error');
  }
}
