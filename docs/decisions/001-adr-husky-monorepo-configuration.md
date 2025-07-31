# ADR-001: Husky Pre-commit Hooks Configuration for Monorepo

## Status
Accepted

## Context
The PowerPoint to Video POC uses a monorepo structure with both backend (Java/Spring Boot) and frontend (React/TypeScript) code. We need pre-commit hooks to ensure code quality and consistency across both codebases before commits are made.

The challenge is configuring Husky to work effectively with both Maven-based backend checks and npm-based frontend checks in a single repository.

## Decision
We will configure Husky at the root level of the monorepo with the following approach:

1. Install Husky in the root directory as a dev dependency
2. Create pre-commit hooks that run both backend and frontend checks:
   - Backend: Run `mvn spotless:apply` and `mvn test-compile` from the backend directory
   - Frontend: Run `npm run lint` from the frontend directory
3. Use a commit-msg hook to enforce conventional commit messages
4. Configure hooks to run in parallel where possible to reduce wait time
5. Provide clear bypass instructions using `--no-verify` flag for emergency situations

## Consequences
**Positive:**
- Unified pre-commit hook management for the entire monorepo
- Consistent code quality enforcement across both codebases
- Single installation and configuration point
- Developers cannot accidentally commit improperly formatted code

**Negative:**
- Slightly longer commit times due to running checks for both codebases
- Developers need both Java and Node.js environments set up even if working on only one part
- Potential for hooks to fail if dependencies are not installed in both backend and frontend

**Mitigation:**
- Document clear setup instructions in README
- Provide `--no-verify` bypass option for emergencies
- Consider future optimization to only run checks on changed files