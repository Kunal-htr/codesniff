import { API_BASE } from './config.js';

export function initBackendCheck() {
  const API_BASE_CHECK = typeof API_BASE !== 'undefined'
    ? API_BASE
    : "https://codesniff-backend.azurewebsites.net";

  // Create indicator HTML
  const indicator = document.createElement("div");
  indicator.id = "backend-indicator";
  indicator.innerHTML = `
    <div id="bi-pill">
      <span id="bi-dot"></span>
      <span id="bi-text">Connecting...</span>
      <span id="bi-timer"></span>
    </div>
  `;

  document.body.appendChild(indicator);

  // State manager
  const pill = indicator.querySelector("#bi-pill");
  const text = indicator.querySelector("#bi-text");
  const timer = indicator.querySelector("#bi-timer");

  function setState(state, message) {
    pill.className = "bi-" + state;
    text.textContent = message;
  }

  // Live timer
  let seconds = 0;
  let timerInterval = null;

  function startTimer() {
    seconds = 0;
    timerInterval = setInterval(() => {
      seconds++;
      const m = Math.floor(seconds / 60);
      const s = seconds % 60;
      timer.textContent = m > 0 ? `${m}m ${s}s` : `${s}s`;
    }, 1000);
  }

  function stopTimer() {
    clearInterval(timerInterval);
    timer.textContent = "";
  }

  // Auto-hide after success
  function hideAfterDelay(ms) {
    setTimeout(() => {
      indicator.classList.add("bi-fadeout");
      setTimeout(() => { indicator.style.display = "none"; }, 700);
    }, ms);
  }

  // Main check logic
  let attempt = 0;
  const MAX_ATTEMPTS = 10;
  const RETRY_DELAY = 6000;

  async function checkBackend() {
    attempt++;

    if (attempt === 1) {
      setState("connecting", "Connecting to server");
      startTimer();
    } else if (attempt <= 3) {
      setState("waking", "Server waking up");
    } else {
      setState("waking", "Still starting up");
    }

    try {
      const res = await fetch(API_BASE_CHECK + "/api/health", {
        signal: AbortSignal.timeout(8000)
      });

      if (res.ok) {
        stopTimer();
        setState("ready", "Server ready");
        hideAfterDelay(2500);
        return;
      }
      throw new Error("Not OK");

    } catch (e) {
      if (attempt < MAX_ATTEMPTS) {
        setTimeout(checkBackend, RETRY_DELAY);
      } else {
        stopTimer();
        setState("error", "Server unavailable");
        hideAfterDelay(5000);
      }
    }
  }

  checkBackend();
}
