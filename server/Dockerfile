FROM node:20-alpine

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci --only=production

# Copy server code
COPY server.js ./

# Expose port
EXPOSE 8999

# Set environment variables
ENV NODE_ENV=production
ENV PORT=8999

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8999/ || exit 1

# Start server
CMD ["node", "server.js"]
