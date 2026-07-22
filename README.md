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
│  │  │           └─ cia
│  │  │              ├─ business
│  │  │              │  ├─ adapters
│  │  │              │  │  ├─ in
│  │  │              │  │  │  ├─ ApiInterceptor.java
│  │  │              │  │  │  └─ ConfigApiProcessor.java
│  │  │              │  │  └─ out
│  │  │              │  ├─ services
│  │  │              │  │  ├─ CommandDispatcherService.java
│  │  │              │  │  └─ impl
│  │  │              │  │     └─ CommandDispatcherServiceImpl.java
│  │  │              │  ├─ useCases
│  │  │              │  │  └─ CommonEventUseCase.java
│  │  │              │  └─ utils
│  │  │              │     ├─ commons
│  │  │              │     │  └─ EventOperation.java
│  │  │              │     ├─ exceptions
│  │  │              │     │  ├─ EmptyOperationResponseException.java
│  │  │              │     │  └─ handlers
│  │  │              │     └─ mappers
│  │  │              │        └─ CommonModelMapper.java
│  │  │              ├─ config
│  │  │              │  └─ InterceptorConfig.java
│  │  │              ├─ models
│  │  │              │  ├─ CommonModel.java
│  │  │              │  ├─ Event.java
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
   │  │        └─ cia
   │  │           ├─ business
   │  │           │  ├─ adapters
   │  │           │  │  └─ in
   │  │           │  ├─ services
   │  │           │  │  └─ impl
   │  │           │  ├─ useCases
   │  │           │  └─ utils
   │  │           │     ├─ commons
   │  │           │     ├─ exceptions
   │  │           │     └─ mappers
   │  │           ├─ config
   │  │           ├─ models
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
   │              └─ cia
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