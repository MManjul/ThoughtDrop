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

let auth = null;
let currentUser = null;
const listeners = [];

export function initAuth() {
  const fbConfigStr = localStorage.getItem('thoughtdrop_firebase_config');
  if (fbConfigStr) {
    try {
      const config = JSON.parse(fbConfigStr);
      if (config.apiKey && config.projectId) {
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
      }
    } catch (e) {
      console.warn("Invalid Firebase config in localStorage, falling back to Local Preview mode:", e);
    }
  }

  // Fallback to Local Preview Mode
  const localSavedUser = localStorage.getItem('thoughtdrop_local_user');
  if (localSavedUser) {
    currentUser = JSON.parse(localSavedUser);
  } else {
    // Default local user for instant testing
    currentUser = {
      uid: 'local_mayank',
      displayName: 'Mayank Manjul',
      email: 'local@thoughtdrop.dev',
      photoURL: 'https://ui-avatars.com/api/?name=Mayank+Manjul&background=ff3366&color=fff',
      isCloud: false
    };
  }
  notifyListeners();
}

export async function loginWithGoogle() {
  if (auth) {
    const provider = new GoogleAuthProvider();
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
  } else {
    // In local mode, simulate sign-in
    currentUser = {
      uid: 'local_mayank',
      displayName: 'Mayank Manjul',
      email: 'mayank@local.dev',
      photoURL: 'https://ui-avatars.com/api/?name=Mayank+Manjul&background=ff3366&color=fff',
      isCloud: false
    };
    localStorage.setItem('thoughtdrop_local_user', JSON.stringify(currentUser));
    notifyListeners();
    return currentUser;
  }
}

export async function logout() {
  if (auth && currentUser?.isCloud) {
    await fbSignOut(auth);
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
