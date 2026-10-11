import { useState, useEffect } from "react";
import { RefreshCw, CheckCircle2, AlertCircle } from "lucide-react";
import { checkHealth } from "../services/api";

export default function ApiStatusBadge() {
  const [status, setStatus] = useState("loading"); // 'loading' | 'online' | 'offline'
  const [data, setData] = useState(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [isRefreshing, setIsRefreshing] = useState(false);

  // Initial check on mount
  useEffect(() => {
    let isMounted = true;

    async function verifyBackend() {
      try {
        const result = await checkHealth();
        if (isMounted) {
          setData(result);
          setStatus("online");
          setErrorMessage("");
        }
      } catch (err) {
        if (isMounted) {
          setStatus("offline");
          setData(null);
          if (err.code === "ERR_NETWORK") {
            setErrorMessage("Cannot reach backend at http://localhost:8080 (ERR_NETWORK)");
          } else if (err.response) {
            setErrorMessage(`Server error: HTTP ${err.response.status}`);
          } else {
            setErrorMessage(err.message || "Failed to connect to backend");
          }
        }
      }
    }

    verifyBackend();

    return () => {
      isMounted = false;
    };
  }, []);

  // Manual retry triggered by user clicking the refresh button
  const handleRefresh = async () => {
    setIsRefreshing(true);
    try {
      const result = await checkHealth();
      setData(result);
      setStatus("online");
      setErrorMessage("");
    } catch (err) {
      setStatus("offline");
      setData(null);
      if (err.code === "ERR_NETWORK") {
        setErrorMessage("Cannot reach backend at http://localhost:8080 (ERR_NETWORK)");
      } else if (err.response) {
        setErrorMessage(`Server error: HTTP ${err.response.status}`);
      } else {
        setErrorMessage(err.message || "Failed to connect to backend");
      }
    } finally {
      setIsRefreshing(false);
    }
  };

  return (
    <div
      className={`api-status-badge ${status}`}
      title={errorMessage || `API Status: ${status} (http://localhost:8080/api/health)`}
    >
      <span className="api-status-icon-wrapper">
        {status === "loading" && <RefreshCw size={13} className="spin-icon" />}
        {status === "online" && <CheckCircle2 size={13} className="icon-online" />}
        {status === "offline" && <AlertCircle size={13} className="icon-offline" />}
      </span>

      <span className="api-status-text">
        {status === "loading" && "Connecting to API..."}
        {status === "online" && `${data?.application || "TurfBook Backend"} (${data?.status || "UP"})`}
        {status === "offline" && "Backend Offline"}
      </span>

      <button
        type="button"
        className="api-refresh-btn"
        onClick={handleRefresh}
        disabled={isRefreshing}
        aria-label="Retry backend connection"
        title="Check status again"
      >
        <RefreshCw size={12} className={isRefreshing ? "spin-icon" : ""} />
      </button>
    </div>
  );
}
