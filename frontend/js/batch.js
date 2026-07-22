import { API_BASE } from './config.js';
import { $, formatPct, getSeverity } from './utils.js';
import { openDiffViewer } from './diffViewer.js';

export function fetchBatchVisuals(batchId) {
  fetch(API_BASE + "/api/batch/" + encodeURIComponent(batchId) + "/summary")
    .then(res => res.json())
    .then(data => renderBatchVisuals(data))
    .catch(err => console.error("Failed to fetch batch summary", err));
}

export function renderBatchVisuals(data) {
  const overview = $("batch-overview");
  const statsContainer = $("batchStatsCards");
  const heatmap = $("batchHeatmap");
  if (!overview || !statsContainer || !heatmap) return;

  overview.classList.remove("hidden");

  // 1. Render Stat Cards
  statsContainer.innerHTML = `
    <div class="stat-card">
      <div class="stat-card-title">Highest Similarity</div>
      <div class="stat-card-value high">${formatPct(data.highestSimilarity || 0)}</div>
    </div>
    <div class="stat-card">
      <div class="stat-card-title">Average Similarity</div>
      <div class="stat-card-value">${formatPct(data.averageSimilarity || 0)}</div>
    </div>
    <div class="stat-card">
      <div class="stat-card-title">Lowest Similarity</div>
      <div class="stat-card-value safe">${formatPct(data.lowestSimilarity || 0)}</div>
    </div>
    <div class="stat-card">
      <div class="stat-card-title">Suspicious Pairs</div>
      <div class="stat-card-value ${data.suspiciousPairCount > 0 ? 'high' : 'safe'}">${data.suspiciousPairCount || 0}</div>
    </div>
  `;

  // 2. Render Heatmap Grid
  heatmap.innerHTML = "";
  
  // Extract unique files and sort alphabetically
  const pairs = data.pairs || [];
  const uniqueSet = new Set();
  pairs.forEach(p => {
    uniqueSet.add(p.a);
    uniqueSet.add(p.b);
  });
  const files = Array.from(uniqueSet).sort();
  const N = files.length;
  
  // Fallback if no files exist
  if (N === 0) return;

  heatmap.style.gridTemplateColumns = `repeat(${N}, 24px)`;

  // Build lookup for fast pair retrieval
  const pairMap = new Map();
  pairs.forEach(p => {
    pairMap.set(`${p.a}|${p.b}`, p);
    pairMap.set(`${p.b}|${p.a}`, p);
  });

  for (let i = 0; i < N; i++) {
    for (let j = 0; j < N; j++) {
      const fileI = files[i];
      const fileJ = files[j];
      const cell = document.createElement("div");

      if (i === j) {
        cell.className = "heatmap-cell diagonal";
        cell.title = fileI;
      } else {
        const match = pairMap.get(`${fileI}|${fileJ}`);
        if (match) {
          const severity = getSeverity(match.score);
          cell.className = `heatmap-cell ${severity}`;
          cell.title = `${fileI} vs ${fileJ}: ${formatPct(match.score)}`;
          
          // Apply opacity scaling within severity band for true heatmap feel
          let intensity = 1.0;
          if (severity === 'low') intensity = 0.3 + (match.score / 0.3) * 0.7;
          else if (severity === 'medium') intensity = 0.4 + ((match.score - 0.3) / 0.4) * 0.6;
          else intensity = 0.5 + ((match.score - 0.7) / 0.3) * 0.5;
          cell.style.opacity = intensity.toFixed(2);

          if (match.reportId) {
            cell.addEventListener("click", () => openDiffViewer(match.reportId));
          }
        } else {
          // Missing pair
          cell.className = "heatmap-cell";
          cell.style.background = "var(--border)";
          cell.title = `${fileI} vs ${fileJ}: No data`;
        }
      }
      heatmap.appendChild(cell);
    }
  }
}
