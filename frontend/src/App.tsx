import { Client, IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useEffect, useMemo, useRef, useState } from "react";

type PlayerColor = "WHITE" | "BLACK";

type RoomSnapshot = {
  roomCode: string;
  whiteRemainingMs: number;
  blackRemainingMs: number;
  activePlayer: PlayerColor;
  lastSwitchEpochMs: number;
  running: boolean;
  gameOver: boolean;
  readyToStart: boolean;
  incrementMs: number;
  whiteJoined: boolean;
  blackJoined: boolean;
  statusMessage: string;
};

type RoomSessionResponse = {
  roomCode: string;
  assignedColor: PlayerColor;
  snapshot: RoomSnapshot;
};

type DisplayTimes = {
  white: number;
  black: number;
};

const API_BASE_URL = "http://localhost:8080";
const STORAGE_ROOM_CODE_KEY = "ctimer_room_code";
const STORAGE_PLAYER_COLOR_KEY = "ctimer_player_color";

/**
 * Calculates display times using the latest snapshot and local current time.
 */
function calculateDisplayTimes(snapshot: RoomSnapshot, nowMs: number): DisplayTimes {
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
 * Reads API error text from a failed HTTP response.
 */
async function readErrorMessage(response: Response): Promise<string> {
  try {
    const payload = (await response.json()) as { message?: string };
    return payload.message ?? "Request failed.";
  } catch {
    return "Request failed.";
  }
}

/**
 * Creates a room with selected time controls and host color.
 */
async function createRoom(initialMinutes: number, incrementSeconds: number, hostColor: PlayerColor): Promise<RoomSessionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/create`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ initialMinutes, incrementSeconds, hostColor })
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  return (await response.json()) as RoomSessionResponse;
}

/**
 * Joins a room by code.
 */
async function joinRoom(roomCode: string): Promise<RoomSessionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/join`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ roomCode })
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  return (await response.json()) as RoomSessionResponse;
}

/**
 * Reconnects a client to a room using its previous color.
 */
async function reconnectRoom(roomCode: string, player: PlayerColor): Promise<RoomSessionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/reconnect`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ roomCode, player })
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  return (await response.json()) as RoomSessionResponse;
}

/**
 * Starts a room game from the black side.
 */
async function startGame(roomCode: string, player: PlayerColor): Promise<RoomSnapshot> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/start`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ roomCode, player })
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  return (await response.json()) as RoomSnapshot;
}

/**
 * Ends a room game manually.
 */
async function gameOver(roomCode: string, player: PlayerColor): Promise<RoomSnapshot> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/game-over`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ roomCode, player })
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  return (await response.json()) as RoomSnapshot;
}

/**
 * Fetches the latest room state.
 */
async function fetchRoomState(roomCode: string): Promise<RoomSnapshot> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/${roomCode}/state`);
  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
  return (await response.json()) as RoomSnapshot;
}

/**
 * Renders room setup and in-room chess timer interactions.
 */
export default function App(): JSX.Element {
  const [snapshot, setSnapshot] = useState<RoomSnapshot | null>(null);
  const [nowMs, setNowMs] = useState<number>(Date.now());
  const [myColor, setMyColor] = useState<PlayerColor | null>(null);
  const [roomCode, setRoomCode] = useState<string>("");
  const [createMinutes, setCreateMinutes] = useState<number>(5);
  const [createIncrement, setCreateIncrement] = useState<number>(0);
  const [createColor, setCreateColor] = useState<PlayerColor>("WHITE");
  const [joinCode, setJoinCode] = useState<string>("");
  const [errorMessage, setErrorMessage] = useState<string>("");
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    const storedRoomCode = localStorage.getItem(STORAGE_ROOM_CODE_KEY);
    const storedColor = localStorage.getItem(STORAGE_PLAYER_COLOR_KEY);
    if (!storedRoomCode || !storedColor) {
      return;
    }

    const normalizedColor = storedColor === "WHITE" || storedColor === "BLACK" ? storedColor : null;
    if (!normalizedColor) {
      localStorage.removeItem(STORAGE_ROOM_CODE_KEY);
      localStorage.removeItem(STORAGE_PLAYER_COLOR_KEY);
      return;
    }

    reconnectRoom(storedRoomCode, normalizedColor)
      .then((response) => {
        setRoomCode(response.roomCode);
        setMyColor(response.assignedColor);
        setSnapshot(response.snapshot);
      })
      .catch(() => {
        localStorage.removeItem(STORAGE_ROOM_CODE_KEY);
        localStorage.removeItem(STORAGE_PLAYER_COLOR_KEY);
      });
  }, []);

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      setNowMs(Date.now());
    }, 100);

    return () => {
      window.clearInterval(intervalId);
    };
  }, []);

  useEffect(() => {
    if (!roomCode) {
      return;
    }

    let isMounted = true;
    fetchRoomState(roomCode)
      .then((state) => {
        if (isMounted) {
          setSnapshot(state);
        }
      })
      .catch((error: Error) => {
        if (isMounted) {
          setErrorMessage(error.message);
        }
      });

    const stompClient = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE_URL}/ws`),
      reconnectDelay: 2000,
      onConnect: () => {
        stompClient.subscribe(`/topic/room/${roomCode}/clock`, (message: IMessage) => {
          const payload = JSON.parse(message.body) as RoomSnapshot;
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
  }, [roomCode]);

  const displayTimes = useMemo<DisplayTimes>(() => {
    if (!snapshot) {
      return { white: 0, black: 0 };
    }
    return calculateDisplayTimes(snapshot, nowMs);
  }, [snapshot, nowMs]);

  const handleCreateRoom = async (): Promise<void> => {
    try {
      const response = await createRoom(createMinutes, createIncrement, createColor);
      setRoomCode(response.roomCode);
      setMyColor(response.assignedColor);
      setSnapshot(response.snapshot);
      localStorage.setItem(STORAGE_ROOM_CODE_KEY, response.roomCode);
      localStorage.setItem(STORAGE_PLAYER_COLOR_KEY, response.assignedColor);
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  };

  const handleJoinRoom = async (): Promise<void> => {
    try {
      const normalized = joinCode.trim().toUpperCase();
      const response = await joinRoom(normalized);
      setRoomCode(response.roomCode);
      setMyColor(response.assignedColor);
      setSnapshot(response.snapshot);
      localStorage.setItem(STORAGE_ROOM_CODE_KEY, response.roomCode);
      localStorage.setItem(STORAGE_PLAYER_COLOR_KEY, response.assignedColor);
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  };

  const handleStart = async (): Promise<void> => {
    if (!roomCode || !myColor) {
      return;
    }

    try {
      const updated = await startGame(roomCode, myColor);
      setSnapshot(updated);
      setNowMs(Date.now());
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  };

  const handleClockPress = (player: PlayerColor): void => {
    if (!roomCode) {
      return;
    }

    if (!clientRef.current?.connected) {
      setErrorMessage("WebSocket is not connected.");
      return;
    }

    clientRef.current.publish({
      destination: "/app/clock.press",
      body: JSON.stringify({ roomCode, player })
    });
  };

  const handleGameOver = async (): Promise<void> => {
    if (!roomCode || !myColor) {
      return;
    }
    try {
      const updated = await gameOver(roomCode, myColor);
      setSnapshot(updated);
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  };

  const handleNewRoom = (): void => {
    localStorage.removeItem(STORAGE_ROOM_CODE_KEY);
    localStorage.removeItem(STORAGE_PLAYER_COLOR_KEY);
    setSnapshot(null);
    setMyColor(null);
    setRoomCode("");
    setJoinCode("");
    setErrorMessage("");
  };

  if (!roomCode || !myColor || !snapshot) {
    return (
      <main className="page">
        <h1>Chess Timer Rooms</h1>

        <section className="panel">
          <h2>Create Game</h2>
          <div className="row">
            <label>
              Minutes
              <input
                type="number"
                min={1}
                value={createMinutes}
                onChange={(event) => setCreateMinutes(Number(event.target.value))}
              />
            </label>

            <label>
              Increment (sec)
              <input
                type="number"
                min={0}
                value={createIncrement}
                onChange={(event) => setCreateIncrement(Number(event.target.value))}
              />
            </label>

            <label>
              Your Color
              <select value={createColor} onChange={(event) => setCreateColor(event.target.value as PlayerColor)}>
                <option value="WHITE">White</option>
                <option value="BLACK">Black</option>
              </select>
            </label>
          </div>
          <button onClick={handleCreateRoom}>Create Room</button>
        </section>

        <section className="panel">
          <h2>Join Game</h2>
          <div className="row">
            <label>
              Room Code
              <input
                value={joinCode}
                onChange={(event) => setJoinCode(event.target.value.toUpperCase())}
                placeholder="ABC123"
              />
            </label>
          </div>
          <button onClick={handleJoinRoom}>Join Room</button>
        </section>

        {errorMessage ? <p className="error">{errorMessage}</p> : null}
      </main>
    );
  }

  return (
    <main className="page">
      <header className="room-header">
        <h1>Room {roomCode}</h1>
        <p>You are {myColor}</p>
      </header>

      <section className="clock-grid">
        <button
          className={`clock-card ${snapshot.activePlayer === "WHITE" ? "active" : ""}`}
          onClick={() => handleClockPress("WHITE")}
          disabled={!snapshot.running || snapshot.activePlayer !== "WHITE" || myColor !== "WHITE"}
        >
          <span className="label">WHITE</span>
          <span className="time">{formatTime(displayTimes.white)}</span>
        </button>

        <button
          className={`clock-card ${snapshot.activePlayer === "BLACK" ? "active" : ""}`}
          onClick={() => handleClockPress("BLACK")}
          disabled={!snapshot.running || snapshot.activePlayer !== "BLACK" || myColor !== "BLACK"}
        >
          <span className="label">BLACK</span>
          <span className="time">{formatTime(displayTimes.black)}</span>
        </button>
      </section>

      <section className="panel">
        <p>{snapshot.statusMessage}</p>
        <p>
          Players: White {snapshot.whiteJoined ? "joined" : "waiting"} | Black {snapshot.blackJoined ? "joined" : "waiting"}
        </p>
        <button onClick={handleStart} disabled={!snapshot.readyToStart || myColor !== "BLACK" || snapshot.gameOver}>
          Start (Black)
        </button>
        <button onClick={handleGameOver} disabled={!snapshot.running || snapshot.gameOver}>
          Game Over
        </button>
        {snapshot.gameOver ? <button onClick={handleNewRoom}>New Room</button> : null}
      </section>

      {errorMessage ? <p className="error">{errorMessage}</p> : null}
    </main>
  );
}
