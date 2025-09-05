# Claude AI Assistant Preferences

## Git Commit Messages

When creating git commits, DO NOT include references to Claude or AI generation in the commit message. Keep commit messages professional and focused solely on the technical changes made.

### Example:
```
fix: properly check R2 presigned URL expiration before refreshing

- Fixed URL expiration check to parse actual expiration time from presigned URL
- Only refresh URLs that are actually expired or within 5 minutes of expiring
```

## Linting and Code Quality

Before committing, always run:
- `mvn spotless:apply` to fix formatting issues
- `npm run lint` if working on frontend code
- `npm run typecheck` if TypeScript is involved