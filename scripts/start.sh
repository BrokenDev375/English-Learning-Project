#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ ! -f .env ]; then
  cp .env.example .env
  app_password=$(openssl rand -hex 20)
  root_password=$(openssl rand -hex 20)
  admin_password=$(openssl rand -hex 20)
  sed -i "s/local-app-change-me/$app_password/;s/local-root-change-me/$root_password/;s/generate-admin-password/$admin_password/" .env
fi
docker compose up -d --build --wait
printf 'Open http://localhost:5173 (or APP_PORT configured in .env)\n'
