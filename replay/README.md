# Replay mode

Judges can run HomeOS without a watch, ring or phone. The backend records every real input
(watch heart rate, chips, spoken questions after speech-to-text, Health Connect batches from the
ring) to `replay/recordings/<date>.jsonl` when `HOMEOS_RECORD=on`.

```bash
docker compose up -d --build           # FalkorDB + HomeOS backend + dashboard on :3001
cd backend && npm ci
npx tsx scripts/replay.ts ../replay/recordings/<recording>.jsonl --speed 2
```

Open http://localhost:3001 to watch the agents decide, coordinate through the graph, learn and
explain, exactly as in the demo video. The recordings contain only real data captured from the
team's devices during the event; nothing is synthesised.
