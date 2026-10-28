# Boord-AI: opencode in een eigen sandbox-container.
# - géén host-poort: alleen bereikbaar via het docker-netwerk staffhub_default
# - bash + edit staan in de config op deny: lezen/zoeken only
# - model-login: één keer `docker exec -it opencode-ai opencode auth login`
FROM node:24-bookworm-slim

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates git ripgrep \
 && rm -rf /var/lib/apt/lists/*

RUN npm install -g opencode-ai

WORKDIR /app
ENV EDITOR=vi
CMD ["opencode", "serve", "--hostname", "0.0.0.0", "--port", "4096"]
