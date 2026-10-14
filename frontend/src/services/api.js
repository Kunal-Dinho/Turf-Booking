import api from "../api";

/**
 * Checks the health status of the Spring Boot backend
 * Calls GET http://localhost:8080/api/health
 */
export const checkHealth = async () => {
  const response = await api.get("/api/health");
  return response.data;
};

export default api;
