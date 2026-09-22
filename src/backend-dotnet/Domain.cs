namespace backend_dotnet;
using Microsoft.EntityFrameworkCore;
public sealed class TradingDbContext(DbContextOptions<TradingDbContext> options) : DbContext(options)
{
    public DbSet<User> Users => Set<User>(); public DbSet<Trade> Trades => Set<Trade>(); public DbSet<TradingBusiness> TradingBusinesses => Set<TradingBusiness>(); public DbSet<DailyTradeEntry> DailyTradeEntries => Set<DailyTradeEntry>(); public DbSet<TradeEntrySnapshot> TradeEntrySnapshots => Set<TradeEntrySnapshot>();
    protected override void OnModelCreating(ModelBuilder b)
    {
        b.Entity<User>().ToTable("users").HasKey(x => x.Id); b.Entity<User>().HasIndex(x => x.Username).IsUnique();
        b.Entity<Trade>().ToTable("trades").HasKey(x => x.Id); b.Entity<Trade>().Property(x => x.Quantity).HasPrecision(18, 4); b.Entity<Trade>().Property(x => x.Price).HasPrecision(18, 4);
        b.Entity<TradingBusiness>().ToTable("trading_businesses").HasKey(x => x.Id);
        b.Entity<DailyTradeEntry>().ToTable("daily_trade_entries").HasKey(x => x.Id); b.Entity<DailyTradeEntry>().HasIndex(x => new { x.BusinessId, x.BusinessDate }).IsUnique().HasDatabaseName("uq_daily_trade_entries_business_date"); b.Entity<DailyTradeEntry>().HasOne(x => x.Business).WithMany().HasForeignKey(x => x.BusinessId);
        b.Entity<TradeEntrySnapshot>().ToTable("trade_entry_snapshots").HasKey(x => x.Id);
        foreach (var entity in b.Model.GetEntityTypes())
            foreach (var property in entity.GetProperties())
                property.SetColumnName(ToSnakeCase(property.Name));
    }
    private static string ToSnakeCase(string value) => string.Concat(value.Select((character, index) => index > 0 && char.IsUpper(character) ? $"_{char.ToLowerInvariant(character)}" : char.ToLowerInvariant(character).ToString()));
}
public interface ILockable { bool Locked { get; set; } string? LockedById { get; set; } string? LockedByDisplayName { get; set; } DateTime? LockedAt { get; set; } DateTime? LockExpiresAt { get; set; } }
public class User { public string Id { get; set; } = ""; public string Username { get; set; } = ""; public string PasswordHash { get; set; } = ""; public bool IsActive { get; set; } = true; public bool IsAdmin { get; set; } public DateTime CreatedAt { get; set; } }
public class Trade : ILockable { public string Id { get; set; } = ""; public DateOnly TradeDate { get; set; } public string Account { get; set; } = ""; public string Instrument { get; set; } = ""; public string Side { get; set; } = ""; public decimal Quantity { get; set; } public decimal Price { get; set; } public string Currency { get; set; } = "USD"; public string Status { get; set; } = "BOOKED"; public string? Notes { get; set; } public int Version { get; set; } = 1; public string CreatedById { get; set; } = ""; public DateTime CreatedAt { get; set; } public DateTime UpdatedAt { get; set; } public bool Locked { get; set; } public string? LockedById { get; set; } public string? LockedByDisplayName { get; set; } public DateTime? LockedAt { get; set; } public DateTime? LockExpiresAt { get; set; } }
public class TradingBusiness { public string Id { get; set; } = ""; public string Code { get; set; } = ""; public string Name { get; set; } = ""; public bool IsActive { get; set; } = true; }
public class DailyTradeEntry : ILockable { public string Id { get; set; } = ""; public string BusinessId { get; set; } = ""; public TradingBusiness Business { get; set; } = null!; public DateOnly BusinessDate { get; set; } public decimal Delta { get; set; } public decimal Gamma { get; set; } public decimal Theta { get; set; } public decimal Vega { get; set; } public decimal Pnl { get; set; } public int Version { get; set; } = 1; public string? CreatedById { get; set; } public string? UpdatedById { get; set; } public DateTime CreatedAt { get; set; } public DateTime UpdatedAt { get; set; } public bool Locked { get; set; } public string? LockedById { get; set; } public string? LockedByDisplayName { get; set; } public DateTime? LockedAt { get; set; } public DateTime? LockExpiresAt { get; set; } }
public class TradeEntrySnapshot { public string Id { get; set; } = ""; public DateOnly BusinessDate { get; set; } public string DataJson { get; set; } = ""; public string SavedById { get; set; } = ""; public DateTime CreatedAt { get; set; } }
