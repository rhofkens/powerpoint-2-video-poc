# Increment 1: Project Setup & Infrastructure

## Overview
This increment establishes the foundational infrastructure for the PowerPoint to Video POC, including the monorepo structure, backend and frontend setup, database configuration, and basic connectivity between components.

## Detailed Scope

### Included Features and Tasks:

#### 1. Repository Setup
- Initialize Git repository with appropriate `.gitignore` for Java/Spring Boot and Node.js/React
- Create README.md with project overview and setup instructions
- Set up commit message conventions

#### 2. Monorepo Structure Creation
- Create directory structure as defined in architecture.md:
  - `backend/` - Spring Boot application
  - `frontend/` - React SPA  
  - `shared/` - Shared resources and API documentation
  - `scripts/` - Build and deployment scripts
  - `docs/` - Documentation (already partially created)
  - `storage/` - File storage for presentations and media (gitignored)

#### 3. Backend Infrastructure (Spring Boot 3.5.4 + Java 24)
- Initialize Spring Boot project using Spring Initializr with:
  - Spring Boot 3.5.4
  - Java 24
  - Maven as build tool
  - Dependencies: Spring Web, Spring Data JPA, PostgreSQL Driver, Spring Validation, Spring Configuration Processor
- Create package structure under `ai.bluefields.ppt2video`:
  - `controller/` - REST endpoints
  - `service/` - Business logic layer
  - `repository/` - Data access layer
  - `entity/` - JPA entities
  - `dto/` - Data transfer objects
  - `config/` - Configuration classes
  - `exception/` - Custom exceptions
- Configure `application.properties` with:
  - PostgreSQL connection settings
  - Server port configuration (8080)
  - Jackson JSON configuration
  - File upload size limits
- Implement basic health check endpoint (`GET /api/health`)
- Configure CORS for frontend communication

#### 4. Database Setup (PostgreSQL 17)
- PostgreSQL 17 is installed and configured locally
- database schema: `ppt2video_poc`
- database user: `ppt2video_db_user`
- database password: exported in env var PPT2VIDEOPOC_DB_PWD
- Implement initial JPA entities:
  - `Presentation` - Stores uploaded presentation metadata
  - `Slide` - Individual slide information
- Configure database connection pooling
- Set up development database initialization

#### 5. Frontend Infrastructure (React 18.x + Vite)
- Migrate existing Lovable UI codebase to `frontend/` directory
- Ensure proper Vite configuration for development
- Verify TypeScript configuration
- Set up environment variables for API endpoint configuration
- Configure proxy for backend communication during development
- Verify TailwindCSS and shadcn components are working

#### 6. State Management Setup
- Configure Zustand store structure with initial slices:
  - `presentationStore` - For managing presentation data
  - `uiStore` - For UI state management
- Create type definitions for store interfaces

#### 7. Development Environment Configuration
- Create `.env.example` files for both frontend and backend
- Document required environment variables
- Set up development scripts in root `package.json`:
  - `dev:backend` - Start Spring Boot application
  - `dev:frontend` - Start Vite dev server
  - `dev` - Start both concurrently

#### 8. Basic Integration Testing
- Verify frontend can communicate with backend health endpoint
- Test CORS configuration
- Ensure PostgreSQL connection is working
- Verify file structure matches architecture document

#### 9. GitHub Actions CI Setup
- Create `.github/workflows/` directory structure
- Configure CI workflow for backend:
  - Trigger on push and pull requests
  - Set up Java 24 environment
  - Run `mvn clean install`
  - Run `mvn spotless:check`
  - Cache Maven dependencies
- Configure CI workflow for frontend:
  - Trigger on push and pull requests
  - Set up Node.js environment
  - Run `npm ci`
  - Run `npm run lint`
  - Run `npm run build`
  - Cache npm dependencies
- Configure build status badges for README

#### 10. Pre-commit Hooks with Husky
- Install and configure Husky in the root directory
- Create pre-commit hook that runs:
  - Backend checks: `mvn spotless:apply` and `mvn test-compile`
  - Frontend checks: `npm run lint --prefix frontend`
- Configure commit-msg hook for conventional commits
- Document hook bypass instructions for emergencies
- Ensure hooks are automatically installed on `npm install`

### Explicitly Excluded:
- External API integrations (OpenAI, ElevenLabs, D-ID)
- Business logic implementation
- File upload functionality
- PowerPoint parsing logic
- Authentication/authorization
- Production deployment configuration
- Automated testing setup
- CI/CD pipeline configuration

## Detailed Acceptance Criteria

### Backend Criteria:
1. `mvn clean install` completes without errors
2. `mvn spotless:check` passes without formatting issues
3. `mvn spring-boot:run` starts application without errors
4. Health endpoint returns `{"status": "UP"}` at `GET /api/health`
5. PostgreSQL connection is established and verified
6. Database schema is created with Presentation and Slide tables
7. CORS is configured to accept requests from `http://localhost:5173`
8. Application properties are externalized and configurable

### Frontend Criteria:
1. `npm run lint` passes without errors
2. `npm run build` completes successfully
3. `npm run dev` starts application without errors on port 5173
4. All Lovable UI components render without errors
5. TailwindCSS styles are applied correctly
6. TypeScript compilation has no errors
7. Zustand store is initialized and accessible
8. Environment variables are loaded from `.env` file
9. Can make successful API call to backend health endpoint

### Integration Criteria:
1. Frontend successfully calls backend health endpoint without CORS errors
2. Both applications can be started with a single npm command
3. Development proxy is configured correctly
4. File structure exactly matches architecture.md specification

### Development Environment Criteria:
1. Clear setup instructions in README.md
2. All environment variables documented in `.env.example`
3. Developer can get system running with less than 5 commands
4. No hardcoded configuration values

### CI/CD Criteria:
1. GitHub Actions workflows successfully run on push to main branch
2. Backend CI completes with all checks passing
3. Frontend CI completes with all checks passing
4. Build status badges display correctly in README
5. CI runs complete in under 5 minutes

### Pre-commit Hook Criteria:
1. Husky installs automatically with `npm install` in root directory
2. Pre-commit hooks prevent commits with:
   - Backend: Spotless formatting violations
   - Frontend: ESLint errors
3. Developers can bypass hooks with `--no-verify` flag when needed
4. Commit message format is validated

## Detailed Documentation Tasks

### 1. Create Comprehensive Project README.md
The README should include all essential information in a concise format:
- **Project Overview**: Purpose and goals of the POC
- **Prerequisites**: System requirements (Java 24, Node.js, PostgreSQL 17)
- **Quick Start Guide**: Step-by-step setup instructions
- **Technology Stack**: Complete list with versions
- **Project Structure**: Directory layout explanation
- **Development Workflow**:
  - Git conventions and branching strategy
  - How to run backend and frontend together
  - Code formatting with Spotless (backend) and ESLint (frontend)
  - Database migration approach
- **Environment Configuration**:
  - All environment variables with descriptions
  - Example `.env` files
  - Configuration options
- **Common Commands**: Quick reference for development tasks
- **Troubleshooting**: Common issues and solutions
- **IDE Setup**: Recommendations for IntelliJ IDEA and VS Code

### 2. Create API Documentation Structure
- Set up `shared/api-docs/` directory
- Create template for REST endpoint documentation
- Document health check endpoint as example

## Notes
- This increment focuses purely on infrastructure setup with no business logic
- All external API integrations are deferred to later increments
- The goal is to establish a solid foundation for rapid development
- Manual testing approach aligns with POC requirements (no automated tests)