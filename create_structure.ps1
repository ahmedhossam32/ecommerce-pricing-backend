$basePath = "E:\Uni\GP\ecommerce-backend\src\main\java\com\ecommerce"

# Create main packages
@(
    "$basePath\config",
    "$basePath\controller",
    "$basePath\repository",
    "$basePath\entity",
    "$basePath\client",
    "$basePath\exception",
    "$basePath\util",
    "$basePath\service\auth",
    "$basePath\service\product",
    "$basePath\service\order",
    "$basePath\service\cart",
    "$basePath\service\seller",
    "$basePath\service\pricing",
    "$basePath\dto\request",
    "$basePath\dto\response"
) | ForEach-Object {
    New-Item -ItemType Directory -Path $_ -Force | Out-Null
}

Write-Host "✓ All folders created successfully!"

