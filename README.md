# Ctimer

A simple chess timer web app with:

- Java (Spring Boot) backend with WebSocket updates
- React + TypeScript frontend
- Backend-authoritative clock switching logic

## Project Structure

- `backend/`: Gradle Spring Boot service
- `frontend/`: React + TypeScript client (Vite)

## How It Works

- Both clocks are always visible to all players.
- Only the active player's clock counts down.
- When the active player clicks their clock:
  - elapsed time is deducted from their remaining time
  - increment is added (if configured)
  - active turn switches to the opponent
- Backend is the source of truth and pushes updates over WebSocket.

## Run Backend

```bash
cd backend
gradle bootRun
```

Backend runs on `http://localhost:8080`.

## Run Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on `http://localhost:5173`.

## Default Game Settings

- Initial time: 5 minutes each side
- Increment: 0 seconds

You can reset the game from the UI with custom initial minutes and increment seconds.
