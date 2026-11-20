# NovaCart High-Concurrency Verification

Test date: 2026-11-20

The test ran against the real Spring Boot services, MySQL, Redis and RabbitMQ.
The RabbitMQ timeout was temporarily configured to 5 seconds for the test, then
restored to the default 30 minutes.

| Scenario | Result |
|---|---|
| Initial stock | 40 |
| Concurrent order requests | 80 |
| Successful orders | 40 |
| Expected stockout/conflict responses | 40 |
| Unexpected failures | 0 |
| Oversold products | 0 |
| Throughput | 290.71 requests/s |
| Mean latency | 172.56 ms |
| P95 latency | 232.80 ms |
| Max latency | 256.11 ms |

Idempotency and timeout checks:

| Check | Result |
|---|---|
| 16 concurrent requests with one idempotency key | 1 distinct order ID |
| 16 concurrent requests with one payment callback ID | 16 successful responses, 1 final state |
| Payment callback database rows for that callback | 1 |
| RabbitMQ delayed order timeout | `PENDING_PAYMENT` → `CANCELLED` |
| Final stock after cleanup | Restored to 39 |

Reproduce:

```powershell
python scripts\high_concurrency_test.py `
  --stock 40 `
  --requests 80 `
  --timeout-wait 20
```

Use `NOVACART_ORDER_TIMEOUT_MS=5000` when starting `novacart-mall` for a short
timeout test. Remove the temporary timeout queue before restarting with the
production-style 30-minute setting.
