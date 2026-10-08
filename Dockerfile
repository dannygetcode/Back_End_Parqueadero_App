# Etapa 1: compilar
FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar app.jar

# Etapa 2: ejecutar
FROM eclipse-temurin:17-jre
# /app/uploads debe existir y ser del usuario de la app antes de montar el volumen: Docker copia
# propietario y permisos del directorio de la imagen al volumen con nombre la primera vez que se crea.
RUN useradd -r -u 10001 app \
    && mkdir -p /app/uploads \
    && chown -R app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
