# leenustechs-cia-api
Project for an open source students management api
```
leenustechs-cia-api
├─ LICENSE
├─ README.md
├─ pom.xml
├─ src
│  ├─ main
│  │  ├─ java
│  │  │  └─ mx
│  │  │     └─ com
│  │  │        └─ leenustechs
│  │  │           ├─ App.java
│  │  │           └─ ciaApi
│  │  │              ├─ business
│  │  │              │  ├─ adapters
│  │  │              │  │  ├─ in
│  │  │              │  │  │  ├─ ApiInterceptor.java
│  │  │              │  │  │  └─ ConfigApiProcessor.java
│  │  │              │  │  └─ out
│  │  │              │  │     └─ KafkaProducerAdapter.java
│  │  │              │  ├─ services
│  │  │              │  │  ├─ CommandDispatcherService.java
│  │  │              │  │  └─ impl
│  │  │              │  │     └─ CommandDispatcherServiceImpl.java
│  │  │              │  ├─ useCases
│  │  │              │  │  └─ CommonEventUseCase.java
│  │  │              │  └─ utils
│  │  │              │     ├─ commons
│  │  │              │     │  ├─ CustomSerializer.java
│  │  │              │     │  └─ EventOperation.java
│  │  │              │     ├─ exceptions
│  │  │              │     │  ├─ EmptyOperationResponseException.java
│  │  │              │     │  └─ handlers
│  │  │              │     └─ mappers
│  │  │              │        └─ CommonModelMapper.java
│  │  │              ├─ config
│  │  │              │  ├─ FeingConfig.java
│  │  │              │  ├─ InterceptorConfig.java
│  │  │              │  ├─ JacksonConfig.java
│  │  │              │  └─ KafkaConfig.java
│  │  │              ├─ models
│  │  │              │  ├─ CommonModel.java
│  │  │              │  ├─ Event.java
│  │  │              │  ├─ constants
│  │  │              │  │  └─ KafkaTopics.java
│  │  │              │  ├─ requests
│  │  │              │  │  └─ CommonModelRequest.java
│  │  │              │  ├─ responses
│  │  │              │  │  └─ CommonModelResponse.java
│  │  │              │  └─ types
│  │  │              │     └─ OperationType.java
│  │  │              └─ rest
│  │  │                 ├─ ConfigApi.java
│  │  │                 └─ GlobalExceptionHandler.java
│  │  └─ resources
│  │     ├─ application.properties
│  │     └─ templates
│  │        └─ hello.html
│  └─ test
│     ├─ java
│     │  └─ mx
│     │     └─ com
│     │        └─ leenustechs
│     └─ resources
└─ target
   ├─ classes
   │  ├─ application.properties
   │  ├─ mx
   │  │  └─ com
   │  │     └─ leenustechs
   │  │        └─ ciaApi
   │  │           ├─ business
   │  │           │  ├─ adapters
   │  │           │  │  ├─ in
   │  │           │  │  └─ out
   │  │           │  ├─ services
   │  │           │  │  └─ impl
   │  │           │  ├─ useCases
   │  │           │  └─ utils
   │  │           │     ├─ commons
   │  │           │     ├─ exceptions
   │  │           │     └─ mappers
   │  │           ├─ config
   │  │           ├─ models
   │  │           │  ├─ constants
   │  │           │  ├─ requests
   │  │           │  ├─ responses
   │  │           │  └─ types
   │  │           └─ rest
   │  └─ templates
   │     └─ hello.html
   ├─ generated-sources
   │  └─ annotations
   │     └─ mx
   │        └─ com
   │           └─ leenustechs
   │              └─ ciaApi
   │                 └─ business
   │                    └─ utils
   │                       └─ mappers
   │                          └─ CommonModelMapperImpl.java
   ├─ generated-test-sources
   │  └─ test-annotations
   ├─ maven-archiver
   │  └─ pom.properties
   ├─ maven-status
   │  └─ maven-compiler-plugin
   │     ├─ compile
   │     │  └─ default-compile
   │     │     ├─ createdFiles.lst
   │     │     └─ inputFiles.lst
   │     └─ testCompile
   │        └─ default-testCompile
   │           ├─ createdFiles.lst
   │           └─ inputFiles.lst
   └─ test-classes

```
## Publicación Kafka y respuestas HTTP

La API requiere Java 21, Maven 3.9.16 o superior y las variables
`SPRING_APPLICATION_NAME`, `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_USERNAME` y
`KAFKA_PASSWORD`. Kafka utiliza SASL_SSL con SCRAM-SHA-512; las credenciales
no se registran en los logs.

`POST /config-api/v1/activation/login` requiere un `payload` que sea un objeto
JSON. Un payload ausente, nulo, escalar o array devuelve `400 INVALID_PAYLOAD`
sin publicar. El evento conserva el payload recibido; la respuesta añade
`status: "success"` a una copia independiente.

La respuesta `202` se envía después del ACK de Kafka con `acks=all`. La
publicación limita la espera de metadatos/buffer a 5 segundos y la entrega a
30 segundos; el adaptador espera el resultado hasta 35 segundos. Un fallo,
interrupción o timeout devuelve `503 KAFKA_PUBLICATION_UNCONFIRMED`. Un timeout
no demuestra que el evento no llegó: los reintentos HTTP pueden duplicarlo y
requieren una estrategia de idempotencia. El ACK tampoco confirma que un
consumidor haya procesado el evento.

Ejecutar las pruebas y empaquetar con `mvn verify`. Las pruebas cubren el contrato
HTTP y la confirmación mediante un productor simulado; no sustituyen una prueba
contra un broker real.

Limitación existente: Spring Boot 4.1.0 y Spring Cloud 2025.1.1 no superan la
verificación de compatibilidad al arrancar (Spring Cloud requiere Boot 4.0.x).
Debe alinearse esa combinación antes de desplegar; no se deshabilita el verificador.
