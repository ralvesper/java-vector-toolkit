# Java Vector Toolkit (POC)

POC reutilizavel para indexacao, busca semantica e base para RAG em Java, com arquitetura Ports and Adapters.

## Objetivo

Este projeto demonstra como encapsular responsabilidades de vetor em um toolkit reutilizavel:

- indexacao de documentos
- chunking configuravel
- geracao de embeddings
- busca semantica com filtros
- recuperacao de contexto para RAG
- isolamento do provider de vector store via porta (`VectorStorePort`)

## Modulos

- `vector-core`: dominio e portas (`VectorStorePort`, `EmbeddingProvider`, `VectorKnowledgeBase`).
- `vector-document`: carregamento de documentos e chunking (`FixedSizeChunkStrategy`).
- `vector-pinecone`: adapter Pinecone via HTTP API.
- `vector-rag`: montagem de contexto e servico de RAG.
- `vector-spring-boot-starter`: auto-configuracao Spring Boot.
- `examples/semantic-search-demo`: API REST de demonstracao.
- `examples/source-code-search-demo`: exemplo CLI para busca em informacoes de codigo.

## Requisitos

- Java 21
- Maven 3.9+
- Docker (opcional)
- Docker Compose (opcional)
- GNU Make (opcional)

## Quickstart

### 1) Build e testes

```bash
cd /home/rodrigo/dev/sodexo/gitlab/pluxee-tooling/java-vector-toolkit
mvn clean test
```

Para executar apenas testes negativos (cenarios de falha esperada de configuracao):

```bash
cd /home/rodrigo/dev/sodexo/gitlab/pluxee-tooling/java-vector-toolkit
mvn -pl vector-spring-boot-starter -am -Pnegative-tests test
```

Ou com `Makefile`:

```bash
cd /home/rodrigo/dev/sodexo/gitlab/pluxee-tooling/java-vector-toolkit
make test
make build
```

### 2) Subir API localmente

```bash
cd /home/rodrigo/dev/sodexo/gitlab/pluxee-tooling/java-vector-toolkit
mvn -pl examples/semantic-search-demo spring-boot:run
```

### 3) Subir com Docker (porta externa 18084)

Via `Makefile`:

```bash
cd /home/rodrigo/dev/sodexo/gitlab/pluxee-tooling/java-vector-toolkit
make docker-build
make docker-run
```

Via Docker Compose:

```bash
cd /home/rodrigo/dev/sodexo/gitlab/pluxee-tooling/java-vector-toolkit
make compose-up
```

Smoke test ponta a ponta (sobe app + valida indexacao e busca):

```bash
cd /home/rodrigo/dev/sodexo/gitlab/pluxee-tooling/java-vector-toolkit
make smoke
```

Aplicacao disponivel em `http://localhost:18084`.

## Uso da API REST

Base URL:

```text
http://localhost:18084
```

### 1) Indexar documento

```bash
curl -X POST http://localhost:18084/api/vector/documents \
  -H "Content-Type: application/json" \
  -d '{
    "dataset": "java-docs",
    "documentId": "websphere.md",
    "content": "WebSphere Application Server pode apresentar timeout em operacoes longas...",
    "metadata": {
      "technology": "websphere",
      "type": "documentation",
      "project": "legacy-system"
    }
  }'
```

### 2) Buscar por similaridade

```bash
curl -X POST http://localhost:18084/api/vector/search \
  -H "Content-Type: application/json" \
  -d '{
    "dataset": "java-docs",
    "query": "como resolver timeout no servidor",
    "topK": 5,
    "filters": {
      "technology": "websphere"
    }
  }'
```

### 3) Perguntar via RAG

```bash
curl -X POST http://localhost:18084/api/vector/ask \
  -H "Content-Type: application/json" \
  -d '{
    "dataset": "java-docs",
    "question": "Como resolver timeout no servidor?"
  }'
```

### 4) Remover documento

```bash
curl -X DELETE http://localhost:18084/api/vector/documents/java-docs/websphere.md
```

## Segundo consumidor (CLI)

Executa um exemplo de reutilizacao com dados de codigo-fonte:

```bash
cd /home/rodrigo/dev/sodexo/gitlab/pluxee-tooling/java-vector-toolkit
mvn -pl examples/source-code-search-demo exec:java
```

## Configuracao minima

```yaml
vector:
  store:
    provider: in-memory
  chunking:
    size: 800
    overlap: 100
  search:
    top-k: 5
```

Para usar Pinecone:

```yaml
vector:
  store:
    provider: pinecone
  pinecone:
    api-key: ${PINECONE_API_KEY}
    host: ${PINECONE_HOST}
```

## Como integrar em outro projeto Spring Boot

1) Adicione dependencia do starter no projeto consumidor:

```xml
<dependency>
    <groupId>com.pluxee.ai</groupId>
    <artifactId>vector-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

2) Configure provider no `application.yml` (in-memory ou pinecone).

3) Injete e use os beans prontos:

```java
@Service
public class ArchitectureSearchService {

    private final VectorKnowledgeBase knowledgeBase;

    public ArchitectureSearchService(VectorKnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }
}
```

Beans auto-configurados pelo starter:

- `EmbeddingProvider`
- `VectorStorePort`
- `ChunkStrategy`
- `VectorKnowledgeBase`
- `ContextBuilder`
- `RagService`

## Makefile (atalhos)

- `make test`: roda testes Maven.
- `make build`: gera artifacts do demo REST.
- `make docker-build`: build da imagem Docker.
- `make docker-run`: sobe container em `18084:8080`.
- `make docker-stop`: remove container local.
- `make docker-logs`: acompanha logs do container.
- `make compose-up`: sobe stack via Docker Compose.
- `make compose-down`: derruba stack do compose.
- `make compose-logs`: logs via compose.
- `make smoke`: valida startup da app e fluxo basico de indexacao/busca via API.

## Pinecone

O adapter `vector-pinecone` implementa `VectorStorePort` e pode substituir `InMemoryVectorStore` por configuracao de beans no projeto consumidor.

Na POC atual, o exemplo REST sobe com store em memoria por default.

### Consultar dados diretamente no Pinecone (curl)

Defina variaveis de ambiente:

```bash
export PINECONE_API_KEY="<seu-token>"
export PINECONE_HOST="https://<index-host>.svc.<region>.pinecone.io"
```

Consultar estatisticas do indice:

```bash
curl -X POST "$PINECONE_HOST/describe_index_stats" \
  -H "Api-Key: $PINECONE_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{}'
```

Buscar vetores por similaridade em um namespace (dataset):

```bash
curl -X POST "$PINECONE_HOST/query" \
  -H "Api-Key: $PINECONE_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "java-docs",
    "vector": [0.1, 0.2, 0.3],
    "topK": 5,
    "includeMetadata": true,
    "filter": {
      "technology": "websphere"
    }
  }'
```

Remover vetores por `documentId` dentro do namespace:

```bash
curl -X POST "$PINECONE_HOST/vectors/delete" \
  -H "Api-Key: $PINECONE_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "java-docs",
    "filter": {
      "documentId": "websphere.md"
    }
  }'
```

Observacao: para teste funcional rapido do toolkit, prefira primeiro os endpoints locais em `http://localhost:18084/api/vector/*`.

## Troubleshooting rapido

- Erro `Conflict. The container name ... is already in use`: rode `make docker-stop` ou `make compose-down` e suba novamente.
- `404` em `GET /`: esperado; use os endpoints em `/api/vector/*`.
- Erro `no main manifest attribute`: garantir uso do `spring-boot-maven-plugin` com `repackage` no modulo `semantic-search-demo`.

## Documentacao adicional

- Comandos uteis: `docs/comandos-uteis.md`
- Status de aderencia ao PRD: `docs/prd-checklist-status.md`
- Collection Postman: `../plx-docs/collections/java-vector-toolkit-semantic-search.postman_collection.json`
- Environments Postman: `../plx-docs/collections/java-vector-toolkit-local.postman_environment.json` e `../plx-docs/collections/java-vector-toolkit-docker.postman_environment.json`

### Regra de atualizacao da collection

Quando endpoints/payloads mudarem, atualizar em conjunto:

- `examples/semantic-search-demo/src/main/java/com/pluxee/vector/examples/semantic/VectorController.java`
- `README.md`
- `../plx-docs/collections/java-vector-toolkit-semantic-search.postman_collection.json`

### Observacao sobre ambientes

Este workspace esta rodando localmente. Use o environment `java-vector-toolkit-local` por padrao.
O environment `java-vector-toolkit-docker` pode ser usado quando a app estiver rodando no Docker via `http://localhost:18084`.

