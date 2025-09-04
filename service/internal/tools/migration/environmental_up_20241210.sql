CREATE TABLE IF NOT EXISTS "environmental" (
    "id" INTEGER PRIMARY KEY,
    "job_id" varchar(255) NOT NULL,
    "temperature" varchar(255) NOT NULL,
    "humidity" varchar(255) NOT NULL,
    "pressure" varchar(255) NOT NULL,
    "created_at" timestamp DEFAULT CURRENT_TIMESTAMP
);