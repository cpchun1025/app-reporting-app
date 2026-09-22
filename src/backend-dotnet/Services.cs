namespace backend_dotnet;
using System.Security.Cryptography;
using Microsoft.EntityFrameworkCore;
public sealed record ApiSettings(string ConnectionString, string JwtSecret, int AccessTokenExpireMinutes, bool SeedDevUsers, string[] CorsOrigins, string? DevAdminPassword, string? DevTraderPassword, string TradeSaveCopyPath)
{
    public static ApiSettings FromConfiguration(IConfiguration c) => new(c["DATABASE_URL"] ?? $"Server={c["MSSQL_HOST"] ?? "localhost"},{c["MSSQL_PORT"] ?? "1433"};Database={c["APP_DATABASE_NAME"] ?? "trading_reporting"};User Id={c["MSSQL_SA_USER"] ?? "sa"};Password={c["MSSQL_SA_PASSWORD"]};Encrypt={c["MSSQL_ENCRYPT"] ?? "true"};TrustServerCertificate={c["MSSQL_TRUST_SERVER_CERTIFICATE"] ?? "true"}", c["JWT_SECRET"] ?? "local-development-secret-change-me", int.TryParse(c["ACCESS_TOKEN_EXPIRE_MINUTES"], out var expiry) ? expiry : 60, !string.Equals(c["SEED_DEV_USERS"], "false", StringComparison.OrdinalIgnoreCase), (c["CORS_ORIGINS"] ?? "http://localhost:5173").Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries), c["DEV_ADMIN_PASSWORD"], c["DEV_TRADER_PASSWORD"], c["TRADE_SAVE_COPY_PATH"] ?? "./trade-entry-copies");
}
public sealed class PasswordVerifier
{
    public bool Verify(string password, string encoded)
    {
        if (!encoded.StartsWith("$pbkdf2-sha256$", StringComparison.Ordinal)) return false;
        var parts = encoded.Split('$'); if (parts.Length != 5 || !int.TryParse(parts[2], out var rounds)) return false;
        var salt = DecodePasslibBase64(parts[3]); var expected = DecodePasslibBase64(parts[4]);
        var actual = Rfc2898DeriveBytes.Pbkdf2(password, salt, rounds, HashAlgorithmName.SHA256, expected.Length);
        return CryptographicOperations.FixedTimeEquals(actual, expected);
    }

    public string Hash(string password)
    {
        var salt = RandomNumberGenerator.GetBytes(16);
        var checksum = Rfc2898DeriveBytes.Pbkdf2(password, salt, 29000, HashAlgorithmName.SHA256, 32);
        return $"$pbkdf2-sha256$29000${EncodePasslibBase64(salt)}${EncodePasslibBase64(checksum)}";
    }

    private static byte[] DecodePasslibBase64(string value) => Convert.FromBase64String(value.Replace('.', '+').PadRight((value.Length + 3) / 4 * 4, '='));
    private static string EncodePasslibBase64(byte[] value) => Convert.ToBase64String(value).TrimEnd('=').Replace('+', '.');
}
public static class SeedData
{
    public static async Task Run(IServiceProvider services, ApiSettings settings, CancellationToken ct)
    {
        if (!settings.SeedDevUsers) throw new InvalidOperationException("SEED_DEV_USERS must be true to run the explicit development seed command.");
        if (string.IsNullOrWhiteSpace(settings.DevAdminPassword) || string.IsNullOrWhiteSpace(settings.DevTraderPassword)) throw new InvalidOperationException("DEV_ADMIN_PASSWORD and DEV_TRADER_PASSWORD are required.");
        var db = services.GetRequiredService<TradingDbContext>();
        var passwords = services.GetRequiredService<PasswordVerifier>();
        foreach (var (username, password, isAdmin) in new[] { ("dev_admin", settings.DevAdminPassword, true), ("dev_trader", settings.DevTraderPassword, false) })
            if (!await db.Users.AnyAsync(x => x.Username == username, ct)) db.Users.Add(new User { Id = Guid.NewGuid().ToString(), Username = username, PasswordHash = passwords.Hash(password), IsAdmin = isAdmin, IsActive = true, CreatedAt = DateTime.UtcNow });
        foreach (var (code, name) in new[] { ("ALPHA", "Alpha"), ("BETA", "Beta"), ("GAMMA", "Gamma"), ("DELTA", "Delta"), ("EPSILON", "Epsilon"), ("ZETA", "Zeta"), ("ETA", "Eta"), ("THETA", "Theta"), ("IOTA", "Iota"), ("KAPPA", "Kappa"), ("LAMBDA", "Lambda"), ("MU", "Mu") })
            if (!await db.TradingBusinesses.AnyAsync(x => x.Code == code, ct)) db.TradingBusinesses.Add(new TradingBusiness { Id = Guid.NewGuid().ToString(), Code = code, Name = name, IsActive = true });
        await db.SaveChangesAsync(ct);
    }
}
