# Docker setup

This project uses JavaFX for UI, so the recommended Docker setup is to run only MySQL in a container and keep the Java app on the host machine.

## 1) Prepare env file

Copy the example env file:

`copy .env.docker.example .env`

## 2) Start MySQL

`docker compose up -d`

MySQL will be available at `localhost:3300`.

## 3) Configure app connection

Set values in `src/main/resources/db.properties`:

- `db.url=jdbc:mysql://localhost:3300/diagram?...`
- `db.username=...`
- `db.password=...`

Use credentials from your `.env`.

## 4) Stop services

`docker compose down`

To remove data volume too:

`docker compose down -v`
