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

/**
 * Retrieves public payment configuration (test key ID & currency).
 * Calls GET /api/payments/config
 * @returns {Promise<{keyId: string, currency: string, paymentWindowMinutes: number}>}
 */
export const getPaymentConfig = async () => {
  const response = await api.get("/api/payments/config");
  return response.data;
};

/**
 * Submits client checkout response to server for authoritative HMAC verification.
 * Calls POST /api/payments/verify
 * @param {Object} verificationData { bookingReference, razorpayOrderId, razorpayPaymentId, razorpaySignature }
 * @returns {Promise<Object>} The confirmed booking response
 */
export const verifyPayment = async (verificationData) => {
  const response = await api.post("/api/payments/verify", verificationData);
  return response.data;
};

/**
 * Notifies server to release slot when payment is dismissed or cancelled before completion.
 * Calls POST /api/payments/dismiss
 * @param {string} bookingReference
 * @returns {Promise<Object>}
 */
export const dismissPayment = async (bookingReference) => {
  const response = await api.post("/api/payments/dismiss", { bookingReference });
  return response.data;
};


