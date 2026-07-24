# Production image for InvoiceGenie web console
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ENV NEXT_TELEMETRY_DISABLED=1
# BACKEND_URL is read at runtime for rewrites; build still needs a default
ENV BACKEND_URL=http://app:8080
# NEXT_PUBLIC_* are inlined at build time — prod image must not allow free tenant spoofing
ENV NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE=false
ENV NEXT_PUBLIC_APP_NAME=InvoiceGenie AR
# Do not bake demo API keys into the image
ENV NEXT_PUBLIC_API_KEY=
RUN npm run build

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
COPY --from=builder /app/public ./public
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
EXPOSE 3000
CMD ["node", "server.js"]
