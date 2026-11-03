## 2026.11.4

- **Security:** the Flux Converter's screen now checks that you are still next to it before it changes a side, like every other AeroFlux screen already did.
- **Security:** the recipe-sync message the server sends to players now refuses a list that claims an absurd length, so a broken or hostile server cannot make a client run out of memory.
- A code review of the packets, screens, claims, area tools and saved data found nothing else to change. A saved tank whose fluid comes from a mod you removed loads as an empty tank (now covered by a test).
