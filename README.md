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
no demuestra que el evento no llegó. El registro persistente impide republicarlo
automáticamente con la misma clave; el ACK tampoco confirma que un consumidor
haya procesado el evento.

### Idempotencia HTTP

Todas las solicitudes de login deben incluir `Idempotency-Key`: una clave única
por operación lógica (se recomienda un UUID aleatorio), de 1 a 128 caracteres
ASCII entre letras, dígitos, `.`, `_`, `:` y `-`. El cliente debe guardar la
clave antes del primer envío y reutilizarla con el mismo payload en todos los
reintentos. Sin clave válida, la API devuelve `400 INVALID_IDEMPOTENCY_KEY`.

| Reintento con la misma clave | Resultado |
| --- | --- |
| Mismo payload, publicación confirmada y respuesta persistida | Repite el `202` y el cuerpo original, incluido `transactionId`, sin publicar |
| Payload diferente | `409 IDEMPOTENCY_KEY_CONFLICT` |
| Solicitud todavía en curso o interrumpida antes de persistir el resultado | `409 IDEMPOTENCY_IN_PROGRESS`, sin publicar |
| Entrega fallida o incierta | `503 KAFKA_PUBLICATION_UNCONFIRMED`, sin publicar |

El orden de las propiedades de un objeto JSON no cambia la identidad del
payload. El `transactionId` del cuerpo de entrada no determina la idempotencia:
el servidor crea y persiste un identificador estable por clave. El registro se
reclama con una restricción única en la base antes de publicar en Kafka; si la
base no está disponible, no se publica.

Desarrollo usa H2 en `./data/cia-api` (directorio ignorado por Git). Conservar ese
directorio entre reinicios; no usar una base en memoria para el servicio real.
Para producción y varias réplicas, configurar **la misma base PostgreSQL** en
todas las instancias mediante `CIA_DATABASE_URL` (URL JDBC),
`CIA_DATABASE_USERNAME` y `CIA_DATABASE_PASSWORD`. El directorio H2 local no
coordina réplicas con discos independientes. Proporcionar contraseñas mediante la
configuración segura del despliegue. `schema.sql` crea la tabla si no existe; la
cuenta de arranque requiere permiso de creación o una migración previa equivalente.

No hay expiración ni eliminación automática de claves. El registro guarda
payloads y respuestas: requiere almacenamiento persistente, acceso restringido y
una política de conservación adecuada. El cliente no debe cambiar de clave para
reintentar una operación incierta. Tras una caída entre el envío y el guardado
de la respuesta, un operador debe reconciliar el registro con el evento Kafka
usando su `transaction_id`; no borrar ni liberar una clave incierta
automáticamente. Esta solución evita publicaciones duplicadas por reintentos,
pero no ofrece una transacción atómica entre Kafka y la base ni procesamiento
exactamente una vez en los consumidores.

Ejecutar las pruebas y empaquetar con `mvn verify`. Las pruebas cubren arranque y HTTP real,
reintentos, concurrencia, persistencia tras reapertura de la base, fallos y
confirmación mediante un productor simulado. No sustituyen una prueba contra un
broker real ni la validación del PostgreSQL del despliegue.

### Compatibilidad Spring

Spring Boot **4.0.9** y Spring Cloud **2025.1.1** están alineados. La prueba de
arranque mantiene activo el verificador de compatibilidad y comprueba
`/actuator/health`, además del contrato HTTP de idempotencia.
