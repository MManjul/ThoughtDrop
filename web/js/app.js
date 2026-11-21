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
  });
});

function setupEventListeners() {
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

  // Note Detail Modal
  document.getElementById('close-detail-btn')?.addEventListener('click', closeDetailModal);
  document.getElementById('detail-pdf-btn')?.addEventListener('click', () => {
    if (currentEditingNote) exportNotePdf(currentEditingNote);
  });
  document.getElementById('detail-save-btn')?.addEventListener('click', handleSaveEditedNote);
  document.getElementById('detail-refine-btn')?.addEventListener('click', handleRefineInModal);
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
    // Show subtle feedback
    refineBtn.innerHTML = `✓ Refined & Saved!`;
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
            <button class="icon-btn pdf-btn" title="Export Crimson PDF" data-action="pdf" data-id="${note.id}">🖨️</button>
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

// Detail & Edit Modal
function openDetailModal(note) {
  currentEditingNote = note;
  const modal = document.getElementById('detail-modal');
  if (!modal) return;

  document.getElementById('detail-title-input').value = note.title || '';
  document.getElementById('detail-category-input').value = note.category || 'Idea';
  document.getElementById('detail-summary-input').value = note.summary || '';
  document.getElementById('detail-points-input').value = (note.keyPoints || []).join('\n');
  document.getElementById('detail-actions-input').value = (note.actionItems || []).join('\n');
  document.getElementById('detail-transcript-input').value = note.transcript || '';

  modal.classList.add('active');
}

function closeDetailModal() {
  document.getElementById('detail-modal')?.classList.remove('active');
  currentEditingNote = null;
}

async function handleSaveEditedNote() {
  if (!currentEditingNote) return;

  const title = document.getElementById('detail-title-input').value.trim();
  const category = document.getElementById('detail-category-input').value.trim();
  const summary = document.getElementById('detail-summary-input').value.trim();
  const pointsStr = document.getElementById('detail-points-input').value;
  const actionsStr = document.getElementById('detail-actions-input').value;
  const transcript = document.getElementById('detail-transcript-input').value;

  const keyPoints = pointsStr.split('\n').map(p => p.replace(/^[•\-\*]\s*/, '').trim()).filter(Boolean);
  const actionItems = actionsStr.split('\n').map(a => a.replace(/^[•\-\*]\s*/, '').trim()).filter(Boolean);

  const updatedNote = {
    ...currentEditingNote,
    title: title || "Untitled Thought",
    category: category || "Idea",
    summary,
    keyPoints,
    actionItems,
    transcript,
    updatedAt: Date.now()
  };

  await saveNote(updatedNote, getCurrentUser());
  closeDetailModal();
}

async function handleRefineInModal() {
  const summary = document.getElementById('detail-summary-input').value.trim();
  const transcript = document.getElementById('detail-transcript-input').value.trim();
  const category = document.getElementById('detail-category-input').value.trim();

  const sourceText = transcript || summary;
  if (!sourceText) {
    alert("Please write something in Summary or Transcript to refine.");
    return;
  }

  const btn = document.getElementById('detail-refine-btn');
  const origText = btn.innerHTML;
  btn.disabled = true;
  btn.innerHTML = `<span class="spinner"></span> Refining...`;

  try {
    const refined = await refineTextWithAI(sourceText, category);
    document.getElementById('detail-title-input').value = refined.title;
    document.getElementById('detail-category-input').value = refined.category;
    document.getElementById('detail-summary-input').value = refined.summary;
    document.getElementById('detail-points-input').value = (refined.keyPoints || []).join('\n');
    document.getElementById('detail-actions-input').value = (refined.actionItems || []).join('\n');

    btn.innerHTML = `✓ Refined!`;
    setTimeout(() => {
      btn.disabled = false;
      btn.innerHTML = origText;
    }, 1200);
  } catch (err) {
    alert("Refine Error: " + err.message);
    btn.disabled = false;
    btn.innerHTML = origText;
  }
}

// Settings Modal
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
