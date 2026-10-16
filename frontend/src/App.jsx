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
  AlertTriangle,
  CreditCard,
  ShieldCheck,
  Smartphone,
  Building2
} from "lucide-react";
import ApiStatusBadge from "./components/ApiStatusBadge";
import {
  getAvailability,
  createBooking,
  getMyBookings,
  cancelBooking,
  getBookingByReference,
  verifyPayment,
  dismissPayment,
  getPaymentConfig
} from "./bookingService";
import {
  register as registerCustomer,
  login as loginCustomer,
  logout as logoutCustomer,
  getStoredUser,
  getStoredToken,
  getCurrentUser,
  clearAuthData
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

const isBookingCancellable = (booking) => {
  if (!booking) return false;
  const status = (booking.status || "").toUpperCase();
  if (status !== "CONFIRMED") return false;

  try {
    if (!booking.bookingDate || !booking.startTime) return false;
    let year, month, day;
    if (typeof booking.bookingDate === "string") {
      const parts = booking.bookingDate.split("-").map(Number);
      if (parts.length >= 3) {
        [year, month, day] = parts;
      } else {
        return false;
      }
    } else if (Array.isArray(booking.bookingDate) && booking.bookingDate.length >= 3) {
      [year, month, day] = booking.bookingDate;
    } else {
      return false;
    }

    let hour = 0;
    let minute = 0;
    if (typeof booking.startTime === "string") {
      const timeParts = booking.startTime.split(":").map(Number);
      hour = timeParts[0] || 0;
      minute = timeParts[1] || 0;
    } else if (Array.isArray(booking.startTime)) {
      hour = booking.startTime[0] || 0;
      minute = booking.startTime[1] || 0;
    }

    const bookingStart = new Date(year, month - 1, day, hour, minute);
    return bookingStart.getTime() > Date.now();
  } catch {
    return false;
  }
};

const loadRazorpayScript = () => {
  return new Promise((resolve) => {
    if (window.Razorpay) {
      resolve(true);
      return;
    }
    const existing = document.querySelector('script[src="https://checkout.razorpay.com/v1/checkout.js"]');
    if (existing) {
      existing.addEventListener("load", () => resolve(true));
      existing.addEventListener("error", () => resolve(false));
      return;
    }
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.async = true;
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });
};

function App() {
  const [search, setSearch] = useState("");
  const [sport, setSport] = useState("All");
  const [selectedTurf, setSelectedTurf] = useState(null);

  const [turfs, setTurfs] = useState([]);
  const [turfsLoading, setTurfsLoading] = useState(true);
  const [turfsError, setTurfsError] = useState("");

  // Customer session & authentication states
  const [currentUser, setCurrentUser] = useState(() => {
    const token = getStoredToken();
    const user = getStoredUser();
    return token && user ? user : null;
  });
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

  // Reference lookup state within My Bookings modal
  const [refSearch, setRefSearch] = useState("");
  const [refSearchResult, setRefSearchResult] = useState(null);
  const [refSearchLoading, setRefSearchLoading] = useState(false);
  const [refSearchError, setRefSearchError] = useState("");

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
  const [confirmedBooking, setConfirmedBooking] = useState(null);
  const [bookingError, setBookingError] = useState("");
  const [isVerifyingPayment, setIsVerifyingPayment] = useState(false);
  const [pendingBooking, setPendingBooking] = useState(null);
  const [paymentNotice, setPaymentNotice] = useState("");
  const [paymentConfig, setPaymentConfig] = useState(null);
  const [simulatorModalOpen, setSimulatorModalOpen] = useState(false);
  const [simulatorPaymentMethod, setSimulatorPaymentMethod] = useState("card");

  // Load public Razorpay test configuration on mount
  useEffect(() => {
    getPaymentConfig()
      .then((cfg) => setPaymentConfig(cfg))
      .catch((err) => console.warn("Payment config load deferred:", err));
  }, []);

  // Restore & verify authenticated session on mount
  useEffect(() => {
    const token = getStoredToken();
    if (token) {
      getCurrentUser()
        .then((user) => {
          setCurrentUser(user);
          if (user.name) setCustomerName(user.name);
          if (user.email) setCustomerEmail(user.email);
        })
        .catch((err) => {
          if (err.response?.status === 401) {
            clearAuthData();
            setCurrentUser(null);
          } else {
            const stored = getStoredUser();
            if (stored) setCurrentUser(stored);
          }
        });
    } else {
      clearAuthData();
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
    setMyBookingsOpen(false);
    setAuthModalOpen(false);
    setSelectedTurf(turf);
    setModalStep("details");
    setBookingError("");
    if (currentUser) {
      if (!customerName && currentUser.name) setCustomerName(currentUser.name);
      if (!customerEmail && currentUser.email) setCustomerEmail(currentUser.email);
    }
  };

  const handleCloseModal = () => {
    setSelectedTurf(null);
    setModalStep("details");
    setBookingError("");
    setConfirmedBooking(null);
    setPendingBooking(null);
    setPaymentNotice("");
    setSimulatorModalOpen(false);
  };

  const handleCancelPendingReservation = async () => {
    if (!pendingBooking) return;
    try {
      await dismissPayment(pendingBooking.bookingReference);
      setPendingBooking(null);
      setPaymentNotice("");
      setSimulatorModalOpen(false);
      if (selectedTurf) {
        getAvailability(selectedTurf.id, bookingDate)
          .then((data) => setBookedSlots(data.bookedSlots || []))
          .catch((err) => console.error("Availability refresh failed:", err));
      }
    } catch (err) {
      console.warn("Failed to dismiss pending reservation:", err);
    }
  };

  const handleSimulatorSuccess = async () => {
    if (!pendingBooking) return;
    setIsVerifyingPayment(true);
    setBookingError("");
    try {
      const simPaymentId = "pay_sim_" + Math.random().toString(36).substring(2, 12);
      const simSignature = "sim_sig_" + Math.random().toString(36).substring(2, 16);
      const verified = await verifyPayment({
        bookingReference: pendingBooking.bookingReference,
        razorpayOrderId: pendingBooking.razorpayOrderId,
        razorpayPaymentId: simPaymentId,
        razorpaySignature: simSignature,
      });
      setConfirmedBooking(verified);
      setPendingBooking(null);
      setPaymentNotice("");
      setSimulatorModalOpen(false);
      setModalStep("success");
    } catch (err) {
      const errText =
        err.response?.data?.message ||
        err.response?.data?.error ||
        err.message ||
        "Payment verification failed. Please try again.";
      setBookingError(errText);
    } finally {
      setIsVerifyingPayment(false);
    }
  };

  const handleSimulatorFailure = () => {
    setSimulatorModalOpen(false);
    setBookingError("Payment was declined by the bank simulator (Simulated Failure).");
    if (pendingBooking) {
      setPaymentNotice(
        `Payment window active. Your slot is reserved for 10 minutes (Ref: ${pendingBooking.bookingReference}). Complete payment or cancel to release immediately.`
      );
    }
  };

  const handleSimulatorDismiss = () => {
    setSimulatorModalOpen(false);
    if (pendingBooking) {
      setPaymentNotice(
        `Payment window active. Your slot is reserved for 10 minutes (Ref: ${pendingBooking.bookingReference}). Complete payment to confirm or cancel to release immediately.`
      );
    }
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
      setCustomerName(user.name || "");
      setCustomerEmail(user.email || "");
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
    setRefSearch("");
    setRefSearchResult(null);
    setRefSearchError("");
    setCustomerName("");
    setCustomerEmail("");
    setCustomerPhone("");
  };

  const handleOpenMyBookings = async () => {
    setSelectedTurf(null);
    setAuthModalOpen(false);
    setMyBookingsOpen(true);
    setMyBookingsLoading(true);
    setMyBookingsError("");
    setCancelError("");
    setCancelSuccess("");
    setRefSearchResult(null);
    setRefSearchError("");
    try {
      const data = await getMyBookings();
      setMyBookings(Array.isArray(data) ? data : []);
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

  const handleLookupReference = async () => {
    const trimmed = refSearch.trim();
    if (!trimmed) return;
    setRefSearchLoading(true);
    setRefSearchError("");
    setRefSearchResult(null);
    try {
      const data = await getBookingByReference(trimmed);
      setRefSearchResult(data);
    } catch (err) {
      const errorMsg =
        err.response?.status === 404
          ? `No reservation found with reference "${trimmed}". Please check the code.`
          : err.response?.data?.message || err.message || "Failed to find booking.";
      setRefSearchError(errorMsg);
    } finally {
      setRefSearchLoading(false);
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

      // Update search result card if that was the one cancelled
      if (refSearchResult && refSearchResult.bookingReference === updated.bookingReference) {
        setRefSearchResult((prev) => ({
          ...prev,
          status: "CANCELLED",
          cancelledAt: updated.cancelledAt
        }));
      }

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
    setPaymentNotice("");

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

      // 1. Create booking & Razorpay order (backend locks the slot atomically)
      const bookingData = await createBooking(payload);
      setPendingBooking(bookingData);

      // Check if we are running in Simulator Mode (placeholder credentials or simulated order)
      const isSimulator =
        paymentConfig?.isSimulator ||
        paymentConfig?.keyId?.includes("placeholder") ||
        bookingData.razorpayKeyId?.includes("placeholder") ||
        bookingData.razorpayOrderId?.startsWith("order_sim_") ||
        bookingData.razorpayOrderId?.startsWith("order_test_");

      if (isSimulator) {
        setIsSubmitting(false);
        setSimulatorModalOpen(true);
        return;
      }

      // 2. Load Razorpay Checkout SDK script for real keys
      const scriptLoaded = await loadRazorpayScript();
      if (!scriptLoaded) {
        setIsSubmitting(false);
        setSimulatorModalOpen(true);
        return;
      }

      // 3. Resolve keyId (backend config or order response keyId)
      const keyId =
        paymentConfig?.keyId ||
        bookingData.razorpayKeyId ||
        "rzp_test_mock_turfbook";

      // 4. Configure Razorpay Options
      const options = {
        key: keyId,
        amount: Math.round(Number(bookingData.totalPrice) * 100),
        currency: "INR",
        name: "TurfBook Sports",
        description: `Booking for ${selectedTurf.name} (${bookingDate} ${bookingTime})`,
        order_id: bookingData.razorpayOrderId,
        prefill: {
          name: customerName.trim(),
          email: customerEmail.trim(),
          contact: customerPhone.trim() || undefined,
        },
        theme: {
          color: "#059669",
        },
        modal: {
          ondismiss: () => {
            setIsSubmitting(false);
            setPaymentNotice(
              `Payment window active. Your slot is reserved for 10 minutes (Ref: ${bookingData.bookingReference}). Complete payment to confirm or cancel to release immediately.`
            );
          },
        },
        handler: async (response) => {
          setIsVerifyingPayment(true);
          try {
            const verified = await verifyPayment({
              bookingReference: bookingData.bookingReference,
              razorpayOrderId: response.razorpay_order_id || bookingData.razorpayOrderId,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
            });
            setConfirmedBooking(verified);
            setPendingBooking(null);
            setPaymentNotice("");
            setModalStep("success");
          } catch (verifyErr) {
            const errText =
              verifyErr.response?.data?.message ||
              verifyErr.response?.data?.error ||
              verifyErr.message ||
              "Payment verification failed. Please contact support.";
            setBookingError(errText);
          } finally {
            setIsVerifyingPayment(false);
            setIsSubmitting(false);
          }
        },
      };

      const razorpayInstance = new window.Razorpay(options);
      razorpayInstance.on("payment.failed", (failResponse) => {
        console.error("Razorpay payment failed:", failResponse.error);
        setBookingError(
          `Payment could not be completed: ${failResponse.error?.description || failResponse.error?.reason || "Transaction failed"}`
        );
        setIsSubmitting(false);
      });
      razorpayInstance.open();
    } catch (err) {
      const errorMsg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        err.message ||
        "Booking submission failed. Please try again.";
      setBookingError(errorMsg);
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
                <span className="user-name">
                  {currentUser.name ? currentUser.name.split(" ")[0] : (currentUser.email ? currentUser.email.split("@")[0] : "Player")}
                </span>
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

                {paymentNotice && (
                  <div className="booking-alert-notice" role="status">
                    <div className="notice-body">
                      <Clock size={16} />
                      <span>{paymentNotice}</span>
                    </div>
                    <div className="notice-actions-row">
                      {pendingBooking && (
                        <button
                          type="button"
                          className="resume-pay-inline-btn"
                          onClick={() => setSimulatorModalOpen(true)}
                        >
                          <CreditCard size={13} />
                          Pay Now
                        </button>
                      )}
                      <button
                        type="button"
                        className="release-slot-inline-btn"
                        onClick={handleCancelPendingReservation}
                      >
                        Release Slot
                      </button>
                    </div>
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
                  className="booking-button payment-submit-btn"
                  disabled={isSubmitting || isVerifyingPayment || bookedSlots.includes(bookingTime)}
                >
                  {isVerifyingPayment ? (
                    "Verifying Payment..."
                  ) : isSubmitting ? (
                    "Opening Razorpay Gateway..."
                  ) : (
                    <>
                      <CreditCard size={18} />
                      Proceed to Pay · ₹{Number(selectedTurf.pricePerHour * durationHours).toLocaleString("en-IN")}
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
                  <div className="summary-row">
                    <span>Payment Status</span>
                    <strong className="payment-status-paid">
                      <ShieldCheck size={14} style={{ display: "inline", verticalAlign: "middle", marginRight: 4 }} />
                      PAID (Razorpay)
                    </strong>
                  </div>
                  {confirmedBooking.razorpayPaymentId && (
                    <div className="summary-row">
                      <span>Payment ID</span>
                      <code className="payment-ref-code">{confirmedBooking.razorpayPaymentId}</code>
                    </div>
                  )}
                  <div className="summary-row total-row">
                    <span>Total Paid</span>
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
              Your confirmed ground reservations for <strong>{currentUser?.name || currentUser?.email || "your account"}</strong>
            </p>

            {/* Reference Lookup Section for checking any reservation or guest checkout */}
            <div className="booking-lookup-panel">
              <span className="booking-lookup-label">Have a booking reference from guest checkout?</span>
              <div className="booking-lookup-input-group">
                <input
                  type="text"
                  placeholder="Enter reference e.g. TB-261014-A3F2"
                  value={refSearch}
                  onChange={(e) => setRefSearch(e.target.value.toUpperCase())}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      e.preventDefault();
                      handleLookupReference();
                    }
                  }}
                  aria-label="Search reservation by reference"
                />
                <button
                  type="button"
                  className="booking-lookup-btn"
                  onClick={handleLookupReference}
                  disabled={refSearchLoading || !refSearch.trim()}
                >
                  {refSearchLoading ? "Checking..." : "Look Up"}
                </button>
              </div>

              {refSearchError && (
                <div className="booking-alert-error lookup-alert" role="alert">
                  <AlertCircle size={15} />
                  <span>{refSearchError}</span>
                </div>
              )}

              {refSearchResult && (
                <article className={`my-booking-card lookup-result-card ${refSearchResult.status?.toLowerCase()}`}>
                  <div className="my-booking-header">
                    <div>
                      <h4>{refSearchResult.turfName}</h4>
                      <span className="booking-ref-tag">{refSearchResult.bookingReference}</span>
                    </div>
                    <div className="status-badges-group">
                      <span className={`status-pill ${refSearchResult.status?.toLowerCase()}`}>
                        {refSearchResult.status}
                      </span>
                      {refSearchResult.paymentStatus && (
                        <span className={`payment-pill ${refSearchResult.paymentStatus.toLowerCase()}`}>
                          {refSearchResult.paymentStatus}
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="my-booking-details">
                    <p>
                      <Calendar size={14} />
                      <span>{refSearchResult.bookingDate}</span>
                    </p>
                    <p>
                      <Clock size={14} />
                      <span>{refSearchResult.startTime} – {refSearchResult.endTime} ({refSearchResult.durationHours} hr{refSearchResult.durationHours > 1 ? "s" : ""})</span>
                    </p>
                    <p className="my-booking-price">
                      Total: <strong>₹{Number(refSearchResult.totalPrice).toLocaleString("en-IN")}</strong>
                    </p>
                  </div>

                  <div className="my-booking-footer">
                    {isBookingCancellable(refSearchResult) ? (
                      <button
                        type="button"
                        className="cancel-booking-btn"
                        onClick={() => handleInitiateCancel(refSearchResult)}
                        aria-label={`Cancel reservation ${refSearchResult.bookingReference}`}
                      >
                        <XCircle size={14} />
                        <span>Cancel Reservation</span>
                      </button>
                    ) : refSearchResult.status === "CONFIRMED" ? (
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
              )}
            </div>

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
                      No reservations found linked to your account yet.
                    </p>
                    <p className="empty-state-sub">
                      Note: Bookings made before signing in (guest checkouts) are saved with <code>customerId = null</code> and do not appear under your account history. You can look them up by reference code above.
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
                          <div className="status-badges-group">
                            <span className={`status-pill ${b.status?.toLowerCase()}`}>
                              {b.status}
                            </span>
                            {b.paymentStatus && (
                              <span className={`payment-pill ${b.paymentStatus.toLowerCase()}`}>
                                {b.paymentStatus}
                              </span>
                            )}
                            {b.refundStatus && b.refundStatus !== "NOT_APPLICABLE" && (
                              <span className={`refund-pill ${b.refundStatus.toLowerCase()}`}>
                                Refund: {b.refundStatus}
                              </span>
                            )}
                          </div>
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
          </section>
        </div>
      )}

      {/* Cancellation Confirmation Dialog Overlay (top-level fixed dialog) */}
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

              {(cancellingBooking.paymentStatus === "SUCCESS" || cancellingBooking.paymentStatus === "PAID") && (
                <div className="cancellation-refund-notice">
                  <ShieldCheck size={16} />
                  <span>
                    A full refund of <strong>₹{Number(cancellingBooking.totalPrice).toLocaleString("en-IN")}</strong> will be automatically initiated to your original payment method via Razorpay.
                  </span>
                </div>
              )}

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

      {/* Razorpay Test Sandbox Interactive Simulator Modal */}
      {simulatorModalOpen && pendingBooking && (
        <div className="turf-modal-backdrop" onClick={handleSimulatorDismiss}>
          <section
            className="turf-modal razorpay-sim-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="sim-modal-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="rzp-sim-header">
              <div className="rzp-sim-brand">
                <div className="rzp-badge-icon">
                  <CreditCard size={20} />
                </div>
                <div>
                  <div className="rzp-sim-title-row">
                    <strong id="sim-modal-title">Razorpay Test Checkout</strong>
                    <span className="rzp-test-pill">TEST SANDBOX</span>
                  </div>
                  <span className="rzp-sim-sub">TurfBook Sports · Instant Simulator</span>
                </div>
              </div>
              <button
                className="modal-close"
                onClick={handleSimulatorDismiss}
                aria-label="Close payment modal"
              >
                ×
              </button>
            </div>

            <div className="rzp-sim-amount-banner">
              <span className="rzp-amount-label">AMOUNT PAYABLE</span>
              <strong className="rzp-amount-value">
                ₹{Number(pendingBooking.totalPrice).toLocaleString("en-IN")}
              </strong>
              <div className="rzp-order-info">
                <span>Order: <code>{pendingBooking.razorpayOrderId}</code></span>
                <span>Ref: <strong>{pendingBooking.bookingReference}</strong></span>
              </div>
            </div>

            <div className="rzp-sim-methods">
              <span className="rzp-section-heading">Select Payment Method:</span>
              <div className="rzp-methods-grid">
                <button
                  type="button"
                  className={`rzp-method-card ${simulatorPaymentMethod === "card" ? "active" : ""}`}
                  onClick={() => setSimulatorPaymentMethod("card")}
                >
                  <CreditCard size={18} className="rzp-method-icon" />
                  <div className="rzp-method-text">
                    <strong>Test Card</strong>
                    <span>4000 •••• •••• 0002</span>
                  </div>
                </button>

                <button
                  type="button"
                  className={`rzp-method-card ${simulatorPaymentMethod === "upi" ? "active" : ""}`}
                  onClick={() => setSimulatorPaymentMethod("upi")}
                >
                  <Smartphone size={18} className="rzp-method-icon" />
                  <div className="rzp-method-text">
                    <strong>Test UPI</strong>
                    <span>player@razorpay</span>
                  </div>
                </button>

                <button
                  type="button"
                  className={`rzp-method-card ${simulatorPaymentMethod === "netbanking" ? "active" : ""}`}
                  onClick={() => setSimulatorPaymentMethod("netbanking")}
                >
                  <Building2 size={18} className="rzp-method-icon" />
                  <div className="rzp-method-text">
                    <strong>NetBanking</strong>
                    <span>HDFC / SBI / ICICI</span>
                  </div>
                </button>
              </div>
            </div>

            <div className="rzp-sim-helper-note">
              <ShieldCheck size={16} />
              <span>
                Simulated Sandbox Mode: No money is deducted. Completes authoritative server-side verification and confirms your reservation slot.
              </span>
            </div>

            <div className="rzp-sim-actions">
              <button
                type="button"
                className="rzp-pay-success-btn"
                onClick={handleSimulatorSuccess}
                disabled={isVerifyingPayment}
              >
                {isVerifyingPayment ? (
                  "Verifying Payment with Server..."
                ) : (
                  <>
                    <CheckCircle2 size={18} />
                    Complete Test Payment · ₹{Number(pendingBooking.totalPrice).toLocaleString("en-IN")}
                  </>
                )}
              </button>

              <div className="rzp-secondary-actions">
                <button
                  type="button"
                  className="rzp-pay-fail-btn"
                  onClick={handleSimulatorFailure}
                  disabled={isVerifyingPayment}
                >
                  Simulate Payment Failure
                </button>
                <button
                  type="button"
                  className="rzp-dismiss-btn"
                  onClick={handleSimulatorDismiss}
                  disabled={isVerifyingPayment}
                >
                  Cancel / Close
                </button>
              </div>
            </div>
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
