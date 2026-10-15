import api from "./api";

const TOKEN_KEY = "turfbook_token";
const USER_KEY = "turfbook_user";

export const getStoredToken = () => {
  return localStorage.getItem(TOKEN_KEY);
};

export const getStoredUser = () => {
  const userStr = localStorage.getItem(USER_KEY);
  if (!userStr) return null;
  try {
    return JSON.parse(userStr);
  } catch {
    localStorage.removeItem(USER_KEY);
    return null;
  }
};

export const saveAuthData = (token, user) => {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  }
  if (user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  }
};

export const clearAuthData = () => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
};

/**
 * Registers a new customer account.
 */
export const register = async (name, email, password) => {
  const response = await api.post("/api/auth/register", {
    name,
    email,
    password,
  });
  const data = response.data;
  saveAuthData(data.token, { id: data.id, name: data.name, email: data.email });
  return data;
};

/**
 * Logs in with email and password.
 */
export const login = async (email, password) => {
  const response = await api.post("/api/auth/login", {
    email,
    password,
  });
  const data = response.data;
  saveAuthData(data.token, { id: data.id, name: data.name, email: data.email });
  return data;
};

/**
 * Fetches the currently authenticated customer's profile.
 */
export const getCurrentUser = async () => {
  const response = await api.get("/api/auth/me");
  const data = response.data;
  saveAuthData(null, { id: data.id, name: data.name, email: data.email });
  return data;
};

/**
 * Logs out the customer and clears all local credentials.
 */
export const logout = () => {
  clearAuthData();
};
