CREATE TABLE IF NOT EXISTS lotteries (
    lottery_id VARCHAR(64),
    lottery_name VARCHAR(255),
    created_at TIMESTAMP,
    participants VARCHAR(255)[],
    lottery_state VARCHAR(255),
    winner VARCHAR(255),
    PRIMARY KEY(lottery_id));