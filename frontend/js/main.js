import { $, qsa } from './utils.js';
import { checkAuthState, initAuth } from './auth.js';
import { showPageInline, routeFromHash, initScrollEffects, initScrollAnimations } from './router.js';
import { initUpload } from './upload.js';
import { initResults } from './results.js';
import { initDiffViewer } from './diffViewer.js';
import { initBackendCheck } from './health.js';
import { initDashboard, loadHistory, initDashboardStats } from './dashboard.js';

document.addEventListener("DOMContentLoaded", () => {
  checkAuthState();
  showPageInline(routeFromHash());
  initScrollEffects();
  initScrollAnimations();

  // Dropdown toggle
  const avatarBtn = $("avatarBtn");
  const avatarMenu = $("avatarDropdownMenu");
  if (avatarBtn && avatarMenu) {
      avatarBtn.addEventListener("click", (e) => {
          e.stopPropagation();
          avatarMenu.classList.toggle("hidden");
      });
      document.addEventListener("click", () => {
          avatarMenu.classList.add("hidden");
      });
  }

  // Profile Sidebar Tabs
  const profileTabs = document.querySelectorAll(".profile-nav-item");
  const profileSections = document.querySelectorAll(".profile-section");
  profileTabs.forEach(tab => {
      tab.addEventListener("click", () => {
          profileTabs.forEach(t => t.classList.remove("active"));
          profileSections.forEach(s => s.classList.add("hidden"));
          tab.classList.add("active");
          const targetId = tab.getAttribute("data-target");
          const targetSec = document.getElementById(targetId);
          if(targetSec) targetSec.classList.remove("hidden");
      });
  });

  // Password visibility toggles
  document.querySelectorAll(".pwd-toggle").forEach(btn => {
    btn.addEventListener("click", () => {
      const targetId = btn.getAttribute("data-target");
      const input = document.getElementById(targetId);
      if (input) {
        if (input.type === "password") {
          input.type = "text";
          btn.innerHTML = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line></svg>`;
        } else {
          input.type = "password";
          btn.innerHTML = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>`;
        }
      }
    });
  });

  // Tabs
  qsa(".tab").forEach(t => {
    t.addEventListener("click", () => {
      qsa(".tab").forEach(x => x.classList.remove("active"));
      t.classList.add("active");
      qsa(".tabpanel").forEach(p => p.classList.remove("active"));
      const panel = $(`tab-${t.dataset.tab}`);
      if (panel) panel.classList.add("active");
      const resultsEl = $("results");
      if (resultsEl) resultsEl.classList.add("hidden");
    });
  });

  // Get Started button
  const btnGetStarted = $("btnGetStarted");
  if (btnGetStarted) {
    btnGetStarted.addEventListener("click", (e) => {
      e.preventDefault();
      location.hash = "#/upload";
      showPageInline("upload");
      qsa(".tab").forEach(t => t.classList.remove("active"));
      const codeTab = Array.from(qsa(".tab")).find(t => t.dataset && t.dataset.tab === "code");
      if (codeTab) codeTab.classList.add("active");
      qsa(".tabpanel").forEach(p => p.classList.remove("active"));
      const codePanel = $("tab-code");
      if (codePanel) codePanel.classList.add("active");
      setTimeout(() => {
        const upload = $("page-upload");
        upload && upload.scrollIntoView({ behavior: "smooth", block: "start" });
        const firstInput = upload && upload.querySelector("textarea, input, button");
        if (firstInput) firstInput.focus();
      }, 120);
    });
  }

  initAuth();
  initUpload();
  initResults();
  initDiffViewer();
  initBackendCheck();
  initDashboard();

  // Track current route to prevent duplicate history loads
  let currentRoute = null;

  // Handle SPA routing on hash change
  window.addEventListener("hashchange", () => {
    const route = routeFromHash();
    showPageInline(route);
    if (route === "history" && currentRoute !== "history") {
      loadHistory();
    }
    if (route === "dashboard" && currentRoute !== "dashboard") {
      initDashboardStats();
    }
    currentRoute = route;
  });

  // Guard to ensure correct page on load
  const initialRoute = routeFromHash();
  const guard = setInterval(() => {
    const route = routeFromHash();
    showPageInline(route);
    if (route === "history" && currentRoute !== "history") {
      loadHistory();
      currentRoute = route;
    }
    if (route === "dashboard" && currentRoute !== "dashboard") {
      initDashboardStats();
      currentRoute = route;
    }
  }, 120);
  setTimeout(() => clearInterval(guard), 1800);
});
