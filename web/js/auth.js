/**
 * ThoughtDrop Web Studio - Authentication Module
 * Supports Google Sign-In via Firebase Auth, with seamless Local Preview fallback.
 */

import { initializeApp, getApps } from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-app.js';
import { 
  getAuth, 
  signInWithPopup, 
  GoogleAuthProvider, 
  signOut as fbSignOut, 
  onAuthStateChanged 
} from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-auth.js';

const DEFAULT_FIREBASE_CONFIG = {
  apiKey: "AIzaSyC5Iy3MvBYQyxLnOpnatl_tHFBntFYLFMs",
  authDomain: "thoughtdrop-4f22e.firebaseapp.com",
  projectId: "thoughtdrop-4f22e",
  storageBucket: "thoughtdrop-4f22e.firebasestorage.app",
  messagingSenderId: "599762645775",
  appId: "1:599762645775:web:ac57fbe0c002a5849a047a"
};

let auth = null;
let currentUser = null;
const listeners = [];

async function resolveFirebaseConfig() {
  const fbConfigStr = localStorage.getItem('thoughtdrop_firebase_config');
  if (fbConfigStr) {
    try { return JSON.parse(fbConfigStr); } catch (e) {}
  }
  try {
    const mod = await import('./firebase-config.js');
    if (mod.firebaseConfig) {
      localStorage.setItem('thoughtdrop_firebase_config', JSON.stringify(mod.firebaseConfig));
      return mod.firebaseConfig;
    }
  } catch (e) {}
  localStorage.setItem('thoughtdrop_firebase_config', JSON.stringify(DEFAULT_FIREBASE_CONFIG));
  return DEFAULT_FIREBASE_CONFIG;
}

export async function initAuth() {
  // Clear any legacy mock preview user so user isn't misled
  localStorage.removeItem('thoughtdrop_local_user');

  const config = await resolveFirebaseConfig();
  if (config && config.apiKey && config.projectId) {
    try {
      const app = getApps().length === 0 ? initializeApp(config) : getApps()[0];
      auth = getAuth(app);

      onAuthStateChanged(auth, (user) => {
        currentUser = user ? {
          uid: user.uid,
          displayName: user.displayName || 'Google User',
          email: user.email,
          photoURL: user.photoURL || 'https://www.gravatar.com/avatar?d=mp',
          isCloud: true
        } : null;
        notifyListeners();
      });
      return;
    } catch (e) {
      console.warn("Firebase init failed:", e);
    }
  }

  // Not signed in
  currentUser = null;
  notifyListeners();
}

export async function loginWithGoogle() {
  if (!auth) {
    const config = await resolveFirebaseConfig();
    const app = getApps().length === 0 ? initializeApp(config) : getApps()[0];
    auth = getAuth(app);
  }

  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: 'select_account' });
  try {
    const result = await signInWithPopup(auth, provider);
    currentUser = {
      uid: result.user.uid,
      displayName: result.user.displayName,
      email: result.user.email,
      photoURL: result.user.photoURL,
      isCloud: true
    };
    notifyListeners();
    return currentUser;
  } catch (error) {
    console.error("Google Sign-In failed:", error);
    throw error;
  }
}

export async function logout() {
  if (auth) {
    try {
      await fbSignOut(auth);
    } catch (e) {
      console.warn("Firebase sign out:", e);
    }
  }
  currentUser = null;
  localStorage.removeItem('thoughtdrop_local_user');
  notifyListeners();
}

export function getCurrentUser() {
  return currentUser;
}

export function onUserChanged(callback) {
  listeners.push(callback);
  callback(currentUser);
}

function notifyListeners() {
  listeners.forEach(cb => cb(currentUser));
}
