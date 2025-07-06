# ==============================================================================
# Makefile pour le Backend - Personal Data Dashboard
#
# Automatise le build et le lancement de l'environnement de développement
# pour l'application Spring Boot.
#
# Prérequis : Docker, Docker Compose, Maven (mvn)
# ==============================================================================

# --- Configuration ---
# Chemin vers le répertoire du projet backend Spring Boot
BACKEND_DIR := ./


# --- Cibles principales ---
.PHONY: run build rebuild down logs clean help

# La cible par défaut affiche l'aide pour guider l'utilisateur.
all: help

run: build ## Reconstruit l'image Docker du backend et lance l'environnement (app + db)
	@echo "🚀 Lancement de l'environnement backend avec Docker Compose..."
	docker compose up --build -d
	@echo "✅ Backend démarré. Utilise 'make logs' pour voir les logs."

build: ## Construit l'application Spring Boot en un fichier JAR exécutable
	@echo "🛠️  Construction du backend (Spring Boot)..."
	@echo "Cette opération peut prendre un moment."
	@cd $(BACKEND_DIR) && mvn clean package -DskipTests
	@echo "✅ Artefact backend (.jar) construit dans le dossier '$(BACKEND_DIR)/target'."

rebuild: clean build ## Nettoie les anciens artefacts puis reconstruit complètement le backend

down: ## Arrête et supprime les conteneurs Docker (app + db)
	@echo "🔥 Arrêt et suppression des conteneurs de l'environnement..."
	docker-compose down --volumes
	@echo "✅ Environnement arrêté."

# --- Cibles utilitaires ---
.PHONY: logs ps clean-db

logs: ## Affiche les logs de tous les services en temps réel (app + db)
	@echo "📜 Affichage des logs en continu (Ctrl+C pour arrêter)..."
	docker-compose logs -f

ps: ## Affiche le statut des conteneurs Docker
	@echo "📊 Statut des conteneurs :"
	docker-compose ps

clean: ## Nettoie le dossier 'target' généré par Maven
	@echo "🧹 Nettoyage des artefacts de build du backend..."
	@cd $(BACKEND_DIR) && mvn clean
	@echo "✅ Dossier 'target' supprimé."

clean-db: ## Supprime uniquement le volume de la base de données pour repartir de zéro
	@echo "⚠️  ATTENTION : Cette commande va supprimer toutes les données de la base PostgreSQL."
	@read -p "Es-tu sûr de vouloir continuer ? (y/N) " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		echo "🔥 Suppression du volume de la base de données..."; \
		docker-compose down -v; \
		echo "✅ Volume de la base de données supprimé."; \
	else \
		echo "Opération annulée."; \
	fi


# --- Aide ---
help: ## Affiche ce message d'aide
	@echo "Makefile pour la gestion du backend"
	@echo ""
	@echo "Utilisation : make [cible]"
	@echo ""
	@echo "Cibles disponibles :"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

