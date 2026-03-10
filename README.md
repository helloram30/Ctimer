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
   - initial minutes
   - increment seconds
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
