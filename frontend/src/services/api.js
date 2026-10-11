import axios from "axios";

// Create a centralized Axios instance for all backend requests
const api = axios.create({
  baseURL: "http://localhost:8080/api",
  timeout: 5000,
  headers: {
    "Content-Type": "application/json",
  },
});

/**
 * Checks the health status of the Spring Boot backend
 * Calls GET http://localhost:8080/api/health
 */
export const checkHealth = async () => {
  const response = await api.get("/health");
  return response.data;
};

export default api;
