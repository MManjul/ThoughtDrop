/**
 * ThoughtDrop Web Studio - Main Application Controller
 */

import { initAuth, loginWithGoogle, logout, getCurrentUser, onUserChanged } from './auth.js';
import { initDb, saveNote, deleteNote, togglePinNote, onNotesChanged } from './db.js';
import { refineTextWithAI, getGeminiKeys, getGroqKeys, getSelectedModel, getSelectedEngine } from './ai.js';
import { exportNotePdf } from './pdf.js';

let allNotes = [];
let selectedCategory = 'all';
let searchQuery = '';
let currentEditingNote = null;
let currentSharingNote = null;

// Initialize on DOM Ready or immediately if DOM is already ready
function initApp() {
  setupEventListeners();
  initAuth();

  onUserChanged((user) => {
    updateAuthUI(user);
    initDb(user);
  });

  onNotesChanged((notes) => {
    allNotes = notes;
    renderNotesGrid();
    // If modal is open, refresh its view
    if (currentEditingNote) {
      const refreshed = allNotes.find(n => n.id === currentEditingNote.id);
      if (refreshed) {
        currentEditingNote = refreshed;
        populateDetailView(refreshed);
      }
    }
  });
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initApp);
} else {
  initApp();
}

function setupEventListeners() {
  // Global Escape Key Listener - Returns to main page
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' || e.key === 'Esc') {
      closeAllModals();
    }
  });

  // Auth button
  document.getElementById('auth-btn')?.addEventListener('click', async () => {
    const user = getCurrentUser();
    if (user && user.isCloud) {
      if (confirm("Do you want to sign out?")) {
        await logout();
      }
    } else {
      try {
        await loginWithGoogle();
      } catch (err) {
        alert("Google Sign-in failed: " + err.message);
      }
    }
  });

  // Settings Modal triggers
  document.getElementById('settings-btn')?.addEventListener('click', openSettingsModal);
  document.getElementById('close-settings-btn')?.addEventListener('click', closeSettingsModal);
  document.getElementById('save-settings-btn')?.addEventListener('click', saveSettings);
  document.getElementById('add-gemini-key-btn')?.addEventListener('click', () => window.addGeminiKeyFromInput?.());
  document.getElementById('add-groq-key-btn')?.addEventListener('click', () => window.addGroqKeyFromInput?.());

  // Quick Capture "Refine with AI"
  document.getElementById('refine-btn')?.addEventListener('click', handleRefineNote);

  // Quick Capture "Save Raw"
  document.getElementById('save-raw-btn')?.addEventListener('click', handleSaveRawNote);

  // Search
  document.getElementById('search-input')?.addEventListener('input', (e) => {
    searchQuery = e.target.value.toLowerCase().trim();
    renderNotesGrid();
  });

  // Category filter chips
  document.querySelectorAll('.filter-chip').forEach(chip => {
    chip.addEventListener('click', () => {
      document.querySelectorAll('.filter-chip').forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      selectedCategory = chip.dataset.category || 'all';
      renderNotesGrid();
    });
  });

  // Note Detail Modal: Close
  document.getElementById('close-detail-btn')?.addEventListener('click', closeDetailModal);

  // Note Detail Modal: Export PDF
  document.getElementById('detail-pdf-btn')?.addEventListener('click', () => {
    if (currentEditingNote) exportNotePdf(currentEditingNote);
  });

  // Note Detail Modal: Share
  document.getElementById('detail-share-btn')?.addEventListener('click', () => {
    if (currentEditingNote) openShareModal(currentEditingNote);
  });

  // Note Detail Modal: Switch to Content Edit Mode
  document.getElementById('open-edit-mode-btn')?.addEventListener('click', () => {
    switchDetailMode('edit');
  });

  // Note Detail Modal: Cancel Edit
  document.getElementById('detail-cancel-edit-btn')?.addEventListener('click', () => {
    switchDetailMode('view');
  });

  // Note Detail Modal: Refine Content with AI
  document.getElementById('detail-refine-btn')?.addEventListener('click', handleRefineInModal);

  // Note Detail Modal: Save Raw Content Without AI
  document.getElementById('detail-save-raw-btn')?.addEventListener('click', handleSaveRawInModal);

  // Share Modal triggers
  document.getElementById('close-share-btn')?.addEventListener('click', closeShareModal);
  document.getElementById('copy-link-btn')?.addEventListener('click', handleCopyShareLink);
  document.getElementById('share-copy-text-btn')?.addEventListener('click', handleCopyFullNoteText);
  document.getElementById('share-whatsapp-btn')?.addEventListener('click', handleShareWhatsApp);
  document.getElementById('share-email-btn')?.addEventListener('click', handleShareEmail);
  document.getElementById('share-twitter-btn')?.addEventListener('click', handleShareTwitter);

  // Close modals when clicking backdrop
  document.querySelectorAll('.modal-overlay').forEach(overlay => {
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) {
        closeAllModals();
      }
    });
  });
}

function closeAllModals() {
  closeDetailModal();
  closeShareModal();
  closeSettingsModal();
}

function updateAuthUI(user) {
  const authPill = document.getElementById('auth-pill-content');
  if (!authPill) return;

  if (user) {
    authPill.innerHTML = `
      <img src="${user.photoURL}" class="avatar" alt="User">
      <span style="font-weight: 600;">${user.displayName}</span>
      <span style="font-size: 11px; opacity: 0.6;">${user.isCloud ? '(Google)' : '(Local)'}</span>
    `;
  } else {
    authPill.innerHTML = `
      <span style="color: var(--accent-crimson); font-weight: 700;">✦ Sign in with Google</span>
    `;
  }
}

// Quick Capture - AI Refinement
async function handleRefineNote() {
  const textarea = document.getElementById('capture-textarea');
  const categorySelect = document.getElementById('capture-category');
  const refineBtn = document.getElementById('refine-btn');

  const text = textarea.value.trim();
  if (!text) {
    alert("Please write or paste some notes before refining.");
    return;
  }

  const origBtnText = refineBtn.innerHTML;
  refineBtn.disabled = true;
  refineBtn.innerHTML = `<span class="spinner"></span> Refining with Gemini...`;

  try {
    const refined = await refineTextWithAI(text, categorySelect.value);
    const newNote = {
      ...refined,
      transcript: text,
      createdAt: Date.now()
    };

    const user = getCurrentUser();
    await saveNote(newNote, user);

    textarea.value = '';
    refineBtn.innerHTML = `✓ Refined &amp; Saved!`;
    setTimeout(() => {
      refineBtn.disabled = false;
      refineBtn.innerHTML = origBtnText;
    }, 1500);
  } catch (err) {
    alert("AI Refinement Error: " + err.message);
    refineBtn.disabled = false;
    refineBtn.innerHTML = origBtnText;
  }
}

// Quick Capture - Save Raw
async function handleSaveRawNote() {
  const textarea = document.getElementById('capture-textarea');
  const categorySelect = document.getElementById('capture-category');

  const text = textarea.value.trim();
  if (!text) return;

  const lines = text.split('\n').filter(l => l.trim().length > 0);
  const title = lines[0].slice(0, 40) || "Quick Thought";

  const newNote = {
    title: title,
    category: categorySelect.value || "Idea",
    summary: text,
    keyPoints: lines.slice(1, 5),
    actionItems: [],
    transcript: text,
    createdAt: Date.now()
  };

  const user = getCurrentUser();
  await saveNote(newNote, user);
  textarea.value = '';
}

// Render Notes Grid
function renderNotesGrid() {
  const grid = document.getElementById('notes-grid');
  const countEl = document.getElementById('notes-count');
  if (!grid) return;

  let filtered = allNotes.filter(note => {
    // Category match
    if (selectedCategory !== 'all' && (note.category || '').toLowerCase() !== selectedCategory.toLowerCase()) {
      return false;
    }
    // Search match
    if (searchQuery) {
      const matchTitle = (note.title || '').toLowerCase().includes(searchQuery);
      const matchSummary = (note.summary || '').toLowerCase().includes(searchQuery);
      const matchPoints = (note.keyPoints || []).some(p => p.toLowerCase().includes(searchQuery));
      return matchTitle || matchSummary || matchPoints;
    }
    return true;
  });

  // Sort: pinned first, then newest
  filtered.sort((a, b) => {
    if (a.isPinned && !b.isPinned) return -1;
    if (!a.isPinned && b.isPinned) return 1;
    return (b.createdAt || 0) - (a.createdAt || 0);
  });

  if (countEl) countEl.innerText = `${filtered.length} notes`;

  if (filtered.length === 0) {
    grid.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">✦</div>
        <p style="font-size: 15px; font-weight: 600; color: var(--text-primary); margin-bottom: 6px;">No notes found</p>
        <p style="font-size: 13px;">Type your thoughts in the box above or refine an idea with Gemini.</p>
      </div>
    `;
    return;
  }

  grid.innerHTML = filtered.map(note => {
    const dateFormatted = new Date(note.createdAt || Date.now()).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric'
    });

    const keyPoints = Array.isArray(note.keyPoints) ? note.keyPoints.slice(0, 2) : [];
    const pointsPreview = keyPoints.map(p => `
      <li><span class="dot">•</span><span>${escapeHtml(p)}</span></li>
    `).join('');

    const actionItems = Array.isArray(note.actionItems) ? note.actionItems.slice(0, 2) : [];
    const completedList = Array.isArray(note.completedActions) ? note.completedActions : [];
    const tasksPreview = actionItems.map((task, idx) => {
      const isDone = completedList.includes(task);
      return `
        <div class="card-task-row ${isDone ? 'completed' : ''}" data-action="toggle-task" data-note-id="${note.id}" data-task-idx="${idx}">
          <div class="task-checkbox">${isDone ? '✓' : ''}</div>
          <span class="task-text">${escapeHtml(task)}</span>
        </div>
      `;
    }).join('');

    return `
      <div class="note-card" data-id="${note.id}">
        <div class="note-card-header">
          <span class="category-badge">${escapeHtml(note.category || 'Idea')}</span>
          <span class="note-date">${dateFormatted}</span>
        </div>

        <h3 class="note-card-title">${escapeHtml(note.title || 'Untitled Thought')}</h3>
        <p class="note-card-summary">${escapeHtml(note.summary || '')}</p>

        ${pointsPreview ? `<ul class="note-bullets-preview">${pointsPreview}</ul>` : ''}
        ${tasksPreview ? `<div class="card-tasks-container" style="margin-bottom: 14px; display: flex; flex-direction: column; gap: 4px;">${tasksPreview}</div>` : ''}

        <div class="note-card-footer">
          <span style="font-size: 11px; color: var(--text-muted);">✦ ThoughtDrop</span>
          <div class="card-actions">
            <button class="icon-btn" title="Share" data-action="share" data-id="${note.id}">🔗</button>
            <button class="icon-btn pdf-btn" title="Export PDF" data-action="pdf" data-id="${note.id}">🖨️</button>
            <button class="icon-btn ${note.isPinned ? 'pin-active' : ''}" title="Pin Note" data-action="pin" data-id="${note.id}">📌</button>
            <button class="icon-btn" title="Delete Note" data-action="delete" data-id="${note.id}">🗑️</button>
          </div>
        </div>
      </div>
    `;
  }).join('');

  // Attach card click handlers
  grid.querySelectorAll('.note-card').forEach(card => {
    card.addEventListener('click', (e) => {
      const action = e.target.closest('[data-action]')?.dataset.action;
      const noteId = card.dataset.id;
      const note = allNotes.find(n => n.id === noteId);
      if (!note) return;

      if (action === 'toggle-task') {
        e.stopPropagation();
        const taskRow = e.target.closest('[data-task-idx]');
        if (taskRow) {
          const taskIdx = parseInt(taskRow.dataset.taskIdx, 10);
          toggleTaskCompletion(noteId, taskIdx);
        }
      } else if (action === 'pdf') {
        e.stopPropagation();
        exportNotePdf(note);
      } else if (action === 'share') {
        e.stopPropagation();
        openShareModal(note);
      } else if (action === 'pin') {
        e.stopPropagation();
        togglePinNote(noteId, getCurrentUser());
      } else if (action === 'delete') {
        e.stopPropagation();
        if (confirm("Delete this note permanently?")) {
          deleteNote(noteId, getCurrentUser());
        }
      } else {
        openDetailModal(note);
      }
    });
  });
}

// Open Detail Modal (Clean Optical Glass Reader)
function openDetailModal(note) {
  currentEditingNote = note;
  populateDetailView(note);
  switchDetailMode('view');

  const modal = document.getElementById('detail-modal');
  modal?.classList.add('active');
}

function populateDetailView(note) {
  const dateFormatted = new Date(note.createdAt || Date.now()).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  });

  const badge = document.getElementById('detail-view-badge');
  if (badge) badge.innerText = (note.category || 'Idea').toUpperCase();

  const dateEl = document.getElementById('detail-view-date');
  if (dateEl) dateEl.innerText = dateFormatted;

  const titleEl = document.getElementById('detail-view-title');
  if (titleEl) titleEl.innerText = note.title || 'Untitled Thought';

  const summaryEl = document.getElementById('detail-view-summary');
  if (summaryEl) summaryEl.innerText = note.summary || 'No summary available.';

  // Key Points
  const keyPointsContainer = document.getElementById('detail-section-keypoints');
  const keyPointsList = document.getElementById('detail-view-keypoints');
  const keyPoints = Array.isArray(note.keyPoints) ? note.keyPoints : [];
  if (keyPoints.length > 0 && keyPointsList) {
    keyPointsList.innerHTML = keyPoints.map(p => `
      <li><span class="bullet-dot">•</span><span>${escapeHtml(p)}</span></li>
    `).join('');
    keyPointsContainer.style.display = 'block';
  } else if (keyPointsContainer) {
    keyPointsContainer.style.display = 'none';
  }

  // Action Items
  const actionsContainer = document.getElementById('detail-section-actions');
  const actionsList = document.getElementById('detail-view-actions');
  const actionItems = Array.isArray(note.actionItems) ? note.actionItems : [];
  if (actionItems.length > 0 && actionsList) {
    const completedList = Array.isArray(note.completedActions) ? note.completedActions : [];
    actionsList.innerHTML = actionItems.map((a, idx) => {
      const isDone = completedList.includes(a);
      return `
        <div class="task-item-row ${isDone ? 'completed' : ''}" data-task-idx="${idx}" onclick="window.toggleTaskCompletion('${note.id}', ${idx})">
          <div class="task-checkbox">${isDone ? '✓' : ''}</div>
          <span class="task-item-text">${escapeHtml(a)}</span>
        </div>
      `;
    }).join('');
    actionsContainer.style.display = 'block';
  } else if (actionsContainer) {
    actionsContainer.style.display = 'none';
  }

  // Transcript / Raw notes
  const transcriptContainer = document.getElementById('detail-section-transcript');
  const transcriptEl = document.getElementById('detail-view-transcript');
  if (note.transcript && transcriptEl) {
    transcriptEl.innerText = note.transcript;
    transcriptContainer.style.display = 'block';
  } else if (transcriptContainer) {
    transcriptContainer.style.display = 'none';
  }
}

function switchDetailMode(mode) {
  const viewContainer = document.getElementById('detail-view-container');
  const editContainer = document.getElementById('detail-edit-container');

  if (mode === 'edit') {
    viewContainer.style.display = 'none';
    editContainer.style.display = 'block';
    const textarea = document.getElementById('detail-edit-textarea');
    // Pre-fill with note content (transcript or summary)
    if (textarea && currentEditingNote) {
      textarea.value = currentEditingNote.transcript || currentEditingNote.summary || '';
      textarea.focus();
    }
  } else {
    viewContainer.style.display = 'block';
    editContainer.style.display = 'none';
  }
}

function closeDetailModal() {
  document.getElementById('detail-modal')?.classList.remove('active');
  currentEditingNote = null;
}

// Interactive Task Completion Toggle
export async function toggleTaskCompletion(noteId, taskIndex) {
  const note = allNotes.find(n => n.id === noteId);
  if (!note || !Array.isArray(note.actionItems)) return;
  const taskText = note.actionItems[taskIndex];
  if (!taskText) return;

  if (!Array.isArray(note.completedActions)) {
    note.completedActions = [];
  }

  const existingIdx = note.completedActions.indexOf(taskText);
  if (existingIdx >= 0) {
    note.completedActions.splice(existingIdx, 1);
  } else {
    note.completedActions.push(taskText);
  }

  // Optimistic UI refresh for immediate tactile feedback
  renderNotesGrid();
  if (currentEditingNote && currentEditingNote.id === noteId) {
    currentEditingNote = note;
    populateDetailView(note);
  }

  try {
    const user = getCurrentUser();
    await saveNote(note, user);
  } catch (err) {
    console.error("Failed to sync task completion:", err);
  }
}
window.toggleTaskCompletion = toggleTaskCompletion;

// In-Modal AI Refine
async function handleRefineInModal() {
  if (!currentEditingNote) return;

  const textarea = document.getElementById('detail-edit-textarea');
  const text = textarea?.value.trim();
  if (!text) {
    alert("Please write something to refine.");
    return;
  }

  const btn = document.getElementById('detail-refine-btn');
  const origText = btn.innerHTML;
  btn.disabled = true;
  btn.innerHTML = `<span class="spinner"></span> Refining with Gemini...`;

  try {
    const refined = await refineTextWithAI(text, currentEditingNote.category || 'Idea');
    const updatedNote = {
      ...currentEditingNote,
      ...refined,
      transcript: text,
      updatedAt: Date.now()
    };

    await saveNote(updatedNote, getCurrentUser());
    currentEditingNote = updatedNote;
    populateDetailView(updatedNote);
    switchDetailMode('view');

    btn.innerHTML = origText;
    btn.disabled = false;
  } catch (err) {
    alert("Refine Error: " + err.message);
    btn.disabled = false;
    btn.innerHTML = origText;
  }
}

// In-Modal Save Raw Content
async function handleSaveRawInModal() {
  if (!currentEditingNote) return;

  const textarea = document.getElementById('detail-edit-textarea');
  const text = textarea?.value.trim();
  if (!text) return;

  const updatedNote = {
    ...currentEditingNote,
    transcript: text,
    summary: text.slice(0, 200),
    updatedAt: Date.now()
  };

  await saveNote(updatedNote, getCurrentUser());
  currentEditingNote = updatedNote;
  populateDetailView(updatedNote);
  switchDetailMode('view');
}

// ==========================================================================
// YOUTUBE-STYLE SHARE MODAL CONTROLLER
// ==========================================================================

function openShareModal(note) {
  currentSharingNote = note;
  const modal = document.getElementById('share-modal');
  if (!modal) return;

  document.getElementById('share-preview-badge').innerText = (note.category || 'Idea').toUpperCase();
  document.getElementById('share-preview-title').innerText = note.title || 'Untitled Thought';
  document.getElementById('share-preview-summary').innerText = note.summary || '';

  // Generate shareable link
  const shareUrl = `${window.location.origin}${window.location.pathname}#note=${encodeURIComponent(note.id)}`;
  const input = document.getElementById('share-link-input');
  if (input) input.value = shareUrl;

  const copyBtn = document.getElementById('copy-link-btn');
  if (copyBtn) copyBtn.innerText = 'Copy';

  modal.classList.add('active');
}

function closeShareModal() {
  document.getElementById('share-modal')?.classList.remove('active');
  currentSharingNote = null;
}

async function handleCopyShareLink() {
  const input = document.getElementById('share-link-input');
  const btn = document.getElementById('copy-link-btn');
  if (!input) return;

  try {
    await navigator.clipboard.writeText(input.value);
    if (btn) btn.innerText = '✓ Copied!';
    setTimeout(() => {
      if (btn) btn.innerText = 'Copy';
    }, 2000);
  } catch (err) {
    input.select();
    document.execCommand('copy');
    if (btn) btn.innerText = '✓ Copied!';
  }
}

async function handleCopyFullNoteText() {
  if (!currentSharingNote) return;

  const keyPointsText = (currentSharingNote.keyPoints || []).map(p => `• ${p}`).join('\n');
  const actionItemsText = (currentSharingNote.actionItems || []).map(a => `• ${a}`).join('\n');

  const fullText = `
💡 ${currentSharingNote.title} [Tag: #${currentSharingNote.category}]

Summary:
${currentSharingNote.summary}

${keyPointsText ? `✦ Key Insights:\n${keyPointsText}\n` : ''}
${actionItemsText ? `✦ Next Steps:\n${actionItemsText}\n` : ''}
${currentSharingNote.transcript ? `🎙 Notes:\n${currentSharingNote.transcript}\n` : ''}
— Shared via ThoughtDrop
  `.trim();

  try {
    await navigator.clipboard.writeText(fullText);
    const btn = document.getElementById('share-copy-text-btn');
    const orig = btn.innerHTML;
    btn.innerHTML = `<span class="share-icon">✓</span><span>Copied!</span>`;
    setTimeout(() => { btn.innerHTML = orig; }, 1800);
  } catch (e) {
    alert("Could not copy text to clipboard.");
  }
}

function handleShareWhatsApp() {
  if (!currentSharingNote) return;
  const text = encodeURIComponent(`*${currentSharingNote.title}*\n\n${currentSharingNote.summary}\n\n— Shared via ThoughtDrop`);
  window.open(`https://api.whatsapp.com/send?text=${text}`, '_blank');
}

function handleShareEmail() {
  if (!currentSharingNote) return;
  const subject = encodeURIComponent(`ThoughtDrop: ${currentSharingNote.title}`);
  const body = encodeURIComponent(`${currentSharingNote.title}\n\n${currentSharingNote.summary}\n\n${(currentSharingNote.keyPoints || []).map(p => `• ${p}`).join('\n')}`);
  window.location.href = `mailto:?subject=${subject}&body=${body}`;
}

function handleShareTwitter() {
  if (!currentSharingNote) return;
  const text = encodeURIComponent(`💡 "${currentSharingNote.title}"\n\n${currentSharingNote.summary.slice(0, 150)}...\n\n#ThoughtDrop`);
  window.open(`https://twitter.com/intent/tweet?text=${text}`, '_blank');
}

// ==========================================================================
// SETTINGS MODAL CONTROLLER & MULTI-KEY POOL MANAGEMENT
// ==========================================================================

function maskKey(key) {
  if (!key) return '••••••••';
  const trimmed = key.trim();
  if (trimmed.length <= 10) return '••••••••';
  return `${trimmed.slice(0, 6)}...${trimmed.slice(-4)}`;
}

export function openSettingsModal() {
  const modal = document.getElementById('settings-modal');
  if (!modal) return;

  renderSettingsModalUI();
  modal.classList.add('active');
}

export function closeSettingsModal() {
  document.getElementById('settings-modal')?.classList.remove('active');
}

function renderSettingsModalUI() {
  // 1. Cloud Sync Status
  const user = getCurrentUser();
  const statusText = document.getElementById('settings-cloud-status-text');
  if (statusText) {
    if (user) {
      statusText.textContent = `Connected as ${user.displayName || user.email}`;
    } else {
      statusText.textContent = 'Cloud Sync Connected (Ready to Sign In)';
    }
  }

  // 2. AI Engine Routing
  const currentEngine = getSelectedEngine();
  setEngineSelection(currentEngine, false);

  // 3. Model Selector
  const currentModel = getSelectedModel();
  const modelSelect = document.getElementById('gemini-model-select');
  if (modelSelect) modelSelect.value = currentModel;

  // 4. Gemini Key Pool
  renderGeminiPool();

  // 5. Groq Key Pool
  renderGroqPool();
}

function renderGeminiPool() {
  const container = document.getElementById('gemini-pool-container');
  const counter = document.getElementById('gemini-pool-counter');
  if (!container) return;

  const pool = getGeminiKeys();
  const readyCount = pool.filter(k => !k.isExhausted).length;
  if (counter) counter.textContent = `${readyCount} of ${pool.length} ready`;

  if (pool.length === 0) {
    container.innerHTML = `
      <div style="font-size: 11.5px; color: var(--text-muted); padding: 8px 0;">
        No keys in pool yet. Sign in with Google (top-right) to auto-sync from phone, or add below.
      </div>
    `;
    return;
  }

  container.innerHTML = pool.map(item => `
    <div class="key-pool-pill">
      <div>
        <span style="font-weight: 600; color: #fff;">${escapeHtml(item.label || 'Gemini Key')}</span>
        <span style="font-family: var(--font-mono); color: var(--text-muted); margin-left: 6px;">${maskKey(item.key)}</span>
      </div>
      <div style="display: flex; align-items: center; gap: 8px;">
        <span style="font-size: 10.5px; padding: 2px 7px; border-radius: 10px; ${item.isExhausted ? 'background: rgba(239, 68, 68, 0.15); color: #f87171;' : 'background: rgba(16, 185, 129, 0.15); color: #34d399;'}">
          ${item.isExhausted ? 'Quota Exhausted' : 'Ready'}
        </span>
        <button class="btn-glass" onclick="removeGeminiKey('${item.id || item.key}')" style="padding: 2px 6px; font-size: 11px; color: #f87171;" title="Remove Key">&times;</button>
      </div>
    </div>
  `).join('');
}

function renderGroqPool() {
  const container = document.getElementById('groq-pool-container');
  const counter = document.getElementById('groq-pool-counter');
  if (!container) return;

  const pool = getGroqKeys();
  const readyCount = pool.filter(k => !k.isExhausted).length;
  if (counter) counter.textContent = `${readyCount} of ${pool.length} ready`;

  if (pool.length === 0) {
    container.innerHTML = `
      <div style="font-size: 11.5px; color: var(--text-muted); padding: 8px 0;">
        No Groq keys in pool. Add an optional Groq key below for instant quota failover.
      </div>
    `;
    return;
  }

  container.innerHTML = pool.map(item => `
    <div class="key-pool-pill">
      <div>
        <span style="font-weight: 600; color: #fff;">${escapeHtml(item.label || 'Groq Key')}</span>
        <span style="font-family: var(--font-mono); color: var(--text-muted); margin-left: 6px;">${maskKey(item.key)}</span>
      </div>
      <div style="display: flex; align-items: center; gap: 8px;">
        <span style="font-size: 10.5px; padding: 2px 7px; border-radius: 10px; ${item.isExhausted ? 'background: rgba(239, 68, 68, 0.15); color: #f87171;' : 'background: rgba(245, 158, 11, 0.15); color: #fbbf24;'}">
          ${item.isExhausted ? 'Quota Exhausted' : 'Ready'}
        </span>
        <button class="btn-glass" onclick="removeGroqKey('${item.id || item.key}')" style="padding: 2px 6px; font-size: 11px; color: #f87171;" title="Remove Key">&times;</button>
      </div>
    </div>
  `).join('');
}

export function setEngineSelection(engine, saveToStorage = true) {
  ['auto', 'gemini', 'groq'].forEach(e => {
    const card = document.getElementById(`engine-card-${e}`);
    const radio = document.getElementById(`engine-radio-${e}`);
    if (card && radio) {
      if (e === engine) {
        card.classList.add('selected');
        radio.checked = true;
      } else {
        card.classList.remove('selected');
        radio.checked = false;
      }
    }
  });
  if (saveToStorage) {
    localStorage.setItem('thoughtdrop_selected_engine', engine);
  }
}

export function saveSettings() {
  const modelSelect = document.getElementById('gemini-model-select');
  if (modelSelect) {
    localStorage.setItem('thoughtdrop_selected_model', modelSelect.value);
  }

  closeSettingsModal();
}

window.addGeminiKeyFromInput = () => {
  const input = document.getElementById('add-gemini-key-input');
  if (!input) return;
  const key = input.value.trim();
  if (!key) return;

  const pool = getGeminiKeys();
  if (!pool.some(k => k.key === key)) {
    pool.push({
      id: 'gemini_' + Date.now(),
      key: key,
      label: `Gemini Account (${pool.length + 1})`,
      isExhausted: false
    });
    localStorage.setItem('thoughtdrop_gemini_pool', JSON.stringify(pool));
    localStorage.setItem('thoughtdrop_gemini_key', key);
  }
  input.value = '';
  renderGeminiPool();
};

window.addGroqKeyFromInput = () => {
  const input = document.getElementById('add-groq-key-input');
  if (!input) return;
  const key = input.value.trim();
  if (!key) return;

  const pool = getGroqKeys();
  if (!pool.some(k => k.key === key)) {
    pool.push({
      id: 'groq_' + Date.now(),
      key: key,
      label: `Groq Account (${pool.length + 1})`,
      isExhausted: false
    });
    localStorage.setItem('thoughtdrop_groq_pool', JSON.stringify(pool));
    localStorage.setItem('thoughtdrop_groq_key', key);
  }
  input.value = '';
  renderGroqPool();
};

window.removeGeminiKey = (keyId) => {
  const pool = getGeminiKeys().filter(k => k.id !== keyId && k.key !== keyId);
  localStorage.setItem('thoughtdrop_gemini_pool', JSON.stringify(pool));
  if (pool.length > 0) {
    localStorage.setItem('thoughtdrop_gemini_key', pool[0].key);
  } else {
    localStorage.removeItem('thoughtdrop_gemini_key');
  }
  renderGeminiPool();
};

window.removeGroqKey = (keyId) => {
  const pool = getGroqKeys().filter(k => k.id !== keyId && k.key !== keyId);
  localStorage.setItem('thoughtdrop_groq_pool', JSON.stringify(pool));
  if (pool.length > 0) {
    localStorage.setItem('thoughtdrop_groq_key', pool[0].key);
  } else {
    localStorage.removeItem('thoughtdrop_groq_key');
  }
  renderGroqPool();
};

// Global hooks for HTML inline listeners
window.openSettingsModal = openSettingsModal;
window.closeSettingsModal = closeSettingsModal;
window.saveSettings = saveSettings;
window.closeAllModals = closeAllModals;
window.setEngineSelection = setEngineSelection;

// Setup add key button listeners
document.addEventListener('DOMContentLoaded', () => {
  document.getElementById('add-gemini-key-btn')?.addEventListener('click', window.addGeminiKeyFromInput);
  document.getElementById('add-groq-key-btn')?.addEventListener('click', window.addGroqKeyFromInput);
  document.getElementById('save-settings-btn')?.addEventListener('click', saveSettings);
});

// Listener when phone app pushes fresh API keys to Firestore
window.onAiKeysSynced = (data) => {
  renderSettingsModalUI();
};

function escapeHtml(text) {
  if (!text) return '';
  return String(text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}
