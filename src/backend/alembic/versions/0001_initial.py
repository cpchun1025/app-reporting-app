"""Create users and trades tables."""

from alembic import op
import sqlalchemy as sa

revision = "0001_initial"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("id", sa.String(length=36), nullable=False),
        sa.Column("username", sa.String(length=100), nullable=False),
        sa.Column("password_hash", sa.String(length=255), nullable=False),
        sa.Column("is_active", sa.Boolean(), nullable=False),
        sa.Column("is_admin", sa.Boolean(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("username"),
    )
    op.create_index("ix_users_username", "users", ["username"], unique=False)
    op.create_table(
        "trades",
        sa.Column("id", sa.String(length=36), nullable=False),
        sa.Column("trade_date", sa.Date(), nullable=False),
        sa.Column("account", sa.String(length=100), nullable=False),
        sa.Column("instrument", sa.String(length=100), nullable=False),
        sa.Column("side", sa.String(length=4), nullable=False),
        sa.Column("quantity", sa.Numeric(precision=18, scale=4), nullable=False),
        sa.Column("price", sa.Numeric(precision=18, scale=4), nullable=False),
        sa.Column("currency", sa.String(length=3), nullable=False),
        sa.Column("status", sa.String(length=20), nullable=False),
        sa.Column("notes", sa.Text(), nullable=True),
        sa.Column("version", sa.Integer(), nullable=False),
        sa.Column("created_by_id", sa.String(length=36), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("locked", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("locked_by_id", sa.String(length=36), nullable=True),
        sa.Column("locked_by_display_name", sa.String(length=200), nullable=True),
        sa.Column("locked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("lock_expires_at", sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(["created_by_id"], ["users.id"]),
        sa.ForeignKeyConstraint(["locked_by_id"], ["users.id"]),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_trades_account", "trades", ["account"], unique=False)
    op.create_index("ix_trades_instrument", "trades", ["instrument"], unique=False)
    op.create_index("ix_trades_trade_date", "trades", ["trade_date"], unique=False)
    op.create_index("ix_trades_locked", "trades", ["locked"], unique=False)
    op.create_table(
        "trade_entry_snapshots",
        sa.Column("id", sa.String(length=36), nullable=False),
        sa.Column("business_date", sa.Date(), nullable=False),
        sa.Column("data_json", sa.Text(), nullable=False),
        sa.Column("saved_by_id", sa.String(length=36), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.ForeignKeyConstraint(["saved_by_id"], ["users.id"]),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_trade_entry_snapshots_business_date", "trade_entry_snapshots", ["business_date"], unique=False)


def downgrade() -> None:
    op.drop_index("ix_trades_trade_date", table_name="trades")
    op.drop_index("ix_trades_locked", table_name="trades")
    op.drop_index("ix_trade_entry_snapshots_business_date", table_name="trade_entry_snapshots")
    op.drop_table("trade_entry_snapshots")
    op.drop_index("ix_trades_instrument", table_name="trades")
    op.drop_index("ix_trades_account", table_name="trades")
    op.drop_table("trades")
    op.drop_index("ix_users_username", table_name="users")
    op.drop_table("users")
