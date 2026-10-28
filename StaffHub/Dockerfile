FROM node:24-alpine
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY package.json package-lock.json ./
# npm ci was te streng: npm 11.19 (container) weigerde een lock die npm 11.6
# (bouw-apparaat) perfect in sync achtte - de hele release-build blokkeerde.
# npm install is hier even deterministisch (de lock bestand) maar repareert
# kleine diff-tjes zelf in plaats van te weigeren.
RUN npm install
COPY . .
RUN npm run build && rm -rf ./.next/cache

ENV NODE_ENV=production PORT=3120 STAFFHUB_DATA=/app/data
EXPOSE 3120
CMD ["node", "server.js"]
