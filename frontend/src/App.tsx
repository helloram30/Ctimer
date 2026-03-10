import { Client, IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useEffect, useMemo, useRef, useState } from "react";

type PlayerColor = "WHITE" | "BLACK";

type ClockSnapshot = {
  whiteRemainingMs: number;
  blackRemainingMs: number;
  activePlayer: PlayerColor;
  lastSwitchEpochMs: number;
  running: boolean;
  incrementMs: number;
  statusMessage: string;
};

type DisplayTimes = {
  white: number;
  black: number;
};

const API_BASE_URL = "http://localhost:8080";

/**
 * Calculates display times using the latest snapshot and local current time.
 */
function calculateDisplayTimes(snapshot: ClockSnapshot, nowMs: number): DisplayTimes {
  if (!snapshot.running) {
    return {
      white: Math.max(snapshot.whiteRemainingMs, 0),
      black: Math.max(snapshot.blackRemainingMs, 0)
    };
  }

  const elapsed = Math.max(0, nowMs - snapshot.lastSwitchEpochMs);
  if (snapshot.activePlayer === "WHITE") {
    return {
      white: Math.max(snapshot.whiteRemainingMs - elapsed, 0),
      black: Math.max(snapshot.blackRemainingMs, 0)
    };
  }

  return {
    white: Math.max(snapshot.whiteRemainingMs, 0),
    black: Math.max(snapshot.blackRemainingMs - elapsed, 0)
  };
}

/**
 * Converts milliseconds to MM:SS.T format.
 */
function formatTime(totalMs: number): string {
  const clamped = Math.max(0, totalMs);
  const totalSeconds = Math.floor(clamped / 1000);
  const minutes = Math.floor(totalSeconds / 60)
    .toString()
    .padStart(2, "0");
  const seconds = (totalSeconds % 60).toString().padStart(2, "0");
  const tenths = Math.floor((clamped % 1000) / 100);
  return `${minutes}:${seconds}.${tenths}`;
}

/**
 * Sends a reset request to the backend.
 */
async function resetClock(initialMinutes: number, incrementSeconds: number): Promise<ClockSnapshot> {
  const response = await fetch(`${API_BASE_URL}/api/clock/reset`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ initialMinutes, incrementSeconds })
  });

  if (!response.ok) {
    throw new Error("Reset request failed.");
  }

  return (await response.json()) as ClockSnapshot;
}

/**
 * Fetches the current authoritative clock state.
 */
async function fetchClockState(): Promise<ClockSnapshot> {
  const response = await fetch(`${API_BASE_URL}/api/clock/state`);
  if (!response.ok) {
    throw new Error("Failed to fetch clock state.");
  }
  return (await response.json()) as ClockSnapshot;
}

/**
 * Renders a minimal chess timer UI backed by WebSocket updates.
 */
export default function App(): JSX.Element {
  const [snapshot, setSnapshot] = useState<ClockSnapshot | null>(null);
  const [nowMs, setNowMs] = useState<number>(Date.now());
  const [initialMinutes, setInitialMinutes] = useState<number>(5);
  const [incrementSeconds, setIncrementSeconds] = useState<number>(0);
  const [errorMessage, setErrorMessage] = useState<string>("");
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      setNowMs(Date.now());
    }, 100);

    return () => {
      window.clearInterval(intervalId);
    };
  }, []);

  useEffect(() => {
    let isMounted = true;

    fetchClockState()
      .then((state) => {
        if (isMounted) {
          setSnapshot(state);
        }
      })
      .catch(() => {
        if (isMounted) {
          setErrorMessage("Could not load initial clock state.");
        }
      });

    const stompClient = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE_URL}/ws`),
      reconnectDelay: 2000,
      onConnect: () => {
        stompClient.subscribe("/topic/clock", (message: IMessage) => {
          const payload = JSON.parse(message.body) as ClockSnapshot;
          setSnapshot(payload);
          setNowMs(Date.now());
          setErrorMessage("");
        });
      },
      onStompError: () => {
        setErrorMessage("WebSocket error occurred.");
      }
    });

    stompClient.activate();
    clientRef.current = stompClient;

    return () => {
      isMounted = false;
      clientRef.current?.deactivate();
      clientRef.current = null;
    };
  }, []);

  const displayTimes = useMemo<DisplayTimes>(() => {
    if (!snapshot) {
      return { white: 0, black: 0 };
    }
    return calculateDisplayTimes(snapshot, nowMs);
  }, [snapshot, nowMs]);

  const handleClockPress = (player: PlayerColor): void => {
    if (!clientRef.current?.connected) {
      setErrorMessage("WebSocket is not connected.");
      return;
    }

    clientRef.current.publish({
      destination: "/app/clock.press",
      body: JSON.stringify({ player })
    });
  };

  const handleReset = async (): Promise<void> => {
    try {
      const updated = await resetClock(initialMinutes, incrementSeconds);
      setSnapshot(updated);
      setNowMs(Date.now());
      setErrorMessage("");
    } catch {
      setErrorMessage("Reset failed.");
    }
  };

  return (
    <main className="page">
      <section className="clock-grid">
        <button
          className={`clock-card ${snapshot?.activePlayer === "WHITE" ? "active" : ""}`}
          onClick={() => handleClockPress("WHITE")}
          disabled={!snapshot || !snapshot.running || snapshot.activePlayer !== "WHITE"}
        >
          <span className="label">WHITE</span>
          <span className="time">{formatTime(displayTimes.white)}</span>
        </button>

        <button
          className={`clock-card ${snapshot?.activePlayer === "BLACK" ? "active" : ""}`}
          onClick={() => handleClockPress("BLACK")}
          disabled={!snapshot || !snapshot.running || snapshot.activePlayer !== "BLACK"}
        >
          <span className="label">BLACK</span>
          <span className="time">{formatTime(displayTimes.black)}</span>
        </button>
      </section>

      <section className="controls">
        <label>
          Initial Minutes
          <input
            type="number"
            min={1}
            value={initialMinutes}
            onChange={(event) => setInitialMinutes(Number(event.target.value))}
          />
        </label>

        <label>
          Increment (Seconds)
          <input
            type="number"
            min={0}
            value={incrementSeconds}
            onChange={(event) => setIncrementSeconds(Number(event.target.value))}
          />
        </label>

        <button className="reset" onClick={handleReset}>
          Reset Game
        </button>
      </section>

      <section className="status">
        <p>{snapshot?.statusMessage ?? "Loading..."}</p>
        {errorMessage ? <p className="error">{errorMessage}</p> : null}
      </section>
    </main>
  );
}
