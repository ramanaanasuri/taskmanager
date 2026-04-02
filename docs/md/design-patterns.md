# Design Patterns Reference

A complete walkthrough of every design pattern found in the Task Manager Pro codebase — with visual diagrams, exact source code with syntax highlighting, and interview-ready explanations.

---

## Visual reference document

**File:** [`taskmanager-design-patterns.html`](../html-docs/taskmanager-design-patterns.html)

Covers all 8 patterns across 4 categories. Open it locally in any browser — self-contained, no build step, no dependencies required.

```bash
# From the repo root
start docs\html-docs\taskmanager-design-patterns.html       # Windows
open docs/html-docs/taskmanager-design-patterns.html        # Mac
xdg-open docs/html-docs/taskmanager-design-patterns.html   # Linux
```

---

## Patterns covered

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

## Docs folder structure

```
docs/
├── md/
│   └── design-patterns.md              ← this file
├── html-docs/
│   └── taskmanager-design-patterns.html ← full visual reference
└── pdfs/
```
