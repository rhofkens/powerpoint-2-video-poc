# ADR-002: GitHub Actions Workflow Structure for Monorepo

## Status
Accepted

## Context
The PowerPoint to Video POC uses a monorepo structure with both backend (Java/Spring Boot) and frontend (React/TypeScript) applications. We need to configure GitHub Actions CI workflows that efficiently handle both codebases while avoiding unnecessary builds when only one part changes.

## Decision
We will implement separate workflow files for backend and frontend CI pipelines with the following structure:

1. Create two workflow files:
   - `.github/workflows/backend-ci.yml` - For Java/Spring Boot CI
   - `.github/workflows/frontend-ci.yml` - For React/TypeScript CI

2. Use path filters to trigger workflows only when relevant files change:
   - Backend workflow triggers on changes to `backend/**`, `pom.xml`, and backend workflow file
   - Frontend workflow triggers on changes to `frontend/**`, `package*.json`, and frontend workflow file

3. Both workflows trigger on:
   - Push to main branch
   - Pull requests targeting main branch

4. Share common configuration through workflow-level environment variables

## Consequences
**Positive:**
- Efficient CI execution - only runs tests for changed components
- Parallel execution of backend and frontend checks
- Clear separation of concerns
- Faster feedback for developers
- Reduced CI minutes usage

**Negative:**
- Slightly more complex workflow configuration
- Need to maintain two separate workflow files
- Potential for missing integration issues if changes affect both parts

**Mitigation:**
- Add a future integration test workflow that runs when both parts change
- Document the workflow structure clearly in README
- Use clear naming conventions for workflows