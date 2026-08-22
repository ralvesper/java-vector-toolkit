APP_MODULE=examples/semantic-search-demo
IMAGE_NAME=java-vector-toolkit-semantic-demo
IMAGE_TAG=local
CONTAINER_NAME=java-vector-toolkit-semantic-demo
EXTERNAL_PORT=18084
INTERNAL_PORT=8080
BASE_URL=http://localhost:$(EXTERNAL_PORT)

.PHONY: test test-integration build docker-build docker-run docker-stop docker-logs compose-up compose-down compose-logs smoke

test:
	mvn -q test

test-integration:
	mvn -q test -pl vector-pinecone -Dtest=PineconeLiveIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false

build:
	mvn -q -pl $(APP_MODULE) -am clean package -DskipTests

docker-build:
	docker build -f $(APP_MODULE)/Dockerfile -t $(IMAGE_NAME):$(IMAGE_TAG) .

docker-run:
	docker rm -f $(CONTAINER_NAME) >/dev/null 2>&1 || true
	docker run -d --name $(CONTAINER_NAME) -p $(EXTERNAL_PORT):$(INTERNAL_PORT) $(IMAGE_NAME):$(IMAGE_TAG)

docker-stop:
	docker rm -f $(CONTAINER_NAME)

docker-logs:
	docker logs -f $(CONTAINER_NAME)

compose-up:
	docker compose up -d --build

compose-down:
	docker compose down

compose-logs:
	docker compose logs -f semantic-search-demo

smoke: compose-up
	@echo "[smoke] waiting app on $(BASE_URL)"
	@for i in $$(seq 1 30); do \
		code=$$(curl -s -o /dev/null -w "%{http_code}" $(BASE_URL)/ || true); \
		if [ "$$code" = "404" ] || [ "$$code" = "200" ]; then \
			echo "[smoke] app respondeu com HTTP $$code"; \
			break; \
		fi; \
		sleep 1; \
		if [ $$i -eq 30 ]; then \
			echo "[smoke] timeout aguardando app"; \
			exit 1; \
		fi; \
	done
	@echo "[smoke] indexando documento de teste"
	@idx_code=$$(curl -s -o /tmp/jvt_smoke_index.out -w "%{http_code}" \
		-X POST $(BASE_URL)/api/vector/documents \
		-H "Content-Type: application/json" \
		-d '{"dataset":"smoke","documentId":"smoke-doc","content":"RabbitMQ desacopla processamento assincrono","metadata":{"technology":"rabbitmq"}}'); \
	if [ "$$idx_code" != "200" ]; then \
		echo "[smoke] falha indexacao HTTP $$idx_code"; \
		cat /tmp/jvt_smoke_index.out; \
		exit 1; \
	fi
	@echo "[smoke] consultando busca semantica"
	@search_code=$$(curl -s -o /tmp/jvt_smoke_search.out -w "%{http_code}" \
		-X POST $(BASE_URL)/api/vector/search \
		-H "Content-Type: application/json" \
		-d '{"dataset":"smoke","query":"como desacoplar processamento?","topK":3}'); \
	if [ "$$search_code" != "200" ]; then \
		echo "[smoke] falha busca HTTP $$search_code"; \
		cat /tmp/jvt_smoke_search.out; \
		exit 1; \
	fi
	@grep -q "smoke-doc" /tmp/jvt_smoke_search.out || (echo "[smoke] resultado sem smoke-doc" && cat /tmp/jvt_smoke_search.out && exit 1)
	@echo "[smoke] OK"
