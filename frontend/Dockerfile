# Application build
# Node image
FROM node:20-alpine

# Change directory
WORKDIR /app

# copy the package and package-lock json to the directory
COPY package*.json ./

# Install all the dependecies
RUN npm install

# Copy the rest files
COPY . .

# Expose the react default port
EXPOSE 5173

# Start the application in dev mode
CMD [ "npm", "run", "dev" ]