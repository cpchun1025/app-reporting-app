using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using backend_dotnet;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;

var builder = WebApplication.CreateBuilder(args);
var settings = ApiSettings.FromConfiguration(builder.Configuration);
builder.Services.ConfigureHttpJsonOptions(options => options.SerializerOptions.PropertyNamingPolicy = System.Text.Json.JsonNamingPolicy.SnakeCaseLower);
builder.Services.AddSingleton(settings);
builder.Services.AddDbContext<TradingDbContext>(o => o.UseSqlServer(settings.ConnectionString));
builder.Services.AddSingleton<PasswordVerifier>();
builder.Services.AddCors(o => o.AddDefaultPolicy(p => p.WithOrigins(settings.CorsOrigins).AllowAnyHeader().AllowAnyMethod()));
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme).AddJwtBearer(o => o.TokenValidationParameters = new()
{
    ValidateIssuer = false, ValidateAudience = false, ValidateLifetime = true, ValidateIssuerSigningKey = true,
    IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(settings.JwtSecret)),
    NameClaimType = JwtRegisteredClaimNames.Sub
});
builder.Services.AddAuthorization();
var app = builder.Build();
app.UseCors(); app.UseAuthentication(); app.UseAuthorization();

app.MapGet("/health", () => Results.Ok(new { status = "ok" }));
app.MapGet("/health/live", () => Results.Ok(new { status = "ok" }));
app.MapGet("/health/ready", async (TradingDbContext db, CancellationToken ct) =>
{
    try { await db.Database.ExecuteSqlRawAsync("SELECT 1", ct); return Results.Ok(new { status = "ok" }); }
    catch (Exception) { return Results.Json(new { status = "unavailable" }, statusCode: 503); }
});
app.MapPost("/auth/login", async ([FromBody] LoginRequest request, TradingDbContext db, PasswordVerifier verifier, CancellationToken ct) =>
{
    var user = await db.Users.SingleOrDefaultAsync(x => x.Username == request.Username, ct);
    return user is null || !user.IsActive || !verifier.Verify(request.Password, user.PasswordHash)
        ? Results.Json(new { detail = "Incorrect username or password." }, statusCode: 401)
        : Results.Ok(new TokenResponse(Token(user, settings), "bearer"));
});
app.MapPost("/auth/mock/callback", async ([FromBody] MockCallbackRequest request, TradingDbContext db, CancellationToken ct) =>
{
    if (!settings.SeedDevUsers) return Results.NotFound();
    var user = await db.Users.SingleOrDefaultAsync(x => x.Username == request.Username, ct);
    return user is null || !user.IsActive ? Results.Json(new { detail = "Unknown or inactive user." }, statusCode: 401) : Results.Ok(new TokenResponse(Token(user, settings), "bearer"));
});
var api = app.MapGroup("").RequireAuthorization();
api.MapGet("/auth/me", async (ClaimsPrincipal principal, TradingDbContext db, CancellationToken ct) =>
{
    var user = await UserFor(principal, db, ct);
    return user is null ? UnavailableUser() : Results.Ok(new { username = user.Username, role = user.IsAdmin ? "admin" : "trader" });
});
api.MapPost("/trades", async ([FromBody] TradeInput input, ClaimsPrincipal principal, TradingDbContext db, CancellationToken ct) =>
{
    var user = await UserFor(principal, db, ct); if (user is null) return UnavailableUser();
    var trade = new Trade { Id = Guid.NewGuid().ToString(), TradeDate = input.TradeDate, Account = input.Account, Instrument = input.Instrument, Side = input.Side, Quantity = input.Quantity, Price = input.Price, Currency = input.Currency.ToUpperInvariant(), Status = input.Status, Notes = input.Notes, CreatedById = user.Id, CreatedAt = DateTime.UtcNow, UpdatedAt = DateTime.UtcNow };
    db.Trades.Add(trade); await db.SaveChangesAsync(ct); return Results.Created($"/trades/{trade.Id}", TradeDto.From(trade));
});
api.MapGet("/trades", async ([FromQuery] DateOnly? business_date, TradingDbContext db, CancellationToken ct) =>
{
    if (business_date is not null) { await EnsureEntries(db, business_date.Value, ct); var entries = await db.DailyTradeEntries.Include(x => x.Business).Where(x => x.BusinessDate == business_date).OrderBy(x => x.Business.Code).ToListAsync(ct); return Results.Ok(entries.Select(DailyEntryDto.From)); }
    var trades = await db.Trades.OrderByDescending(x => x.TradeDate).ToListAsync(ct); return Results.Ok(trades.Select(TradeDto.From));
});
api.MapGet("/trades/{id}", async (string id, TradingDbContext db, CancellationToken ct) => await db.Trades.FindAsync([id], ct) is { } t ? Results.Ok(TradeDto.From(t)) : Results.NotFound());
api.MapPut("/trades/{id}", async (string id, [FromBody] TradeUpdate input, ClaimsPrincipal p, TradingDbContext db, CancellationToken ct) =>
{
    var user = await UserFor(p, db, ct); var t = await db.Trades.FindAsync([id], ct); if (user is null) return UnavailableUser(); if (t is null) return Results.NotFound(); Clear(t);
    if (t.Version != input.ExpectedVersion || (t.Locked && t.LockedById != user.Id)) return Conflict("Trade has been changed or is locked.");
    t.TradeDate = input.TradeDate; t.Account = input.Account; t.Instrument = input.Instrument; t.Side = input.Side; t.Quantity = input.Quantity; t.Price = input.Price; t.Currency = input.Currency.ToUpperInvariant(); t.Status = input.Status; t.Notes = input.Notes; t.Version++; t.UpdatedAt = DateTime.UtcNow;
    await db.SaveChangesAsync(ct); return Results.Ok(TradeDto.From(t));
});
api.MapDelete("/trades/{id}", async (string id, [FromQuery] int expected_version, ClaimsPrincipal p, TradingDbContext db, CancellationToken ct) =>
{
    var user = await UserFor(p, db, ct); var t = await db.Trades.FindAsync([id], ct); if (user is null) return UnavailableUser(); if (t is null) return Results.NotFound(); Clear(t);
    if (t.Version != expected_version || (t.Locked && t.LockedById != user.Id)) return Conflict("Trade has been changed or is locked."); db.Remove(t); await db.SaveChangesAsync(ct); return Results.NoContent();
});
api.MapPost("/trades/{id}/lock", (string id, ClaimsPrincipal p, TradingDbContext db, CancellationToken ct) => LockTrade(id, true, p, db, ct));
api.MapPost("/trades/{id}/unlock", (string id, ClaimsPrincipal p, TradingDbContext db, CancellationToken ct) => LockTrade(id, false, p, db, ct));
api.MapGet("/trades/entry", async ([FromQuery] DateOnly business_date, TradingDbContext db, CancellationToken ct) =>
{
    await EnsureEntries(db, business_date, ct); var rows = await db.DailyTradeEntries.Include(x => x.Business).Where(x => x.BusinessDate == business_date).OrderBy(x => x.Business.Code).ToListAsync(ct); return Results.Ok(new { business_date, rows = rows.Select(DailyEntryDto.From) });
});
api.MapPost("/trades/entry/{entry_id}/lock", (string entry_id, ClaimsPrincipal p, TradingDbContext db, CancellationToken ct) => LockEntry(entry_id, true, p, db, ct));
api.MapPost("/trades/entry/{entry_id}/unlock", (string entry_id, ClaimsPrincipal p, TradingDbContext db, CancellationToken ct) => LockEntry(entry_id, false, p, db, ct));
api.MapPost("/trades/entry/save", async ([FromBody] System.Text.Json.JsonElement payload, ClaimsPrincipal p, TradingDbContext db, CancellationToken ct) =>
{
    var options = new System.Text.Json.JsonSerializerOptions { PropertyNamingPolicy = System.Text.Json.JsonNamingPolicy.SnakeCaseLower };
    if (payload.GetProperty("rows")[0].TryGetProperty("values", out _))
    {
        var snapshotRequest = System.Text.Json.JsonSerializer.Deserialize<TradeEntrySaveRequest>(payload.GetRawText(), options);
        if (snapshotRequest is null) return Validation("Invalid trade entry payload.");
        var snapshotUser = await UserFor(p, db, ct); if (snapshotUser is null) return UnavailableUser();
        var serialized = System.Text.Json.JsonSerializer.Serialize(snapshotRequest, options);
        var filename = $"{snapshotRequest.BusinessDate:yyyy-MM-dd}-{Guid.NewGuid():N}.json";
        try
        {
            Directory.CreateDirectory(settings.TradeSaveCopyPath);
            await File.WriteAllTextAsync(Path.Combine(settings.TradeSaveCopyPath, filename), serialized, ct);
            db.TradeEntrySnapshots.Add(new TradeEntrySnapshot { Id = Guid.NewGuid().ToString(), BusinessDate = snapshotRequest.BusinessDate, DataJson = serialized, SavedById = snapshotUser.Id, CreatedAt = DateTime.UtcNow });
            await db.SaveChangesAsync(ct);
            return Results.Ok(new { saved_at = DateTime.UtcNow, snapshot_filename = filename, row_count = snapshotRequest.Rows.Count });
        }
        catch (IOException) { return Results.Json(new { detail = "Trade data was not saved because the server backup copy could not be written." }, statusCode: 500); }
        catch (UnauthorizedAccessException) { return Results.Json(new { detail = "Trade data was not saved because the server backup copy could not be written." }, statusCode: 500); }
    }
    var request = System.Text.Json.JsonSerializer.Deserialize<DailySaveRequest>(payload.GetRawText(), options);
    if (request is null) return Validation("Invalid daily trade entry payload.");
    if (request.Rows.Select(x => x.Id).Distinct().Count() != request.Rows.Count) return Validation("Duplicate row ids are not allowed.");
    var user = await UserFor(p, db, ct); if (user is null) return UnavailableUser();
    await using var tx = await db.Database.BeginTransactionAsync(ct); var entries = await db.DailyTradeEntries.Include(x => x.Business).Where(x => request.Rows.Select(r => r.Id).Contains(x.Id)).ToListAsync(ct);
    if (entries.Count != request.Rows.Count) return Conflict("Daily entry does not exist.");
    foreach (var update in request.Rows) { var e = entries.Single(x => x.Id == update.Id); Clear(e); if (e.BusinessDate != request.BusinessDate || e.Version != update.ExpectedVersion || (e.Locked && e.LockedById != user.Id)) return Conflict("Daily entry has been changed or is locked."); e.Delta = update.Delta; e.Gamma = update.Gamma; e.Theta = update.Theta; e.Vega = update.Vega; e.Pnl = update.Pnl; e.Version++; e.UpdatedById = user.Id; e.UpdatedAt = DateTime.UtcNow; }
    db.TradeEntrySnapshots.Add(new TradeEntrySnapshot { Id = Guid.NewGuid().ToString(), BusinessDate = request.BusinessDate, DataJson = System.Text.Json.JsonSerializer.Serialize(request.Rows), SavedById = user.Id, CreatedAt = DateTime.UtcNow });
    await db.SaveChangesAsync(ct); await tx.CommitAsync(ct); return Results.Ok(new { saved_at = DateTime.UtcNow, business_date = request.BusinessDate, rows = entries.Select(DailyEntryDto.From) });
});
foreach (var pair in new[] { ("/reports/daily", "day"), ("/reports/monthly", "month"), ("/reports/annual", "year"), ("/reports/consolidated", "account") })
    api.MapGet(pair.Item1, ([FromQuery] DateOnly start_date, [FromQuery] DateOnly end_date, TradingDbContext db, CancellationToken ct) => Reports(db, start_date, end_date, pair.Item2, ct));
if (args.Contains("--seed", StringComparer.OrdinalIgnoreCase)) { using var scope = app.Services.CreateScope(); await SeedData.Run(scope.ServiceProvider, settings, CancellationToken.None); return; }
app.Run();

static string Token(User user, ApiSettings s) => new JwtSecurityTokenHandler().WriteToken(new JwtSecurityToken(claims: [new Claim(JwtRegisteredClaimNames.Sub, user.Id)], expires: DateTime.UtcNow.AddMinutes(s.AccessTokenExpireMinutes), signingCredentials: new(new SymmetricSecurityKey(Encoding.UTF8.GetBytes(s.JwtSecret)), SecurityAlgorithms.HmacSha256)));
static async Task<User?> UserFor(ClaimsPrincipal p, TradingDbContext db, CancellationToken ct) => await db.Users.FindAsync([p.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? ""], ct) is { IsActive: true } user ? user : null;
static IResult UnavailableUser() => Results.Json(new { detail = "Access token user is unavailable." }, statusCode: 401);
static IResult Conflict(string detail) => Results.Json(new { detail }, statusCode: 409);
static IResult Validation(string detail) => Results.Json(new { detail }, statusCode: 422);
static void Clear(ILockable l) { if (l.Locked && l.LockExpiresAt <= DateTime.UtcNow) { l.Locked = false; l.LockedById = null; l.LockedByDisplayName = null; l.LockedAt = null; l.LockExpiresAt = null; } }
static async Task<IResult> LockTrade(string id, bool locked, ClaimsPrincipal p, TradingDbContext db, CancellationToken ct) { var u = await UserFor(p, db, ct); var t = await db.Trades.FindAsync([id], ct); if (u is null) return UnavailableUser(); if (t is null) return Results.NotFound(); Clear(t); if (locked && t.Locked && t.LockedById != u.Id) return Conflict("Trade is locked by another user."); if (!locked && t.LockedById != u.Id && !u.IsAdmin) return Results.Json(new { detail = "Only the lock owner or an admin can unlock." }, statusCode: 403); t.Locked = locked; t.LockedById = locked ? u.Id : null; t.LockedByDisplayName = locked ? u.Username : null; t.LockedAt = locked ? DateTime.UtcNow : null; t.LockExpiresAt = locked ? DateTime.UtcNow.AddHours(8) : null; t.Version++; await db.SaveChangesAsync(ct); return Results.Ok(LockDto.From(t)); }
static async Task<IResult> LockEntry(string id, bool locked, ClaimsPrincipal p, TradingDbContext db, CancellationToken ct) { var u = await UserFor(p, db, ct); var e = await db.DailyTradeEntries.FindAsync([id], ct); if (u is null) return UnavailableUser(); if (e is null) return Results.NotFound(); Clear(e); if (locked && e.Locked && e.LockedById != u.Id) return Conflict("Daily trade entry is locked by another user."); if (!locked && e.LockedById != u.Id && !u.IsAdmin) return Results.Json(new { detail = "Only the lock owner or an administrator can unlock this daily trade entry." }, statusCode: 403); e.Locked = locked; e.LockedById = locked ? u.Id : null; e.LockedByDisplayName = locked ? u.Username : null; e.LockedAt = locked ? DateTime.UtcNow : null; e.LockExpiresAt = locked ? DateTime.UtcNow.AddHours(8) : null; e.Version++; await db.SaveChangesAsync(ct); return Results.Ok(LockDto.From(e)); }
static async Task EnsureEntries(TradingDbContext db, DateOnly date, CancellationToken ct) { var businesses = await db.TradingBusinesses.Where(x => x.IsActive).ToListAsync(ct); var existing = await db.DailyTradeEntries.Where(x => x.BusinessDate == date).Select(x => x.BusinessId).ToListAsync(ct); db.DailyTradeEntries.AddRange(businesses.Where(x => !existing.Contains(x.Id)).Select(x => new DailyTradeEntry { Id = Guid.NewGuid().ToString(), BusinessId = x.Id, BusinessDate = date, CreatedAt = DateTime.UtcNow, UpdatedAt = DateTime.UtcNow })); if (db.ChangeTracker.HasChanges()) await db.SaveChangesAsync(ct); }
static async Task<IResult> Reports(TradingDbContext db, DateOnly start, DateOnly end, string period, CancellationToken ct) { if (end < start) return Validation("end_date must be on or after start_date."); var trades = await db.Trades.Where(x => x.TradeDate >= start && x.TradeDate <= end).ToListAsync(ct); var rows = trades.GroupBy(x => period switch { "day" => x.TradeDate.ToString("yyyy-MM-dd"), "month" => x.TradeDate.ToString("yyyy-MM"), "year" => x.TradeDate.Year.ToString(), _ => x.Account }).Select(g => new { period = g.Key, trade_count = g.Count(), buy_quantity = g.Where(x => x.Side == "BUY").Sum(x => x.Quantity), sell_quantity = g.Where(x => x.Side == "SELL").Sum(x => x.Quantity), net_quantity = g.Sum(x => x.Side == "BUY" ? x.Quantity : -x.Quantity), gross_notional = g.Sum(x => x.Quantity * x.Price) }); return Results.Ok(new { start_date = start, end_date = end, rows }); }
public partial class Program { }
