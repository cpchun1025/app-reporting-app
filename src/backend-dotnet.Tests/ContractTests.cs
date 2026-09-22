using System.Text.Json;
using backend_dotnet;

namespace backend_dotnet.Tests;

public sealed class ContractTests
{
    [Fact]
    public void Verifies_and_hashes_exact_passlib_pbkdf2_sha256_format()
    {
        const string password = "correct horse battery staple";
        var verifier = new PasswordVerifier();
        var hash = verifier.Hash(password);

        Assert.StartsWith("$pbkdf2-sha256$29000$", hash);
        Assert.True(verifier.Verify(password, hash));
        Assert.False(verifier.Verify("wrong-password", hash));
        Assert.True(verifier.Verify(password, "$pbkdf2-sha256$29000$LwWgVAqhdG5t7X3vnbP2/g$Kvp1UKKFudwgl1uVpnoE9mKtObRsg6fSMH5fRMTy4k4"));
    }

    [Fact]
    public void Trade_response_serializes_with_python_contract_property_names()
    {
        var dto = TradeDto.From(new Trade
        {
            Id = "trade-1", TradeDate = new DateOnly(2026, 9, 22), Account = "ACC",
            Instrument = "EURUSD", Side = "BUY", Quantity = 10, Price = 1.25m,
            Currency = "USD", Status = "BOOKED", CreatedById = "user-1",
            CreatedAt = DateTime.UnixEpoch, UpdatedAt = DateTime.UnixEpoch
        });

        using var document = JsonDocument.Parse(JsonSerializer.Serialize(dto, new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower
        }));

        Assert.Equal("trade-1", document.RootElement.GetProperty("id").GetString());
        Assert.Equal("2026-09-22", document.RootElement.GetProperty("trade_date").GetString());
        Assert.Equal(1, document.RootElement.GetProperty("version").GetInt32());
    }
}
