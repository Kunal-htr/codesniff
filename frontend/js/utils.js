export const $ = id => document.getElementById(id);
export const qsa = sel => Array.from(document.querySelectorAll(sel));

export const escapeHtml = s => String(s || "")
  .replace(/&/g, "&amp;")
  .replace(/</g, "&lt;")
  .replace(/>/g, "&gt;")
  .replace(/"/g, "&quot;");

export function showToast(message, type = "info", duration = 4000) {
  const container = $("toastContainer");
  if (!container) return;
  const toast = document.createElement("div");
  toast.className = `toast ${type}`;
  const icons = {
    error: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>`,
    success: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>`,
    info: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>`
  };
  toast.innerHTML = `${icons[type] || icons.info}<span>${escapeHtml(message)}</span>`;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = "0";
    toast.style.transform = "translateX(20px)";
    toast.style.transition = "all 0.3s ease";
    setTimeout(() => toast.remove(), 300);
  }, duration);
}

export function setLoading(btn, isLoading) {
  if (isLoading) {
    btn.dataset.originalText = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = `
      <div class="spinner" style="width:16px;height:16px;border-width:2px;margin:0;"></div>
      <span>Analyzing…</span>
    `;
  } else {
    btn.disabled = false;
    btn.innerHTML = btn.dataset.originalText || "Analyze";
  }
}

export const formatPct = x => (x * 100).toFixed(1) + "%";

export function getSeverity(score) {
  if (score < 0.3) return "low";
  if (score < 0.7) return "medium";
  return "high";
}
