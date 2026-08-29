# Build stage
FROM node:18-alpine AS build

WORKDIR /app

ARG REACT_APP_VAPID_PUBLIC_KEY
ENV REACT_APP_VAPID_PUBLIC_KEY=$REACT_APP_VAPID_PUBLIC_KEY

# Memory-safe build on small instances:
#   GENERATE_SOURCEMAP=false  -> webpack skips source maps (roughly halves peak RAM)
#   NODE_OPTIONS max-old-space-size=1024 -> hard cap so Node stays inside the
#                                           instance's RAM instead of swapping to
#                                           disk (swapping is what turns a 3-min
#                                           build into 13 min). 512 is tuned for a
#                                           1 GB box with the backend stopped during
#                                           the build; raise if the box has more RAM.
ENV GENERATE_SOURCEMAP=false
ENV NODE_OPTIONS=--max-old-space-size=512

# Deps layer: cached unless package*.json changes. Full install (build needs
# devDependencies like react-scripts); --no-audit --no-fund trims install time.
COPY package*.json ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund

# Source layer (node_modules/build excluded via .dockerignore)
COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine

# Copy built files
COPY --from=build /app/build /usr/share/nginx/html

# Copy nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy policy pages for Facebook OAuth
COPY --from=build /app/public/policies/*.html /usr/share/nginx/html/policies/

# Expose port 80 only (CloudFront handles HTTPS)
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
