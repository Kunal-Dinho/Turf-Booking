import { useEffect, useState } from "react";
import api from "./api";
import {
  MapPin,
  Search,
  ArrowRight,
  ArrowLeft,
  Calendar,
  Clock,
  CheckCircle2,
  AlertCircle,
  User,
  LogOut,
  BookmarkCheck,
  XCircle,
  AlertTriangle
} from "lucide-react";
import ApiStatusBadge from "./components/ApiStatusBadge";
import { getAvailability, createBooking, getMyBookings, cancelBooking } from "./bookingService";
import {
  register as registerCustomer,
  login as loginCustomer,
  logout as logoutCustomer,
  getStoredUser,
  getStoredToken,
  getCurrentUser
} from "./authService";
import "./App.css";

const TIME_SLOTS = [
  "06:00", "07:00", "08:00", "09:00", "10:00", "11:00",
  "12:00", "13:00", "14:00", "15:00", "16:00", "17:00",
  "18:00", "19:00", "20:00", "21:00", "22:00"
];

const getTodayString = () => {
  return new Date().toISOString().split("T")[0];
};

const getTomorrowString = () => {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return d.toISOString().split("T")[0];
};

function App() {
  const [search, setSearch] = useState("");
  const [sport, setSport] = useState("All");
  const [selectedTurf, setSelectedTurf] = useState(null);

  const [turfs, setTurfs] = useState([]);
  const [turfsLoading, setTurfsLoading] = useState(true);
  const [turfsError, setTurfsError] = useState("");

  // Customer session & authentication states
  const [currentUser, setCurrentUser] = useState(getStoredUser);
  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [authMode, setAuthMode] = useState("login"); // 'login' | 'register'
  const [authName, setAuthName] = useState("");
  const [authEmail, setAuthEmail] = useState("");
  const [authPassword, setAuthPassword] = useState("");
  const [authSubmitting, setAuthSubmitting] = useState(false);
  const [authError, setAuthError] = useState("");

  // My Bookings modal states
  const [myBookingsOpen, setMyBookingsOpen] = useState(false);
  const [myBookings, setMyBookings] = useState([]);
  const [myBookingsLoading, setMyBookingsLoading] = useState(false);
  const [myBookingsError, setMyBookingsError] = useState("");
  const [cancellingBooking, setCancellingBooking] = useState(null);
  const [isCancelling, setIsCancelling] = useState(false);
  const [cancelError, setCancelError] = useState("");
  const [cancelSuccess, setCancelSuccess] = useState("");

  // Booking modal and form states
  const [modalStep, setModalStep] = useState("details"); // 'details' | 'form' | 'success'
  const [bookingDate, setBookingDate] = useState(getTomorrowString);
  const [bookingTime, setBookingTime] = useState("18:00");
  const [durationHours, setDurationHours] = useState(1);
  const [customerName, setCustomerName] = useState(() => getStoredUser()?.name || "");
  const [customerEmail, setCustomerEmail] = useState(() => getStoredUser()?.email || "");
  const [customerPhone, setCustomerPhone] = useState("");
  const [bookedSlots, setBookedSlots] = useState([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [bookingError, setBookingError] = useState("");
  const [confirmedBooking, setConfirmedBooking] = useState(null);

  // Restore & verify authenticated session on mount
  useEffect(() => {
    if (getStoredToken()) {
      getCurrentUser()
        .then((user) => {
          setCurrentUser(user);
        })
        .catch(() => {
          setCurrentUser(null);
        });
    }
  }, []);

  const filteredTurfs = turfs.filter((turf) => {
    const matchesSearch =
      `${turf.name} ${turf.location}`
        .toLowerCase()
        .includes(search.toLowerCase());

    const matchesSport =
      sport === "All" || turf.sport === sport;

    return matchesSearch && matchesSport;
  });

  useEffect(() => {
    api.get("/api/turfs")
      .then((response) => {
        setTurfs(response.data);
      })
      .catch((err) => {
        console.error("Failed to load turfs:", err);
        setTurfsError("Could not load turfs. Please try again.");
      })
      .finally(() => {
        setTurfsLoading(false);
      });
  }, []);

  // Fetch slot availability when selected turf or date changes
  useEffect(() => {
    if (!selectedTurf) return;
    let isCancelled = false;

    getAvailability(selectedTurf.id, bookingDate)
      .then((data) => {
        if (!isCancelled) {
          setBookedSlots(data.bookedSlots || []);
        }
      })
      .catch((err) => {
        console.error("Availability check failed:", err);
        if (!isCancelled) {
          setBookedSlots([]);
        }
      });

    return () => {
      isCancelled = true;
    };
  }, [selectedTurf, bookingDate]);

  const handleOpenTurf = (turf) => {
    setSelectedTurf(turf);
    setModalStep("details");
    setBookingError("");
    if (currentUser) {
      if (!customerName) setCustomerName(currentUser.name || "");
      if (!customerEmail) setCustomerEmail(currentUser.email || "");
    }
  };

  const handleCloseModal = () => {
    setSelectedTurf(null);
    setModalStep("details");
    setBookingError("");
    setConfirmedBooking(null);
  };

  const handleAuthSubmit = async (e) => {
    e.preventDefault();
    setAuthSubmitting(true);
    setAuthError("");

    try {
      let data;
      if (authMode === "register") {
        data = await registerCustomer(authName.trim(), authEmail.trim(), authPassword);
      } else {
        data = await loginCustomer(authEmail.trim(), authPassword);
      }
      const user = { id: data.id, name: data.name, email: data.email };
      setCurrentUser(user);
      setAuthModalOpen(false);
      setAuthPassword("");
      // Sync form fields with authenticated customer details
      setCustomerName(user.name);
      setCustomerEmail(user.email);
    } catch (err) {
      const errorMsg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        err.message ||
        "Authentication failed. Please verify your credentials.";
      setAuthError(errorMsg);
    } finally {
      setAuthSubmitting(false);
    }
  };

  const handleLogout = () => {
    logoutCustomer();
    setCurrentUser(null);
    setMyBookingsOpen(false);
    setMyBookings([]);
    setCancellingBooking(null);
    setCancelError("");
    setCancelSuccess("");
  };

  const handleOpenMyBookings = async () => {
    setMyBookingsOpen(true);
    setMyBookingsLoading(true);
    setMyBookingsError("");
    setCancelError("");
    setCancelSuccess("");
    try {
      const data = await getMyBookings();
      setMyBookings(data);
    } catch (err) {
      if (err.response?.status === 401) {
        handleLogout();
        setAuthError("Your session has expired. Please sign in again.");
        setAuthModalOpen(true);
        return;
      }
      const errorMsg =
        err.response?.data?.message ||
        err.message ||
        "Failed to load your reservations. Please try again.";
      setMyBookingsError(errorMsg);
    } finally {
      setMyBookingsLoading(false);
    }
  };

  const isBookingCancellable = (booking) => {
    if (!booking || booking.status !== "CONFIRMED") return false;
    try {
      if (!booking.bookingDate || !booking.startTime) return false;
      const [year, month, day] = booking.bookingDate.split("-").map(Number);
      const [hour, minute] = booking.startTime.split(":").map(Number);
      const bookingStart = new Date(year, month - 1, day, hour, minute);
      return bookingStart > new Date();
    } catch {
      return false;
    }
  };

  const handleInitiateCancel = (booking) => {
    setCancelError("");
    setCancelSuccess("");
    setCancellingBooking(booking);
  };

  const handleConfirmCancel = async () => {
    if (!cancellingBooking) return;
    setIsCancelling(true);
    setCancelError("");
    try {
      const updated = await cancelBooking(cancellingBooking.bookingReference);
      setCancelSuccess(`Reservation ${updated.bookingReference} was cancelled successfully. The reserved slot has been released.`);
      setCancellingBooking(null);

      // Reflect cancelled status immediately in local list
      setMyBookings((prev) =>
        prev.map((b) =>
          b.bookingReference === updated.bookingReference
            ? { ...b, status: "CANCELLED", cancelledAt: updated.cancelledAt }
            : b
        )
      );

      // Re-fetch availability if turf booking modal is active
      if (selectedTurf) {
        getAvailability(selectedTurf.id, bookingDate)
          .then((data) => setBookedSlots(data.bookedSlots || []))
          .catch((err) => console.error("Availability refresh failed:", err));
      }
    } catch (err) {
      if (err.response?.status === 401) {
        handleLogout();
        setAuthError("Your session has expired. Please sign in again.");
        setAuthModalOpen(true);
        setMyBookingsOpen(false);
        setCancellingBooking(null);
      } else {
        const errorMsg =
          err.response?.data?.message ||
          err.response?.data?.error ||
          err.message ||
          "Failed to cancel booking. Please try again.";
        setCancelError(errorMsg);
      }
    } finally {
      setIsCancelling(false);
    }
  };

  const handleBookingSubmit = async (e) => {
    e.preventDefault();
    if (!selectedTurf) return;
    setIsSubmitting(true);
    setBookingError("");

    try {
      const payload = {
        turfId: selectedTurf.id,
        customerName: customerName.trim(),
        customerEmail: customerEmail.trim(),
        customerPhone: customerPhone.trim() || undefined,
        bookingDate,
        startTime: bookingTime,
        durationHours: Number(durationHours),
      };

      const result = await createBooking(payload);
      setConfirmedBooking(result);
      setModalStep("success");
    } catch (err) {
      const errorMsg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        err.message ||
        "Booking submission failed. Please try again.";
      setBookingError(errorMsg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="app">
      <header className="navbar">
        <a className="logo" href="#">
          <span className="logo-icon">T</span>
          TurfBook
        </a>

        <nav>
          <a href="#turfs">Explore turfs</a>
          <a href="#how-it-works">How it works</a>
        </nav>

        <div className="navbar-actions">
          <ApiStatusBadge />
          {currentUser ? (
            <div className="user-nav-group">
              <button
                className="my-bookings-btn"
                onClick={handleOpenMyBookings}
                aria-label="View my bookings"
              >
                <BookmarkCheck size={16} />
                <span>My Bookings</span>
              </button>
              <div className="user-profile-badge" title={currentUser.email}>
                <User size={15} />
                <span className="user-name">{currentUser.name.split(" ")[0]}</span>
              </div>
              <button
                className="logout-button"
                onClick={handleLogout}
                aria-label="Sign out"
                title="Sign out"
              >
                <LogOut size={16} />
              </button>
            </div>
          ) : (
            <button
              className="login-button"
              onClick={() => {
                setAuthMode("login");
                setAuthError("");
                setAuthModalOpen(true);
              }}
            >
              Sign in
            </button>
          )}
        </div>
      </header>

      <main>
        <section className="hero">
          <div className="hero-content">
            <span className="eyebrow">
              YOUR GAME. YOUR GROUND.
            </span>

            <h1>
              Find your ground.
              <br />
              <span>Own the game.</span>
            </h1>

            <p>
              Discover sports turfs, check availability,
              and get your team ready to play.
            </p>

            <div className="search-panel">
              <MapPin size={20} />
              <input
                aria-label="Search by turf or location"
                placeholder="Search turf or location..."
                value={search}
                onChange={(event) =>
                  setSearch(event.target.value)
                }
              />

              <button
                aria-label="Search turfs"
                onClick={() =>
                  document
                    .getElementById("turfs")
                    ?.scrollIntoView({ behavior: "smooth" })
                }
              >
                <Search size={19} />
                Search
              </button>
            </div>

            <div className="hero-note">
              <span>✓</span> Easy booking
              <span>✓</span> Clear pricing
              <span>✓</span> Play together
            </div>
          </div>

          <div className="hero-art">
            <div className="art-circle">
              <span>⚽</span>
            </div>
            <div className="floating-card">
              <span className="live-dot" />
              Ready for your next match?
            </div>
          </div>
        </section>

        <section className="turfs-section" id="turfs">
          <div className="section-heading">
            <div>
              <span className="eyebrow">PICK YOUR PLAYGROUND</span>
              <h2>Explore turfs</h2>
              <p>Find a place for your next game.</p>
            </div>

            <select
              aria-label="Filter by sport"
              value={sport}
              onChange={(event) => setSport(event.target.value)}
            >
              <option value="All">All sports</option>
              <option value="Football">Football</option>
              <option value="Cricket">Cricket</option>
            </select>
          </div>

          {turfsLoading && <p className="empty-state">Loading turfs...</p>}

          {turfsError && <p className="empty-state">{turfsError}</p>}

          {!turfsLoading && !turfsError && (
            <>
              <div className="turf-grid">
                {filteredTurfs.map((turf) => (
                  <article className="turf-card" key={turf.id}>
                    <div
                      className={`turf-image ${turf.sport?.toLowerCase() === "cricket"
                          ? "orange"
                          : "blue"
                        }`}
                    >
                      <span className="sport-emoji">
                        {turf.sport?.toLowerCase() === "cricket"
                          ? "🏏"
                          : "⚽"}
                      </span>
                      <span className="sample-tag">Live listing</span>
                    </div>

                    <div className="turf-info">
                      <div className="turf-title">
                        <h3>{turf.name}</h3>
                        <span className="sport-tag">{turf.sport}</span>
                      </div>

                      <p className="location">
                        <MapPin size={16} />
                        {turf.location}
                      </p>

                      <div className="card-footer">
                        <p>
                          <strong>
                            ₹{Number(turf.pricePerHour).toLocaleString("en-IN")}
                          </strong>
                          <span> / hour</span>
                        </p>

                        <button
                          aria-label={`View ${turf.name}`}
                          onClick={() => handleOpenTurf(turf)}
                        >
                          <ArrowRight size={20} />
                        </button>
                      </div>
                    </div>
                  </article>
                ))}
              </div>

              {filteredTurfs.length === 0 && (
                <p className="empty-state">
                  No matching turfs. Try another search.
                </p>
              )}
            </>
          )}
        </section>

        <section className="how-section" id="how-it-works">
          <span className="eyebrow">SIMPLE BY DESIGN</span>
          <h2>Three steps. One great game.</h2>

          <div className="steps-grid">
            <div>
              <span className="step-number">01</span>
              <h3>Discover</h3>
              <p>Find a turf that suits your team and sport.</p>
            </div>

            <div>
              <span className="step-number">02</span>
              <h3>Choose a slot</h3>
              <p>Select a date and an available playing time.</p>
            </div>

            <div>
              <span className="step-number">03</span>
              <h3>Get playing</h3>
              <p>Confirm your reservation and bring your team.</p>
            </div>
          </div>
        </section>
      </main>

      {selectedTurf && (
        <div
          className="turf-modal-backdrop"
          onClick={handleCloseModal}
        >
          <section
            className={`turf-modal ${modalStep === "form" ? "booking-modal-wide" : ""}`}
            role="dialog"
            aria-modal="true"
            aria-labelledby="turf-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <button
              className="modal-close"
              onClick={handleCloseModal}
              aria-label="Close modal"
            >
              ×
            </button>

            {modalStep === "details" && (
              <>
                <span className="eyebrow">TURF DETAILS</span>

                <h2 id="turf-modal-title">
                  {selectedTurf.name}
                </h2>

                <p>
                  <MapPin size={16} />
                  {" "}{selectedTurf.location}
                </p>

                <p>Sport: {selectedTurf.sport}</p>

                <p className="modal-price">
                  ₹{Number(selectedTurf.pricePerHour).toLocaleString("en-IN")}
                  <span> / hour</span>
                </p>

                <button
                  className="booking-button"
                  onClick={() => {
                    setModalStep("form");
                    setBookingError("");
                  }}
                >
                  Continue to booking
                  <ArrowRight size={18} />
                </button>
              </>
            )}

            {modalStep === "form" && (
              <form onSubmit={handleBookingSubmit} className="booking-form">
                <div className="booking-form-header">
                  <button
                    type="button"
                    className="modal-back-btn"
                    onClick={() => {
                      setModalStep("details");
                      setBookingError("");
                    }}
                    aria-label="Back to turf details"
                  >
                    <ArrowLeft size={16} /> Back
                  </button>
                  <span className="eyebrow">RESERVE TURF</span>
                </div>

                <h2 id="turf-modal-title">{selectedTurf.name}</h2>
                <p className="booking-modal-sub">
                  <MapPin size={14} /> {selectedTurf.location} · {selectedTurf.sport}
                </p>

                {bookingError && (
                  <div className="booking-alert-error" role="alert">
                    <AlertCircle size={18} />
                    <span>{bookingError}</span>
                  </div>
                )}

                <div className="form-group-row">
                  <div className="form-group">
                    <label htmlFor="booking-date">
                      <Calendar size={14} /> Date
                    </label>
                    <input
                      id="booking-date"
                      type="date"
                      min={getTodayString()}
                      value={bookingDate}
                      onChange={(e) => setBookingDate(e.target.value)}
                      required
                    />
                  </div>

                  <div className="form-group">
                    <label htmlFor="booking-duration">Duration</label>
                    <select
                      id="booking-duration"
                      value={durationHours}
                      onChange={(e) => setDurationHours(Number(e.target.value))}
                    >
                      <option value={1}>1 hour</option>
                      <option value={2}>2 hours</option>
                      <option value={3}>3 hours</option>
                      <option value={4}>4 hours</option>
                    </select>
                  </div>
                </div>

                <div className="form-group">
                  <label htmlFor="booking-time">
                    <Clock size={14} /> Start Time
                  </label>
                  <select
                    id="booking-time"
                    value={bookingTime}
                    onChange={(e) => setBookingTime(e.target.value)}
                    required
                  >
                    {TIME_SLOTS.map((slot) => {
                      const isBooked = bookedSlots.includes(slot);
                      return (
                        <option key={slot} value={slot} disabled={isBooked}>
                          {slot} {isBooked ? "• (Already Booked)" : "• Available"}
                        </option>
                      );
                    })}
                  </select>
                </div>

                <div className="form-group-row">
                  <div className="form-group">
                    <label htmlFor="customer-name">Your Name</label>
                    <input
                      id="customer-name"
                      type="text"
                      placeholder="e.g. Alex Hunter"
                      value={customerName}
                      onChange={(e) => setCustomerName(e.target.value)}
                      required
                    />
                  </div>

                  <div className="form-group">
                    <label htmlFor="customer-phone">Phone (optional)</label>
                    <input
                      id="customer-phone"
                      type="tel"
                      placeholder="+91 98765 43210"
                      value={customerPhone}
                      onChange={(e) => setCustomerPhone(e.target.value)}
                    />
                  </div>
                </div>

                <div className="form-group">
                  <label htmlFor="customer-email">Email Address</label>
                  <input
                    id="customer-email"
                    type="email"
                    placeholder="alex@example.com"
                    value={customerEmail}
                    onChange={(e) => setCustomerEmail(e.target.value)}
                    required
                  />
                </div>

                <div className="booking-price-card">
                  <div className="price-line">
                    <span>Hourly Rate</span>
                    <strong>₹{Number(selectedTurf.pricePerHour).toLocaleString("en-IN")} / hr</strong>
                  </div>
                  <div className="price-line">
                    <span>Duration</span>
                    <span>{durationHours} hr{durationHours > 1 ? "s" : ""}</span>
                  </div>
                  <div className="price-line total-price-line">
                    <span>Total Calculated Price</span>
                    <strong className="total-amount">
                      ₹{Number(selectedTurf.pricePerHour * durationHours).toLocaleString("en-IN")}
                    </strong>
                  </div>
                </div>

                <div className="guest-booking-notice">
                  {currentUser ? (
                    `ℹ️ Authenticated as ${currentUser.name}. This reservation will automatically be linked to your account.`
                  ) : (
                    "ℹ️ Guest checkout mode. Sign in to link your reservations to your personal account."
                  )}
                </div>

                <button
                  type="submit"
                  className="booking-button"
                  disabled={isSubmitting || bookedSlots.includes(bookingTime)}
                >
                  {isSubmitting ? (
                    "Processing Booking..."
                  ) : (
                    <>
                      Confirm Reservation
                      <ArrowRight size={18} />
                    </>
                  )}
                </button>
              </form>
            )}

            {modalStep === "success" && confirmedBooking && (
              <div className="booking-success-view">
                <div className="success-icon-wrap">
                  <CheckCircle2 size={48} className="success-check-icon" />
                </div>

                <span className="eyebrow">RESERVATION CONFIRMED</span>
                <h2>You're booked to play!</h2>

                <div className="booking-reference-badge">
                  <span>Booking Reference</span>
                  <strong>{confirmedBooking.bookingReference}</strong>
                </div>

                <div className="success-summary-box">
                  <div className="summary-row">
                    <span>Turf</span>
                    <strong>{confirmedBooking.turfName}</strong>
                  </div>
                  <div className="summary-row">
                    <span>Date</span>
                    <strong>{confirmedBooking.bookingDate}</strong>
                  </div>
                  <div className="summary-row">
                    <span>Time Slot</span>
                    <strong>
                      {confirmedBooking.startTime} – {confirmedBooking.endTime} ({confirmedBooking.durationHours} hr)
                    </strong>
                  </div>
                  <div className="summary-row">
                    <span>Customer</span>
                    <strong>{confirmedBooking.customerName}</strong>
                  </div>
                  <div className="summary-row total-row">
                    <span>Total Price</span>
                    <strong className="success-total">
                      ₹{Number(confirmedBooking.totalPrice).toLocaleString("en-IN")}
                    </strong>
                  </div>
                </div>

                <p className="success-guest-note">
                  {currentUser ? (
                    <>
                      Linked to your account (<strong>{currentUser.email}</strong>). View all your reservations in <strong>My Bookings</strong>.
                    </>
                  ) : (
                    <>
                      Your reservation is confirmed under status <strong>{confirmedBooking.status}</strong>. Keep your booking reference handy!
                    </>
                  )}
                </p>

                <div className="form-group-row">
                  {currentUser && (
                    <button
                      type="button"
                      className="modal-back-btn"
                      style={{ justifyContent: "center", padding: "12px", width: "100%" }}
                      onClick={() => {
                        handleCloseModal();
                        handleOpenMyBookings();
                      }}
                    >
                      <BookmarkCheck size={16} /> View My Bookings
                    </button>
                  )}
                  <button
                    type="button"
                    className="booking-button"
                    style={{ marginTop: 0 }}
                    onClick={handleCloseModal}
                  >
                    Done
                  </button>
                </div>
              </div>
            )}
          </section>
        </div>
      )}

      {/* Auth Modal: Sign In / Create Account */}
      {authModalOpen && (
        <div className="turf-modal-backdrop" onClick={() => setAuthModalOpen(false)}>
          <section
            className="turf-modal auth-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="auth-modal-title"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              className="modal-close"
              onClick={() => setAuthModalOpen(false)}
              aria-label="Close modal"
            >
              ×
            </button>

            <div className="auth-tabs">
              <button
                type="button"
                className={`auth-tab ${authMode === "login" ? "active" : ""}`}
                onClick={() => {
                  setAuthMode("login");
                  setAuthError("");
                }}
              >
                Sign In
              </button>
              <button
                type="button"
                className={`auth-tab ${authMode === "register" ? "active" : ""}`}
                onClick={() => {
                  setAuthMode("register");
                  setAuthError("");
                }}
              >
                Create Account
              </button>
            </div>

            <h2 id="auth-modal-title">
              {authMode === "login" ? "Welcome back" : "Create your account"}
            </h2>
            <p className="auth-modal-sub">
              {authMode === "login"
                ? "Sign in to access your bookings and reserve turfs quickly."
                : "Register with TurfBook to manage and view all your reservations."}
            </p>

            {authError && (
              <div className="booking-alert-error" role="alert">
                <AlertCircle size={18} />
                <span>{authError}</span>
              </div>
            )}

            <form onSubmit={handleAuthSubmit} className="booking-form auth-form">
              {authMode === "register" && (
                <div className="form-group">
                  <label htmlFor="auth-name">Full Name</label>
                  <input
                    id="auth-name"
                    type="text"
                    placeholder="e.g. Alex Hunter"
                    value={authName}
                    onChange={(e) => setAuthName(e.target.value)}
                    required
                  />
                </div>
              )}

              <div className="form-group">
                <label htmlFor="auth-email">Email Address</label>
                <input
                  id="auth-email"
                  type="email"
                  placeholder="alex@example.com"
                  value={authEmail}
                  onChange={(e) => setAuthEmail(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label htmlFor="auth-password">Password</label>
                <input
                  id="auth-password"
                  type="password"
                  placeholder={authMode === "register" ? "Minimum 6 characters" : "••••••••"}
                  value={authPassword}
                  onChange={(e) => setAuthPassword(e.target.value)}
                  minLength={authMode === "register" ? 6 : undefined}
                  required
                />
              </div>

              <button
                type="submit"
                className="booking-button auth-submit-btn"
                disabled={authSubmitting}
              >
                {authSubmitting ? (
                  "Processing..."
                ) : authMode === "login" ? (
                  <>
                    Sign In
                    <ArrowRight size={18} />
                  </>
                ) : (
                  <>
                    Create Account
                    <ArrowRight size={18} />
                  </>
                )}
              </button>
            </form>
          </section>
        </div>
      )}

      {/* My Bookings Modal: Protected Customer Reservations */}
      {myBookingsOpen && (
        <div className="turf-modal-backdrop" onClick={() => setMyBookingsOpen(false)}>
          <section
            className="turf-modal booking-modal-wide my-bookings-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="my-bookings-title"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              className="modal-close"
              onClick={() => setMyBookingsOpen(false)}
              aria-label="Close modal"
            >
              ×
            </button>

            <span className="eyebrow">CUSTOMER PORTAL</span>
            <h2 id="my-bookings-title">My Bookings</h2>
            <p className="booking-modal-sub">
              Your confirmed ground reservations for <strong>{currentUser?.name}</strong>
            </p>

            {myBookingsLoading && (
              <p className="empty-state">Loading your reservations...</p>
            )}

            {cancelSuccess && (
              <div className="booking-alert-success" role="status">
                <CheckCircle2 size={18} />
                <span>{cancelSuccess}</span>
              </div>
            )}

            {myBookingsError && (
              <div className="booking-alert-error" role="alert">
                <AlertCircle size={18} />
                <span>{myBookingsError}</span>
              </div>
            )}

            {!myBookingsLoading && !myBookingsError && (
              <>
                {myBookings.length === 0 ? (
                  <div className="my-bookings-empty">
                    <p className="empty-state">
                      No reservations found for your account yet.
                    </p>
                    <button
                      className="booking-button"
                      onClick={() => {
                        setMyBookingsOpen(false);
                        document.getElementById("turfs")?.scrollIntoView({ behavior: "smooth" });
                      }}
                    >
                      Explore Turfs Now
                    </button>
                  </div>
                ) : (
                  <div className="my-bookings-list">
                    {myBookings.map((b) => (
                      <article className={`my-booking-card ${b.status?.toLowerCase()}`} key={b.id || b.bookingReference}>
                        <div className="my-booking-header">
                          <div>
                            <h4>{b.turfName}</h4>
                            <span className="booking-ref-tag">{b.bookingReference}</span>
                          </div>
                          <span className={`status-pill ${b.status?.toLowerCase()}`}>
                            {b.status}
                          </span>
                        </div>

                        <div className="my-booking-details">
                          <p>
                            <Calendar size={14} />
                            <span>{b.bookingDate}</span>
                          </p>
                          <p>
                            <Clock size={14} />
                            <span>{b.startTime} – {b.endTime} ({b.durationHours} hr{b.durationHours > 1 ? "s" : ""})</span>
                          </p>
                          <p className="my-booking-price">
                            Total: <strong>₹{Number(b.totalPrice).toLocaleString("en-IN")}</strong>
                          </p>
                        </div>

                        <div className="my-booking-footer">
                          {isBookingCancellable(b) ? (
                            <button
                              type="button"
                              className="cancel-booking-btn"
                              onClick={() => handleInitiateCancel(b)}
                              aria-label={`Cancel reservation ${b.bookingReference}`}
                            >
                              <XCircle size={14} />
                              <span>Cancel Reservation</span>
                            </button>
                          ) : b.status === "CONFIRMED" ? (
                            <span className="booking-status-note past">
                              Past start time (Cancellation closed)
                            </span>
                          ) : (
                            <span className="booking-status-note cancelled">
                              Cancelled · Slot released
                            </span>
                          )}
                        </div>
                      </article>
                    ))}
                  </div>
                )}
              </>
            )}

            {/* Cancellation Confirmation Dialog Overlay */}
            {cancellingBooking && (
              <div
                className="cancel-confirm-backdrop"
                onClick={() => !isCancelling && setCancellingBooking(null)}
              >
                <div
                  className="cancel-confirm-modal"
                  role="alertdialog"
                  aria-modal="true"
                  aria-labelledby="cancel-confirm-title"
                  onClick={(e) => e.stopPropagation()}
                >
                  <div className="cancel-confirm-header">
                    <AlertTriangle size={24} className="cancel-icon-warning" />
                    <div>
                      <h3 id="cancel-confirm-title">Cancel Reservation?</h3>
                      <span className="cancel-ref-sub">Ref: {cancellingBooking.bookingReference}</span>
                    </div>
                  </div>

                  <div className="cancel-confirm-body">
                    <p>
                      Are you sure you want to cancel your reservation for <strong>{cancellingBooking.turfName}</strong> on <strong>{cancellingBooking.bookingDate}</strong> from <strong>{cancellingBooking.startTime} to {cancellingBooking.endTime}</strong>?
                    </p>

                    <div className="cancellation-policy-box">
                      <span className="policy-badge">Cancellation Policy</span>
                      <p>
                        Bookings can only be cancelled prior to scheduled start time. Upon cancellation, your reserved slot is immediately released and made available for other players.
                      </p>
                    </div>

                    {cancelError && (
                      <div className="booking-alert-error" role="alert">
                        <AlertCircle size={16} />
                        <span>{cancelError}</span>
                      </div>
                    )}
                  </div>

                  <div className="cancel-confirm-footer">
                    <button
                      type="button"
                      className="keep-booking-btn"
                      onClick={() => setCancellingBooking(null)}
                      disabled={isCancelling}
                    >
                      Keep Reservation
                    </button>
                    <button
                      type="button"
                      className="confirm-cancel-btn"
                      onClick={handleConfirmCancel}
                      disabled={isCancelling}
                    >
                      {isCancelling ? "Cancelling..." : "Yes, Cancel Booking"}
                    </button>
                  </div>
                </div>
              </div>
            )}
          </section>
        </div>
      )}

      <footer>
        <a className="logo" href="#">
          <span className="logo-icon">T</span>
          TurfBook
        </a>
        <p>Built for the love of the game.</p>
        <p>Portfolio project · Sample data</p>
      </footer>
    </div>
  );
}

export default App;
