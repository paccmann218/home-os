# HomeOS backend + dashboard. Build context is the repo root (see docker-compose.yml).
FROM node:24-slim AS ui
WORKDIR /app/ui
COPY ui/package*.json ./
RUN npm ci
COPY ui/ ./
RUN npm run build

FROM node:24-slim
WORKDIR /app/backend
COPY backend/package*.json ./
RUN npm ci
COPY backend/ ./
COPY graph/ /app/graph/
COPY replay/ /app/replay/
COPY --from=ui /app/ui/dist /app/ui/dist
ENV PORT=3001
EXPOSE 3001
CMD ["npx", "tsx", "src/index.ts"]
