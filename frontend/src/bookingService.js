import api from "./api";

/**
 * Checks available and booked slots for a specific turf and date.
 * @param {string} turfId 
 * @param {string} date (YYYY-MM-DD)
 * @returns {Promise<{turfId: string, date: string, bookedSlots: string[], operatingStart: string, operatingEnd: string}>}
 */
export const getAvailability = async (turfId, date) => {
  const response = await api.get("/api/bookings/availability", {
    params: { turfId, date },
  });
  return response.data;
};

/**
 * Submits a new turf booking.
 * Note: Guest booking only. In future phases, user authentication & payment gateway tokens will be attached here.
 * @param {Object} bookingData 
 * @returns {Promise<Object>} The confirmed booking response with reference
 */
export const createBooking = async (bookingData) => {
  const response = await api.post("/api/bookings", bookingData);
  return response.data;
};

/**
 * Retrieves a booking by its reference code.
 * @param {string} reference 
 * @returns {Promise<Object>}
 */
export const getBookingByReference = async (reference) => {
  const response = await api.get(`/api/bookings/${reference}`);
  return response.data;
};

/**
 * Retrieves all bookings belonging to the currently authenticated customer.
 * Calls protected GET /api/bookings/my
 * @returns {Promise<Array<Object>>}
 */
export const getMyBookings = async () => {
  const response = await api.get("/api/bookings/my");
  return response.data;
};

/**
 * Cancels an eligible booking reservation.
 * Calls protected PATCH /api/bookings/{reference}/cancel
 * @param {string} reference
 * @returns {Promise<Object>}
 */
export const cancelBooking = async (reference) => {
  const response = await api.patch(`/api/bookings/${reference}/cancel`);
  return response.data;
};

