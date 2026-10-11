
import { useState } from "react";
import {
  MapPin,
  Search,
  Clock,
  ArrowRight
} from "lucide-react";
import ApiStatusBadge from "./components/ApiStatusBadge";
import "./App.css";

const turfs = [
  {
    id: 1,
    name: "Blue Arena",
    location: "Mumbai",
    sport: "Football",
    price: 1200,
    hours: "6 AM – 11 PM",
    color: "blue",
    emoji: "⚽",
  },
  {
    id: 2,
    name: "Green Strikers",
    location: "Thane",
    sport: "Football",
    price: 1500,
    hours: "7 AM – 10 PM",
    color: "green",
    emoji: "🥅",
  },
  {
    id: 3,
    name: "Power Play",
    location: "Navi Mumbai",
    sport: "Cricket",
    price: 1000,
    hours: "6 AM – 10 PM",
    color: "orange",
    emoji: "🏏",
  },
];

function App() {
  const [search, setSearch] = useState("");
  const [sport, setSport] = useState("All");

  const filteredTurfs = turfs.filter((turf) => {
    const matchesSearch =
      `${turf.name} ${turf.location}`
        .toLowerCase()
        .includes(search.toLowerCase());

    const matchesSport =
      sport === "All" || turf.sport === sport;

    return matchesSearch && matchesSport;
  });

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

          <div className="turf-grid">
            {filteredTurfs.map((turf) => (
              <article className="turf-card" key={turf.id}>
                <div className={`turf-image ${turf.color}`}>
                  <span className="sport-emoji">
                    {turf.emoji}
                  </span>
                  <span className="sample-tag">
                    Sample listing
                  </span>
                </div>

                <div className="turf-info">
                  <div className="turf-title">
                    <h3>{turf.name}</h3>
                    <span className="sport-tag">
                      {turf.sport}
                    </span>
                  </div>

                  <p className="location">
                    <MapPin size={16} />
                    {turf.location}
                  </p>

                  <p className="location">
                    <Clock size={16} />
                    {turf.hours}
                  </p>

                  <div className="card-footer">
                    <p>
                      <strong>₹{turf.price.toLocaleString("en-IN")}</strong>
                      <span> / hour</span>
                    </p>

                    <button
                      aria-label={`View ${turf.name}`}
                      onClick={() =>
                        alert(
                          "Turf details and booking will be added in the next phase."
                        )
                      }
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
              No matching sample turfs. Try another search.
            </p>
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
