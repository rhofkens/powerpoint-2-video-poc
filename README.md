# PowerPoint to Video Story POC

![Backend CI](https://github.com/your-org/powerpoint-2-video-poc/workflows/Backend%20CI/badge.svg)
![Frontend CI](https://github.com/your-org/powerpoint-2-video-poc/workflows/Frontend%20CI/badge.svg)

## Overview

The PowerPoint to Video Story POC is an AI-powered application that converts PowerPoint presentations into engaging narrative-styled videos. The system analyzes slide content, generates contextual narratives with configurable emotional tones (business, funny, cynical), and produces videos with AI-generated avatars and voiceovers.

### Key Features

- **PowerPoint Upload & Parsing**: Extract content and images from PPTX files
- **AI Content Analysis**: Leverage OpenAI GPT-4o-mini for slide content understanding
- **Narrative Generation**: Create compelling stories with configurable tones
- **Text-to-Speech**: Generate natural voiceovers using ElevenLabs API
- **Avatar Videos**: Create lip-synced avatar videos with D-ID API
- **Real-time Processing**: Track progress through the entire pipeline

## Technology Stack

### Backend
- **Spring Boot** 3.5.4 - Application framework
- **Java** 24 - Programming language with modern features
- **Spring AI** 1.0.1 - AI/LLM integration
- **PostgreSQL** 17 - Primary database
- **Maven** - Build and dependency management

### Frontend
- **React** 18.x - Component-based UI framework
- **TypeScript** - Type safety for JavaScript
- **Vite** - Build tool and development server
- **TailwindCSS** 3.x - Utility-first CSS framework
- **shadcn/ui** - Pre-built UI components
- **Zustand** - State management

### External Services
- **OpenAI GPT-4o-mini** - Content analysis and narrative generation
- **ElevenLabs API** - Text-to-speech generation
- **D-ID API** - Avatar video generation

## Prerequisites

Before setting up the project, ensure you have the following installed:

- **Java 24** or later
- **Node.js** 18.0.0 or later
- **npm** 8.0.0 or later
- **PostgreSQL** 17
- **Git**

### Database Setup

1. Install PostgreSQL 17:
   ```bash
   # macOS (using Homebrew)
   brew install postgresql@17
   brew services start postgresql@17
   
   # Ubuntu/Debian
   sudo apt-get install postgresql-17
   
   # Windows - Download from https://www.postgresql.org/download/windows/
   ```

2. Create database and user:
   ```sql
   CREATE DATABASE ppt2video_poc;
   CREATE USER ppt2video_db_user WITH PASSWORD 'your_secure_password';
   GRANT ALL PRIVILEGES ON DATABASE ppt2video_poc TO ppt2video_db_user;
   ```

## Quick Start

### 1. Clone the Repository
```bash
git clone https://github.com/your-org/powerpoint-2-video-poc.git
cd powerpoint-2-video-poc
```

### 2. Environment Configuration
```bash
# Copy environment template
cp .env.example .env

# Edit .env file with your configuration
nano .env
```

Required environment variables:
```bash
# Database password
PPT2VIDEOPOC_DB_PWD=your_postgres_password_here

# API configuration (for frontend)
VITE_API_URL=http://localhost:8080/api
```

### 3. Install Dependencies
```bash
# Install root dependencies and frontend dependencies
npm run install:all
```

### 4. Start Development Environment
```bash
# Start both backend and frontend concurrently
npm run dev
```

This will start:
- Backend API server at http://localhost:8080
- Frontend development server at http://localhost:5173

### 5. Verify Setup
- Open http://localhost:5173 in your browser
- Check that the health endpoint responds: http://localhost:8080/api/health

## Project Structure

```
powerpoint-2-video-poc/
├── backend/                    # Spring Boot backend application
│   ├── src/main/java/ai/bluefields/ppt2video/
│   │   ├── controller/         # REST endpoints
│   │   ├── service/            # Business logic
│   │   ├── repository/         # Data access layer
│   │   ├── entity/             # JPA entities
│   │   ├── dto/                # Data transfer objects
│   │   ├── config/             # Configuration classes
│   │   └── exception/          # Custom exceptions
│   └── pom.xml                 # Maven configuration
├── frontend/                   # React SPA application
│   ├── src/
│   │   ├── components/         # Reusable UI components
│   │   ├── pages/              # Page-level components
│   │   ├── hooks/              # Custom React hooks
│   │   ├── services/           # API communication
│   │   ├── store/              # Zustand state management
│   │   ├── types/              # TypeScript definitions
│   │   └── utils/              # Utility functions
│   └── package.json            # Frontend dependencies
├── shared/                     # Shared resources
│   └── api-docs/               # API documentation
├── scripts/                    # Build and deployment scripts
├── storage/                    # File storage (gitignored)
└── docs/                       # Documentation
```

## Development Workflow

### Running Individual Services

```bash
# Backend only
npm run dev:backend

# Frontend only  
npm run dev:frontend
```

### Code Formatting

The project uses automated code formatting:

```bash
# Check formatting
npm run lint

# Fix formatting issues
npm run format
```

### Building for Production

```bash
# Build both backend and frontend
npm run build

# Build individual services
npm run build:backend
npm run build:frontend
```

### Database Operations

The application uses JPA with automatic schema generation. On first run, tables will be created automatically.

To reset the database:
```sql
DROP DATABASE ppt2video_poc;
CREATE DATABASE ppt2video_poc;
GRANT ALL PRIVILEGES ON DATABASE ppt2video_poc TO ppt2video_db_user;
```

## API Documentation

### Health Check
- **GET** `/api/health` - Returns service status

Example response:
```json
{
  "status": "UP"
}
```

Additional API documentation is available in the `shared/api-docs/` directory.

## Git Workflow

### Commit Message Format
This project uses conventional commits:

```
type(scope): description

# Examples:
feat: add presentation upload functionality
fix(backend): correct database connection issue
docs: update setup instructions
```

Valid types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

### Pre-commit Hooks
Husky automatically runs checks before commits:
- **Backend**: Spotless formatting, compilation check
- **Frontend**: ESLint, TypeScript validation

To bypass hooks (emergency only):
```bash
git commit --no-verify -m "emergency fix"
```

## Common Commands

```bash
# Development
npm run dev                     # Start both services
npm run dev:backend            # Start backend only
npm run dev:frontend           # Start frontend only

# Building
npm run build                  # Build both services
npm run build:backend          # Build backend only
npm run build:frontend         # Build frontend only

# Code Quality
npm run lint                   # Check code formatting
npm run format                 # Fix formatting issues
npm run lint:backend           # Backend checks only
npm run lint:frontend          # Frontend checks only

# Maintenance
npm run clean                  # Clean all build artifacts
npm run install:all            # Install all dependencies
```

## Troubleshooting

### Database Connection Issues
```bash
# Check if PostgreSQL is running
pg_isready -h localhost -p 5432

# Start PostgreSQL (macOS)
brew services start postgresql@17

# Check connection with specific user
psql -h localhost -U ppt2video_db_user -d ppt2video_poc
```

### Port Already in Use
```bash
# Find process using port 8080 (backend)
lsof -ti:8080

# Find process using port 5173 (frontend)  
lsof -ti:5173

# Kill process by PID
kill -9 <PID>
```

### Maven Issues
```bash
# Clear Maven cache
mvn dependency:purge-local-repository

# Rebuild backend
cd backend && mvn clean install
```

### Node.js Issues
```bash
# Clear npm cache
npm cache clean --force

# Delete node_modules and reinstall
cd frontend && rm -rf node_modules package-lock.json && npm install
```

## IDE Setup

### IntelliJ IDEA (Recommended for Backend)
1. Install Java 24 support plugin
2. Enable annotation processing for Lombok
3. Configure code style: Import `backend/.editorconfig`
4. Install Spotless plugin for formatting

### VS Code (Recommended for Frontend)
1. Install recommended extensions:
   - TypeScript and JavaScript Language Features
   - ESLint
   - Prettier
   - Tailwind CSS IntelliSense
2. Configure workspace settings in `.vscode/settings.json`

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature-name`
3. Make your changes
4. Run tests and formatting: `npm run lint`
5. Commit using conventional format: `git commit -m "feat: your description"`
6. Push to your fork and submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.
