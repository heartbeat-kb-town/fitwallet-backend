CREATE TABLE IF NOT EXISTS health_check (
    id INT PRIMARY KEY AUTO_INCREMENT,
    message VARCHAR(100) NOT NULL,
    checked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO health_check (message) VALUES ('fitwallet-backend DB connection OK');
