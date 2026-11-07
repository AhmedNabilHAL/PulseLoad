FROM node:22-alpine

WORKDIR /app

# Copy dependency manifests first for better build caching.
COPY package*.json ./

RUN npm install

# Copy the application source.
COPY . .

EXPOSE 5173

CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]