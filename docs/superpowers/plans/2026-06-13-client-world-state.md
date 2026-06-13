# Client World State Implementation Plan

1. Add API tests for normalization, stack restoration, covered-session close,
   updates, idempotent close, and missing backend behavior.
2. Implement immutable state values, DSL builder, backend SPI, session stack,
   and player extension in `arc-api`.
3. Implement reflective time/weather packet construction and real-state restore
   in `arc-server`.
4. Register the backend from `ArcBootstrap`.
5. Document the public API and run focused plus integrated verification.
