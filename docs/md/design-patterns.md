# Architecture Documentation

This folder contains architecture and design reference documents for Task Manager Pro.

---

## Design Patterns Reference

**File:** [`design-patterns.html`](./design-patterns.html)

A complete walkthrough of every design pattern found in the Task Manager Pro codebase. Covers all 8 patterns across 4 categories with visual diagrams, exact source code with syntax highlighting, and interview-ready explanations.

### Patterns covered

| # | Pattern | Category | Files |
|---|---------|----------|-------|
| 01 | Repository | Architectural | `TaskRepository.java` |
| 02 | MVC | Architectural | `Task.java`, `TaskController.java`, React frontend |
| 03 | Chain of Responsibility | Behavioral | `JwtAuthenticationFilter.java`, `SecurityConfig.java` |
| 04 | Strategy | Behavioral | `OAuth2AuthenticationSuccessHandler.java`, `JwtAuthenticationFilter.java` |
| 05 | Template Method | Behavioral | `JwtAuthenticationFilter.java`, `OAuth2LoginSuccessHandler.java` |
| 06 | Singleton | Structural | `JwtTokenProvider.java`, all `@Component` beans |
| 07 | Dependency Injection | Structural | `SecurityConfig.java`, `TaskController.java` |
| 08 | JWT + OAuth2 Token | Security | `JwtTokenProvider.java`, `OAuth2AuthenticationSuccessHandler.java` |

### How to view

Open `design-patterns.html` directly in any browser. The file is self-contained — no build step, no dependencies, no server required.

```bash
# From the repo root
open docs/architecture/design-patterns.html        # Mac
xdg-open docs/architecture/design-patterns.html   # Linux
start docs/architecture/design-patterns.html       # Windows
```

GitHub does not render HTML files inline. To view on GitHub, clone the repo and open the file locally, or enable GitHub Pages on the `docs/` folder under **Settings → Pages → Source → Deploy from branch → `/docs`**.

---

## Stack reference

| Layer | Technology |
|-------|-----------|
| Frontend | React (SPA) |
| Backend | Spring Boot 3, Java 17 |
| Database | MariaDB via Spring Data JPA |
| Auth | Google OAuth2 + Facebook OAuth2 + JWT (HMAC-SHA256) |
| Deployment | AWS (production) · GCP (dev/test) |
| Containerisation | Docker · docker-compose |

---

## Folder structure

```
docs/
└── architecture/
    ├── README.md                  ← this file
    └── design-patterns.html       ← full pattern reference
```

Add future architecture documents here — API design decisions, database schema, deployment diagrams, ADRs (Architecture Decision Records).
