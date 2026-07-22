export const API_BASE = window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1" || window.location.protocol === "file:"
  ? "http://localhost:9090"
  : "https://codesniff-backend.azurewebsites.net";
