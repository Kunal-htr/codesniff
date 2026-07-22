import { $, qsa } from './utils.js';

export function hideAllPagesInline() {
  qsa(".page").forEach(p => { p.classList.remove("active"); p.style.display = "none"; });
}

export function updateNavActive(name) {
  qsa(".nav-link").forEach(link => {
    const page = link.dataset.page;
    if (page === name) {
      link.classList.add("active");
    } else {
      link.classList.remove("active");
    }
  });
}

export function showPageInline(name) {
  const home = $("page-home");
  const upload = $("page-upload");
  const results = $("results");
  const longArticle = $("who-can-use");
  const hero = $("hero") || $("hero-section") || document.querySelector(".hero");

  hideAllPagesInline();
  if (results) results.classList.add("hidden");

  if (name === "upload") {
    if (upload) { upload.style.display = "block"; upload.classList.add("active"); }
    if (longArticle) longArticle.style.display = "none";
    if (hero) hero.style.display = "none";
  } else if (["login", "signup", "forgot-password", "reset-password", "verify-email", "dashboard", "profile"].includes(name)) {
    const pageEl = $("page-" + name);
    if (pageEl) { pageEl.style.display = "block"; pageEl.classList.add("active"); }
    if (longArticle) longArticle.style.display = "none";
    if (hero) hero.style.display = "none";
  } else {
    if (home) { home.style.display = "block"; home.classList.add("active"); }
    if (longArticle) longArticle.style.display = "block";
    if (hero) hero.style.display = "";
  }

  const footer = document.querySelector(".site-footer") || document.querySelector(".footer");
  if (footer) footer.style.display = "";

  updateNavActive(name);
}

export function routeFromHash() {
  const raw = (location.hash || "").split("?")[0].replace("#/", "").trim();
  if (["upload", "login", "signup", "forgot-password", "reset-password", "verify-email", "dashboard", "profile"].includes(raw)) {
      return raw;
  }
  return "home";
}

export function initScrollEffects() {
  const topbar = $("topbar");
  if (!topbar) return;
  let ticking = false;
  window.addEventListener("scroll", () => {
    if (!ticking) {
      requestAnimationFrame(() => {
        if (window.scrollY > 10) {
          topbar.classList.add("scrolled");
        } else {
          topbar.classList.remove("scrolled");
        }
        ticking = false;
      });
      ticking = true;
    }
  });
}

export function initScrollAnimations() {
  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.style.animationPlayState = "running";
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.1 });

  qsa(".card, .info-card").forEach(el => {
    el.style.animationPlayState = "paused";
    observer.observe(el);
  });
}
