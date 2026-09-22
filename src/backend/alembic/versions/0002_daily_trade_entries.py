"""Add date-scoped trading businesses and daily risk metrics."""

from alembic import op
import sqlalchemy as sa


revision = "0002_daily_trade_entries"
down_revision = "0001_initial"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "trading_businesses",
        sa.Column("id", sa.String(length=36), nullable=False),
        sa.Column("name", sa.String(length=100), nullable=False),
        sa.Column("code", sa.String(length=30), nullable=False),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("code"),
    )
    op.create_index("ix_trading_businesses_code", "trading_businesses", ["code"], unique=False)
    op.create_index(
        "ix_trading_businesses_is_active", "trading_businesses", ["is_active"], unique=False
    )
    op.create_table(
        "daily_trade_entries",
        sa.Column("id", sa.String(length=36), nullable=False),
        sa.Column("business_id", sa.String(length=36), nullable=False),
        sa.Column("business_date", sa.Date(), nullable=False),
        sa.Column("delta", sa.Numeric(precision=20, scale=4), nullable=False, server_default="0"),
        sa.Column("gamma", sa.Numeric(precision=20, scale=4), nullable=False, server_default="0"),
        sa.Column("theta", sa.Numeric(precision=20, scale=4), nullable=False, server_default="0"),
        sa.Column("vega", sa.Numeric(precision=20, scale=4), nullable=False, server_default="0"),
        sa.Column("pnl", sa.Numeric(precision=20, scale=4), nullable=False, server_default="0"),
        sa.Column("version", sa.Integer(), nullable=False, server_default="1"),
        sa.Column("created_by_id", sa.String(length=36), nullable=False),
        sa.Column("updated_by_id", sa.String(length=36), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("locked", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("locked_by_id", sa.String(length=36), nullable=True),
        sa.Column("locked_by_display_name", sa.String(length=200), nullable=True),
        sa.Column("locked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("lock_expires_at", sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(["business_id"], ["trading_businesses.id"]),
        sa.ForeignKeyConstraint(["created_by_id"], ["users.id"]),
        sa.ForeignKeyConstraint(["locked_by_id"], ["users.id"]),
        sa.ForeignKeyConstraint(["updated_by_id"], ["users.id"]),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("business_id", "business_date", name="uq_daily_trade_entries_business_date"),
    )
    op.create_index(
        "ix_daily_trade_entries_business_date", "daily_trade_entries", ["business_date"], unique=False
    )
    op.create_index("ix_daily_trade_entries_locked", "daily_trade_entries", ["locked"], unique=False)


def downgrade() -> None:
    op.drop_index("ix_daily_trade_entries_locked", table_name="daily_trade_entries")
    op.drop_index("ix_daily_trade_entries_business_date", table_name="daily_trade_entries")
    op.drop_table("daily_trade_entries")
    op.drop_index("ix_trading_businesses_is_active", table_name="trading_businesses")
    op.drop_index("ix_trading_businesses_code", table_name="trading_businesses")
    op.drop_table("trading_businesses")
