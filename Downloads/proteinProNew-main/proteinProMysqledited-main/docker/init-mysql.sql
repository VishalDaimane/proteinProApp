-- Protein Pro App MySQL Initialization Script for Docker
CREATE DATABASE IF NOT EXISTS authentication_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS user_profile_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'proteinpro_auth'@'%' IDENTIFIED BY 'auth_password_123';
CREATE USER IF NOT EXISTS 'proteinpro_profile'@'%' IDENTIFIED BY 'profile_password_123';

GRANT ALL PRIVILEGES ON authentication_db.* TO 'proteinpro_auth'@'%';
GRANT ALL PRIVILEGES ON user_profile_db.* TO 'proteinpro_profile'@'%';
FLUSH PRIVILEGES;
