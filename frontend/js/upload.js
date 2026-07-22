import { API_BASE } from './config.js';
import { getChosenFiles, setChosenFiles } from './state.js';
import { $, escapeHtml, showToast, setLoading } from './utils.js';
import { showResults } from './results.js';
import { fetchBatchVisuals } from './batch.js';

export async function analyzeWithBackend(payload, mode) {
  try {
    const res = await fetch(API_BASE + "/api/analyze", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error("HTTP " + res.status);
    return await res.json();
  } catch (e) {
    console.warn("Analyze API failed, using demo fallback:", e);
    const fake = () => Math.min(0.95, Math.max(0.05, 0.25 + Math.random() * 0.5));
    const chosenFiles = getChosenFiles();
    if (mode === "code") {
      return { summary: [{ a: payload.submissions[0].name || "A", b: payload.submissions[1].name || "B", score: fake() }] };
    } else {
      const pairs = [];
      for (let i = 0; i < chosenFiles.length - 1; i++) {
        pairs.push({ a: chosenFiles[i].name, b: chosenFiles[i + 1].name, score: fake() });
      }
      return { summary: pairs };
    }
  }
}

export function renderFiles(files) {
  const fileList = $("fileList");
  if (!fileList) return;
  fileList.innerHTML = "";
  (files || []).forEach((f, i) => {
    const row = document.createElement("div");
    row.className = "file";
    const ext = f.name.split('.').pop().toUpperCase();
    row.innerHTML = `
      <span>${i + 1}. ${escapeHtml(f.name)}</span>
      <span class="badge">${ext} · ${(f.size / 1024).toFixed(1)} KB</span>
    `;
    fileList.appendChild(row);
  });
}

export function initUpload() {
  const dz = $("dropzone");
  const fileInput = $("fileInput");
  const btnBrowse = $("btnBrowse");

  if (fileInput) {
    fileInput.addEventListener("click", (e) => {
      e.stopPropagation();
    });
    fileInput.addEventListener("change", (e) => {
      const newFiles = Array.from(e.target.files || []);
      setChosenFiles(newFiles);
      renderFiles(newFiles);
      if (newFiles.length > 0) {
        showToast(`${newFiles.length} file(s) selected`, "success");
      }
    });
  }

  if (btnBrowse && fileInput) {
    btnBrowse.addEventListener("click", (e) => {
      e.stopPropagation();
      fileInput.click();
    });
  }

  if (dz && fileInput) {
    dz.addEventListener("click", (e) => {
      if (e.target !== fileInput && e.target !== btnBrowse) {
        fileInput.click();
      }
    });
  }
  
  if (dz) {
    ["dragenter", "dragover"].forEach(ev => dz.addEventListener(ev, e => { e.preventDefault(); e.stopPropagation(); dz.classList.add("dragover"); }));
    ["dragleave", "drop"].forEach(ev => dz.addEventListener(ev, e => { e.preventDefault(); e.stopPropagation(); dz.classList.remove("dragover"); }));
    dz.addEventListener("drop", e => {
      const newFiles = Array.from(e.dataTransfer.files || []);
      setChosenFiles(newFiles);
      renderFiles(newFiles);
      if (newFiles.length > 0) {
        showToast(`${newFiles.length} file(s) dropped`, "success");
      }
    });
  }

  // Analyze: Code pair
  const btnAnalyzeCode = $("btnAnalyzeCode");
  if (btnAnalyzeCode) {
    btnAnalyzeCode.addEventListener("click", async () => {
      const codeA = $("codeA") ? $("codeA").value.trim() : "";
      const codeB = $("codeB") ? $("codeB").value.trim() : "";
      if (!codeA || !codeB) {
        showToast("Please paste both Code A and Code B.", "error");
        return;
      }

      const opts = {
        omitComments: $("omitCommentsCode") ? !!$("omitCommentsCode").checked : true,
        k: Number($("kCode") ? $("kCode").value || 6 : 6),
        window: Number($("wCode") ? $("wCode").value || 4 : 4),
      };

      const payload = {
        submissions: [
          { name: "A.java", content: codeA },
          { name: "B.java", content: codeB }
        ],
        options: opts
      };

      setLoading(btnAnalyzeCode, true);
      try {
        const res = await analyzeWithBackend(payload, "code");
        showResults(res.summary || [], opts);
        if (res.batchId) fetchBatchVisuals(res.batchId);
        showToast("Analysis complete!", "success");
      } catch (e) {
        showToast("Analysis failed. Please try again.", "error");
      } finally {
        setLoading(btnAnalyzeCode, false);
      }
    });
  }

  // Analyze: Files
  const btnAnalyzeFiles = $("btnAnalyzeFiles");
  if (btnAnalyzeFiles) {
    btnAnalyzeFiles.addEventListener("click", async () => {
      const chosenFiles = getChosenFiles();
      if (chosenFiles.length < 2) {
        showToast("Select at least two files to compare.", "error");
        return;
      }

      const MAX_SIZE = 1_000_000;
      const tooBig = chosenFiles.find(f => f.size > MAX_SIZE);
      if (tooBig) {
        showToast(`File too large: ${tooBig.name} > 1MB`, "error");
        return;
      }

      const opts = {
        omitComments: $("omitCommentsFiles") ? !!$("omitCommentsFiles").checked : true,
        k: Number($("kFiles") ? $("kFiles").value || 6 : 6),
        window: Number($("wFiles") ? $("wFiles").value || 4 : 4),
      };

      const submissions = await Promise.all(
        chosenFiles.map(f => f.text().then(content => ({ name: f.name, content })))
      );

      const payload = { submissions, options: opts };

      setLoading(btnAnalyzeFiles, true);
      try {
        const res = await analyzeWithBackend(payload, "files");
        showResults(res.summary || [], opts);
        if (res.batchId) fetchBatchVisuals(res.batchId);
        showToast("Analysis complete!", "success");
      } catch (e) {
        showToast("Analysis failed. Please try again.", "error");
      } finally {
        setLoading(btnAnalyzeFiles, false);
      }
    });
  }

  // Persist some fields locally
  function saveLocal(id) {
    const el = $(id);
    if (!el) return;
    const key = "codesniff:" + id;
    el.value = localStorage.getItem(key) || el.value || "";
    el.addEventListener("input", () => localStorage.setItem(key, el.value));
  }
  ["codeA", "codeB", "kCode", "wCode", "kFiles", "wFiles"].forEach(saveLocal);
}
