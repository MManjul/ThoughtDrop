/**
 * ThoughtDrop Web Studio - Database & Sync Module
 * Syncs notes with Cloud Firestore when logged in, or LocalStorage when offline/testing.
 */

import { initializeApp, getApps } from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-app.js';
import { 
  getFirestore, 
  collection, 
  doc, 
  setDoc, 
  deleteDoc, 
  onSnapshot, 
  query, 
  orderBy 
} from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js';

let db = null;
let unsubscribeFirestore = null;
let unsubscribeSettings = null;
const notesListeners = [];
let cachedNotes = [];

export async function initDb(user) {
  if (unsubscribeFirestore) {
    unsubscribeFirestore();
    unsubscribeFirestore = null;
  }
  if (unsubscribeSettings) {
    unsubscribeSettings();
    unsubscribeSettings = null;
  }

  let fbConfigStr = localStorage.getItem('thoughtdrop_firebase_config');
  if (!fbConfigStr) {
    try {
      const mod = await import('./firebase-config.js');
      if (mod.firebaseConfig) {
        fbConfigStr = JSON.stringify(mod.firebaseConfig);
        localStorage.setItem('thoughtdrop_firebase_config', fbConfigStr);
      }
    } catch (e) {}
  }

  if (fbConfigStr && user && user.isCloud) {
    try {
      const config = JSON.parse(fbConfigStr);
      const app = getApps().length === 0 ? initializeApp(config) : getApps()[0];
      db = getFirestore(app);

      // 1. Real-time Notes Listener
      const notesRef = collection(db, 'users', user.uid, 'notes');
      const q = query(notesRef, orderBy('createdAt', 'desc'));

      unsubscribeFirestore = onSnapshot(q, (snapshot) => {
        cachedNotes = snapshot.docs.map(doc => ({
          id: doc.id,
          ...doc.data()
        }));
        notifyNotesChanged();
      }, (err) => {
        console.error("Firestore listener error, falling back to local:", err);
        loadLocalNotes();
      });

      // 2. Real-time AI Keys Sync from Phone App
      const settingsRef = doc(db, 'users', user.uid, 'settings', 'ai_keys');
      unsubscribeSettings = onSnapshot(settingsRef, (docSnap) => {
        if (docSnap.exists()) {
          const data = docSnap.data();
          let changed = false;
          if (data.geminiApiKey) {
            localStorage.setItem('thoughtdrop_gemini_key', data.geminiApiKey);
            changed = true;
          }
          if (data.groqApiKey) {
            localStorage.setItem('thoughtdrop_groq_key', data.groqApiKey);
            changed = true;
          }
          if (Array.isArray(data.geminiPool) && data.geminiPool.length > 0) {
            localStorage.setItem('thoughtdrop_gemini_pool', JSON.stringify(data.geminiPool));
            changed = true;
          }
          if (Array.isArray(data.groqPool) && data.groqPool.length > 0) {
            localStorage.setItem('thoughtdrop_groq_pool', JSON.stringify(data.groqPool));
            changed = true;
          }
          if (data.selectedModel) {
            localStorage.setItem('thoughtdrop_selected_model', data.selectedModel);
            changed = true;
          }
          if (data.selectedEngine) {
            localStorage.setItem('thoughtdrop_selected_engine', data.selectedEngine);
            changed = true;
          }
          if (changed && typeof window.onAiKeysSynced === 'function') {
            window.onAiKeysSynced(data);
          }
        }
      }, (err) => {
        console.warn("Firestore settings sync notice:", err);
      });

      return;
    } catch (e) {
      console.warn("Firestore init failed:", e);
    }
  }

  // Load from local storage
  loadLocalNotes();
}

function loadLocalNotes() {
  const localStr = localStorage.getItem('thoughtdrop_local_notes');
  if (localStr) {
    try {
      cachedNotes = JSON.parse(localStr);
    } catch (e) {
      cachedNotes = [];
    }
  } else {
    // Seed initial demo note if completely empty
    cachedNotes = [
      {
        id: 'sample_note_1',
        title: 'ThoughtDrop Web Studio Architecture',
        category: 'Strategy',
        summary: 'Cloud-synced optical glass studio for ThoughtDrop notes, providing frictionless note-taking, AI refinement, and executive PDF memos.',
        keyPoints: [
          'Direct Google login linking Android device and PC browser',
          'Client-side BYOK Gemini AI integration for instant note structuring',
          'Publication-grade executive PDF export in signature Crimson HUD theme',
          'Deployable to Cloudflare Pages or GitHub Pages with zero build step'
        ],
        actionItems: [
          'Verify Gemini API key in Settings modal',
          'Test creating a new note and clicking Refine with AI',
          'Connect Firebase project credentials for live two-way sync'
        ],
        transcript: 'Testing the web companion studio for ThoughtDrop. Everything should feel fast, responsive, and private.',
        isPinned: true,
        createdAt: Date.now() - 3600000
      }
    ];
    saveLocalNotes();
  }
  notifyNotesChanged();
}

function saveLocalNotes() {
  localStorage.setItem('thoughtdrop_local_notes', JSON.stringify(cachedNotes));
}

export async function saveNote(note, user) {
  const noteId = note.id || 'note_' + Date.now();
  const noteData = {
    ...note,
    id: noteId,
    createdAt: note.createdAt || Date.now(),
    updatedAt: Date.now()
  };

  if (db && user && user.isCloud) {
    try {
      const docRef = doc(db, 'users', user.uid, 'notes', noteId);
      await setDoc(docRef, noteData, { merge: true });
      return noteData;
    } catch (err) {
      console.error("Firestore save failed, saving locally:", err);
    }
  }

  // Local save
  const existingIdx = cachedNotes.findIndex(n => n.id === noteId);
  if (existingIdx >= 0) {
    cachedNotes[existingIdx] = noteData;
  } else {
    cachedNotes.unshift(noteData);
  }
  saveLocalNotes();
  notifyNotesChanged();
  return noteData;
}

export async function deleteNote(noteId, user) {
  if (db && user && user.isCloud) {
    try {
      const docRef = doc(db, 'users', user.uid, 'notes', noteId);
      await deleteDoc(docRef);
    } catch (err) {
      console.error("Firestore delete failed:", err);
    }
  }

  cachedNotes = cachedNotes.filter(n => n.id !== noteId);
  saveLocalNotes();
  notifyNotesChanged();
}

export async function togglePinNote(noteId, user) {
  const note = cachedNotes.find(n => n.id === noteId);
  if (!note) return;

  const updated = { ...note, isPinned: !note.isPinned };
  await saveNote(updated, user);
}

export function onNotesChanged(callback) {
  notesListeners.push(callback);
  callback(cachedNotes);
}

function notifyNotesChanged() {
  notesListeners.forEach(cb => cb(cachedNotes));
}
