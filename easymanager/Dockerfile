# 1: Application build

# Use ecplise image
FROM eclipse-temurin:21-jdk AS build

# Change the directory
WORKDIR /app

# Copy the files
COPY . .

# Create the jar file
RUN ./mvnw clean package -DskipTests

# 2: Execution
# Use ecplise image
FROM eclipse-temurin:21-jre

# Change the directory
WORKDIR /app

# Copy the jar file 
COPY --from=build /app/target/*.jar app.jar

# Expose the api port
EXPOSE 8080

# Execute the jar file
ENTRYPOINT ["java", "-jar", "app.jar"]
