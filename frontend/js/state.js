let currentUser = null;
let chosenFiles = [];

export function getCurrentUser() {
  return currentUser;
}

export function setCurrentUser(user) {
  currentUser = user;
}

export function getChosenFiles() {
  return chosenFiles;
}

export function setChosenFiles(files) {
  chosenFiles = files;
}

// Expose API for debugging (as in original)
window.codesniff = window.codesniff || {};
window.codesniff.getFiles = () => chosenFiles.slice();
