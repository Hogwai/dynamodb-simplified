# Contributing

## Commit convention

We follow [Conventional Commits](https://www.conventionalcommits.org/) with scopes:

| Type | Scope | Description |
|------|-------|-------------|
| `feat(core)` | core | New feature |
| `fix(core)` | core | Bug fix |
| `feat!` | any | Breaking change (major bump) |
| `docs` | — | Documentation only |
| `refactor` | core | Refactoring, no functional change |
| `test` | core | Adding or fixing tests |
| `chore(deps)` | deps | Dependency updates |
| `chore(ci)` | ci | CI/CD changes |

Format: `type(scope): description` (max 100 chars)

## Branch naming

```
type/description
```

Examples: `feat/batch-delete`, `fix/empty-batch`, `docs/quickstart-ttl`

## Pull request process

1. Create a branch from `main`
2. Make your changes
3. Open a PR with a conventional commit title
4. Ensure CI passes (`./gradlew check`)
5. Request review
6. Squash merge to `main`

The PR title will be validated by CI.

## Release process

1. Merge feature/fix PRs to `main` as usual
2. [release-please](https://github.com/googleapis/release-please) maintains a Release PR with:
   - Auto-generated `CHANGELOG.md`
   - Updated version number
3. Review the Release PR and merge it when ready
4. A GitHub Release is created automatically
5. The existing publish workflow publishes to Maven Central

## Local development

```sh
# Full check (tests + style + coverage)
./gradlew check

# Unit tests only
./gradlew :dynamodb-simplified-core:test

# Integration tests (requires Docker)
./gradlew :dynamodb-simplified-core:integrationTest

# Local DynamoDB
docker compose up
```
