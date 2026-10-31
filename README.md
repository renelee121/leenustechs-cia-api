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

La API requiere Java 21, Maven 3.9.16 o superior, Redis 6 o superior y las
variables `SPRING_APPLICATION_NAME`, `KAFKA_BOOTSTRAP_SERVERS`,
`KAFKA_USERNAME` y `KAFKA_PASSWORD`. Kafka utiliza SASL_SSL con SCRAM-SHA-512;
las credenciales no se registran en los logs.

`POST /config-api/v1/activation/login` requiere un `payload` que sea un objeto
JSON. Un payload ausente, nulo, escalar o array devuelve `400 INVALID_PAYLOAD`
sin publicar. El evento conserva el payload recibido; la respuesta añade
`status: "success"` a una copia independiente.

La respuesta `202` se envía después del ACK de Kafka con `acks=all`. La
publicación limita la espera de metadatos/buffer a 5 segundos y la entrega a
30 segundos; el adaptador espera el resultado hasta 35 segundos. Un fallo,
interrupción o timeout devuelve `503 KAFKA_PUBLICATION_UNCONFIRMED`. Un timeout
no demuestra que el evento no llegó. El ACK tampoco confirma que un consumidor
haya procesado el evento.

### Idempotencia temporal en Redis: TTL fijo de 30 segundos

Todas las solicitudes de login deben incluir `Idempotency-Key`: una clave única
por operación lógica (se recomienda un UUID aleatorio), de 1 a 128 caracteres
ASCII entre letras, dígitos, `.`, `_`, `:` y `-`. El cliente debe reutilizar
la clave y el mismo payload al reintentar. Sin clave válida, la API devuelve
`400 INVALID_IDEMPOTENCY_KEY`.

El primer intento reclama atómicamente `cia-api:idempotency:login:<clave>` en
Redis con **TTL de 30 segundos desde ese momento**. La entrada guarda el payload,
el `transactionId`, el estado y, si hay éxito, la respuesta. Las operaciones Lua
son atómicas entre réplicas. Ni los reintentos ni los cambios de estado renuevan
el TTL: se conserva con `KEEPTTL`. Una operación antigua no puede sobrescribir
una entrada nueva ni recrear su entrada si terminó después de la expiración.

Mientras la entrada exista:

| Reintento con la misma clave | Resultado |
| --- | --- |
| Mismo payload y respuesta confirmada guardada | Repite el `202` y el cuerpo original, incluido `transactionId`, sin publicar |
| Payload diferente | `409 IDEMPOTENCY_KEY_CONFLICT` |
| Publicación en curso | `409 IDEMPOTENCY_IN_PROGRESS`, sin publicar |
| Entrega fallida o incierta | `503 KAFKA_PUBLICATION_UNCONFIRMED`, sin publicar |
| Redis no disponible | `503 IDEMPOTENCY_STORE_UNAVAILABLE`; si no se pudo reclamar la clave, no se publica |

El orden de propiedades de un objeto JSON no cambia la identidad del payload.
El `transactionId` del cuerpo no determina la idempotencia: el servidor genera
uno por reclamación.

**Al vencer los 30 segundos, la misma clave vuelve a aceptarse como una operación
nueva y puede publicar otro evento**, incluso si el intento anterior sigue en
curso o su entrega fue incierta. Esta protección es temporal; no garantiza
deduplicación fuera de la ventana ni procesamiento exactamente una vez. La
pérdida de datos o expulsión anticipada de claves de Redis también acorta la
protección. Para respetar la ventana, usar Redis compartido por todas las
réplicas, dimensionado adecuadamente y con política `noeviction`.

### Configuración y pruebas

Redis local usa `localhost:6379`. Se puede configurar con:

| Variable | Valor predeterminado |
| --- | --- |
| `CIA_REDIS_HOST` | `localhost` |
| `CIA_REDIS_PORT` | `6379` |
| `CIA_REDIS_USERNAME` | vacío |
| `CIA_REDIS_PASSWORD` | vacío |
| `CIA_REDIS_SSL` | `false` |

Proporcionar credenciales mediante la configuración segura del despliegue.
Configurar TLS cuando el servicio Redis lo requiera. Los tiempos de conexión y
comando son de 2 segundos. El usuario Redis necesita permisos para ejecutar
scripts Lua y leer/escribir las claves del prefijo indicado.

Ya no se utilizan JDBC, H2, PostgreSQL, migraciones ni variables
`CIA_DATABASE_*` para la idempotencia. Los datos de instalaciones anteriores no
se migran a Redis y no se borran automáticamente.

Ejecutar `mvn clean verify` con `redis-server` disponible en `PATH` o con
`REDIS_SERVER_BINARY` apuntando al ejecutable. Las pruebas arrancan procesos
Redis reales y aislados en puertos libres, sin persistencia y enlazados a
loopback; no usan ni vacían el Redis de la aplicación. Cubren arranque y HTTP
real, concurrencia, replay, TTL, expiración, finalización tardía y errores.
Kafka se simula en las pruebas; falta validación contra un broker real.

### Compatibilidad Spring

Spring Boot **4.0.9** y Spring Cloud **2025.1.1** están alineados. La prueba de
arranque mantiene activo el verificador de compatibilidad y comprueba
`/actuator/health`, además del contrato HTTP de idempotencia.
