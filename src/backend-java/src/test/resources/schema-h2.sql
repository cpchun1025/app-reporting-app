CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL,
    is_admin BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS trades (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    trade_date DATE NOT NULL,
    account VARCHAR(100) NOT NULL,
    instrument VARCHAR(100) NOT NULL,
    side VARCHAR(4) NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    price DECIMAL(18,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    notes CLOB,
    version INT NOT NULL,
    created_by_id VARCHAR(36) NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    locked_by_id VARCHAR(36) REFERENCES users(id),
    locked_by_display_name VARCHAR(200),
    locked_at TIMESTAMP WITH TIME ZONE,
    lock_expires_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS ix_trades_account ON trades (account);
CREATE INDEX IF NOT EXISTS ix_trades_instrument ON trades (instrument);
CREATE INDEX IF NOT EXISTS ix_trades_trade_date ON trades (trade_date);
CREATE INDEX IF NOT EXISTS ix_trades_locked ON trades (locked);

CREATE TABLE IF NOT EXISTS trade_entry_snapshots (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    business_date DATE NOT NULL,
    data_json CLOB NOT NULL,
    saved_by_id VARCHAR(36) NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_trade_entry_snapshots_business_date ON trade_entry_snapshots (business_date);

CREATE TABLE IF NOT EXISTS trading_businesses (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(30) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_trading_businesses_code ON trading_businesses (code);
CREATE INDEX IF NOT EXISTS ix_trading_businesses_is_active ON trading_businesses (is_active);

CREATE TABLE IF NOT EXISTS daily_trade_entries (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    business_id VARCHAR(36) NOT NULL REFERENCES trading_businesses(id),
    business_date DATE NOT NULL,
    delta DECIMAL(20,4) NOT NULL DEFAULT 0,
    gamma DECIMAL(20,4) NOT NULL DEFAULT 0,
    theta DECIMAL(20,4) NOT NULL DEFAULT 0,
    vega DECIMAL(20,4) NOT NULL DEFAULT 0,
    pnl DECIMAL(20,4) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    created_by_id VARCHAR(36) NOT NULL REFERENCES users(id),
    updated_by_id VARCHAR(36) REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    locked_by_id VARCHAR(36) REFERENCES users(id),
    locked_by_display_name VARCHAR(200),
    locked_at TIMESTAMP WITH TIME ZONE,
    lock_expires_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(business_id, business_date)
);
CREATE INDEX IF NOT EXISTS ix_daily_trade_entries_business_date ON daily_trade_entries (business_date);
CREATE INDEX IF NOT EXISTS ix_daily_trade_entries_locked ON daily_trade_entries (locked);

CREATE TABLE IF NOT EXISTS alembic_version (
    version_num VARCHAR(32) NOT NULL PRIMARY KEY
);
INSERT INTO alembic_version (version_num) VALUES ('0002_daily_trade_entries');
