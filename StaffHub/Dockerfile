FROM node:24-alpine
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build && rm -rf ./.next/cache

ENV NODE_ENV=production PORT=3120 STAFFHUB_DATA=/app/data
EXPOSE 3120
CMD ["node", "server.js"]
