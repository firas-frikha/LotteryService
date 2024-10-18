CREATE TABLE IF NOT EXISTS lotteries (
    lottery_id VARCHAR(64),
    lottery_name VARCHAR(255),
    created_at DATE,
    lottery_state VARCHAR(255),
    winner VARCHAR(255),
    PRIMARY KEY(lottery_id));


CREATE TABLE IF NOT EXISTS lottery_participants (
    participant_id VARCHAR(64),
    lottery_id VARCHAR(64),
    PRIMARY KEY(participant_id)
)