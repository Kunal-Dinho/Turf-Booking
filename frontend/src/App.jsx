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
  AlertCircle
} from "lucide-react";
import ApiStatusBadge from "./components/ApiStatusBadge";
import { getAvailability, createBooking } from "./bookingService";
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

  // Booking modal and form states
  const [modalStep, setModalStep] = useState("details"); // 'details' | 'form' | 'success'
  const [bookingDate, setBookingDate] = useState(getTomorrowString);
  const [bookingTime, setBookingTime] = useState("18:00");
  const [durationHours, setDurationHours] = useState(1);
  const [customerName, setCustomerName] = useState("");
  const [customerEmail, setCustomerEmail] = useState("");
  const [customerPhone, setCustomerPhone] = useState("");
  const [bookedSlots, setBookedSlots] = useState([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [bookingError, setBookingError] = useState("");
  const [confirmedBooking, setConfirmedBooking] = useState(null);

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
  };

  const handleCloseModal = () => {
    setSelectedTurf(null);
    setModalStep("details");
    setBookingError("");
    setConfirmedBooking(null);
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
          <button
            className="login-button"
            onClick={() =>
              alert("Login will be implemented in a later phase.")
            }
          >
            Sign in
          </button>
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
                      className={`turf-image ${
                        turf.sport?.toLowerCase() === "cricket"
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
                  ℹ️ Guest checkout mode. Payment gateway and account synchronization will be enabled in upcoming phases.
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
                  Your reservation is confirmed under status <strong>{confirmedBooking.status}</strong>.
                </p>

                <button
                  type="button"
                  className="booking-button"
                  onClick={handleCloseModal}
                >
                  Done
                </button>
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
