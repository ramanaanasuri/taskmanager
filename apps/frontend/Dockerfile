# Build stage
FROM node:18-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm install --production

COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine

# Copy built files
COPY --from=build /app/build /usr/share/nginx/html

# Copy nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy policy pages for Facebook OAuth                          # ADDED
COPY --from=build /app/public/policies/*.html /usr/share/nginx/html/policies/

# Expose port 80 only (CloudFront handles HTTPS)
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
