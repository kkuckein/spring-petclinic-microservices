FROM java:8
ARG ARTIFACT_NAME
ADD ${ARTIFACT_NAME}.jar /app.jar
ADD logback.xml /logback.xml
ENTRYPOINT ["java","-Dlogback.configurationFile=/logback.xml","-Dgatling.core.simulationClass=org.springframework.samples.petclinic.load.SimpleSimulation","-cp","/app.jar", "io.gatling.app.Gatling"]
