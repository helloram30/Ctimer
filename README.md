# Ctimer

A simple room-based chess timer web app with:

- Java (Spring Boot) backend with WebSocket updates
- React + TypeScript frontend
- Backend-authoritative room and clock logic

## Project Structure

- `backend/`: Gradle Spring Boot service
- `frontend/`: React + TypeScript client (Vite)

## Room Flow

1. Player 1 creates a room by selecting:
   - white initial minutes
   - black initial minutes
   - white increment seconds
   - black increment seconds
   - preferred color (White or Black)
2. Backend generates a 6-character room code.
3. Player 2 joins using that code and is assigned the remaining color.
4. When both players have joined, Black presses `Start`.
5. Timer begins with White running first.
6. If a device refreshes, the player auto-reconnects to the same room and color.
7. Either player can end the game with `Game Over`, then create a new room.

Both players always see both clocks. Only the active player can press their own clock.

## API Summary

- `POST /api/rooms/create`
- `POST /api/rooms/join`
- `POST /api/rooms/start`
- `POST /api/rooms/reconnect`
- `POST /api/rooms/game-over`
- `GET /api/rooms/{roomCode}/state`
- STOMP publish: `/app/clock.press`
- STOMP subscribe: `/topic/room/{roomCode}/clock`

## Run Backend

```bash
cd backend
./gradlew bootRun
```

Backend runs on `http://localhost:8080`.

If startup fails with `Port 8080 was already in use`, either stop the process using that port or run the backend on a different port:

```bash
cd backend
./gradlew bootRun --args='--server.port=8081'
```

## Run Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on `http://localhost:5173`.

Open `http://localhost:5173` in the browser for the UI.
Do not open [frontend/index.html](/Users/ram/Documents/VS_Code/Ctimer/frontend/index.html) directly from the filesystem, and do not expect the UI on `http://localhost:8080`; port `8080` is the backend API/WebSocket server only.
