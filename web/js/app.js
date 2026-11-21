/**
 * ThoughtDrop Web Studio - Main Application Controller
 */

import { initAuth, loginWithGoogle, logout, getCurrentUser, onUserChanged } from './auth.js';
import { initDb, saveNote, deleteNote, togglePinNote, onNotesChanged } from './db.js';
import { refineTextWithAI } from './ai.js';
import { exportNotePdf } from './pdf.js';

let allNotes = [];
let selectedCategory = 'all';
let searchQuery = '';
let currentEditingNote = null;
let currentSharingNote = null;

// Initialize on DOM Ready
document.addEventListener('DOMContentLoaded', () => {
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
});

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

    return `
      <div class="note-card" data-id="${note.id}">
        <div class="note-card-header">
          <span class="category-badge">${escapeHtml(note.category || 'Idea')}</span>
          <span class="note-date">${dateFormatted}</span>
        </div>

        <h3 class="note-card-title">${escapeHtml(note.title || 'Untitled Thought')}</h3>
        <p class="note-card-summary">${escapeHtml(note.summary || '')}</p>

        ${pointsPreview ? `<ul class="note-bullets-preview">${pointsPreview}</ul>` : ''}

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

      if (action === 'pdf') {
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
    actionsList.innerHTML = actionItems.map(a => `
      <li><span class="bullet-dot">•</span><span>${escapeHtml(a)}</span></li>
    `).join('');
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
// SETTINGS MODAL CONTROLLER
// ==========================================================================

function openSettingsModal() {
  const modal = document.getElementById('settings-modal');
  if (!modal) return;

  document.getElementById('gemini-key-input').value = localStorage.getItem('thoughtdrop_gemini_key') || '';
  document.getElementById('firebase-config-input').value = localStorage.getItem('thoughtdrop_firebase_config') || '';

  modal.classList.add('active');
}

function closeSettingsModal() {
  document.getElementById('settings-modal')?.classList.remove('active');
}

function saveSettings() {
  const geminiKey = document.getElementById('gemini-key-input').value.trim();
  const fbConfig = document.getElementById('firebase-config-input').value.trim();

  if (geminiKey) {
    localStorage.setItem('thoughtdrop_gemini_key', geminiKey);
  } else {
    localStorage.removeItem('thoughtdrop_gemini_key');
  }

  if (fbConfig) {
    try {
      JSON.parse(fbConfig); // validate JSON
      localStorage.setItem('thoughtdrop_firebase_config', fbConfig);
    } catch (e) {
      alert("Firebase config must be valid JSON.");
      return;
    }
  } else {
    localStorage.removeItem('thoughtdrop_firebase_config');
  }

  closeSettingsModal();
  alert("Settings saved successfully!");
  location.reload();
}

function escapeHtml(text) {
  if (!text) return '';
  return String(text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}
