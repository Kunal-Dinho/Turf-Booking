
import api from "./api";

export const getTurfs = async () => {
    const response = await api.get("/api/turfs");
    return response.data;
};
