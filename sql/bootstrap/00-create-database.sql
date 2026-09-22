USE [master];
GO

IF N'$(APP_DATABASE_NAME)' = N''
BEGIN
    THROW 50000, 'APP_DATABASE_NAME must be provided.', 1;
END;
GO

DECLARE @DatabaseName sysname = N'$(APP_DATABASE_NAME)';

IF @DatabaseName LIKE N'%[^0-9A-Za-z_]%'
BEGIN
    THROW 50001, 'APP_DATABASE_NAME may contain only letters, numbers, and underscores.', 1;
END;

IF DB_ID(@DatabaseName) IS NULL
BEGIN
    DECLARE @CreateDatabaseSql nvarchar(max) =
        N'CREATE DATABASE ' + QUOTENAME(@DatabaseName) + N';';

    EXEC sys.sp_executesql @CreateDatabaseSql;
    PRINT N'Created database: ' + @DatabaseName;
END
ELSE
BEGIN
    PRINT N'Database already exists: ' + @DatabaseName;
END;
GO
