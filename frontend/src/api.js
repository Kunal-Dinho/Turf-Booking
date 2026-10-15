import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
});

// Automatically inject JWT Bearer token into outgoing requests if present
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("turfbook_token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Gracefully handle 401 Unauthorized responses on protected requests
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      // If a protected request fails authentication, remove stale tokens
      const requestUrl = error.config?.url || "";
      if (requestUrl.includes("/api/auth/me") || requestUrl.includes("/api/bookings/my") || requestUrl.includes("/cancel")) {
        localStorage.removeItem("turfbook_token");
        localStorage.removeItem("turfbook_user");
      }
    }
    return Promise.reject(error);
  }
);

export default api;
