COMPOSE = docker compose

.PHONY: help start stop status logs restart images
.DEFAULT_GOAL := help

help:
	@echo start: start built images; stop: stop and keep data
	@echo status: container status; logs: follow logs; restart: restart frontend
	@echo images: pull container images

start:
	$(COMPOSE) up -d --wait

stop:
	$(COMPOSE) down

status:
	$(COMPOSE) ps

logs:
	$(COMPOSE) logs --tail 100 -f

restart:
	$(COMPOSE) restart frontend

images:
	$(COMPOSE) pull
