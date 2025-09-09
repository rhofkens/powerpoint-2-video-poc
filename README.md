# PowerPoint to Video POC

![Build Status](https://github.com/rhofkens/powerpoint-2-video-poc/actions/workflows/full-ci.yml/badge.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)
![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)
![Maintained](https://img.shields.io/badge/Maintained%3F-yes-green.svg)
![Java](https://img.shields.io/badge/Java-24-007396?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.8-6DB33F?style=flat&logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?style=flat&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5.5-3178C6?style=flat&logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?style=flat&logo=postgresql&logoColor=white)
![OpenAI](https://img.shields.io/badge/OpenAI-GPT--5_Mini-412991?style=flat&logo=openai&logoColor=white)
![Google AI](https://img.shields.io/badge/Google_AI-Veo_3.0-4285F4?style=flat&logo=google&logoColor=white)
![Code Style](https://img.shields.io/badge/code_style-spotless-brightgreen.svg)
![Linting](https://img.shields.io/badge/linting-ESLint-4B32C3.svg)
![Cloudflare](https://img.shields.io/badge/Cloudflare-R2-F38020?style=flat&logo=cloudflare&logoColor=white)
![Node Version](https://img.shields.io/badge/node-%3E%3D18-brightgreen)

## Overview

AI-powered platform that transforms PowerPoint presentations into professional narrative videos with generated avatars, voiceovers, and cinematic intro sequences.

### Key Features

- **Intelligent Content Analysis**: Multi-model AI analysis (GPT-5 Mini, Claude 3.5) for comprehensive slide understanding
- **Narrative Generation**: Context-aware storytelling with emotional tone optimization
- **Multi-Engine TTS**: ElevenLabs integration with emotional enhancement for natural speech
- **Avatar Generation**: D-ID API for lip-synced presenter videos
- **Intro Video Creation**: Google Veo API for AI-generated cinematic intros
- **Asset Management**: Cloudflare R2 storage with automatic publishing pipeline
- **Preflight System**: Comprehensive validation before video generation
- **Real-time Monitoring**: WebSocket-based progress tracking

## Technology Stack

### Backend
- **Spring Boot 3.4.8** / **Java 24** - Modern JVM application framework
- **PostgreSQL 17** - Primary database with Flyway migrations
- **Spring AI 1.0.1** - Multi-model AI orchestration
- **Apache POI** - PowerPoint parsing
- **ImageMagick** - Slide rendering

### Frontend
- **React 18** / **TypeScript** - Type-safe component architecture
- **Vite** - Fast HMR development
- **TailwindCSS** / **shadcn/ui** - Modern UI components
- **Zustand** - Lightweight state management

### AI/ML Services
- **OpenAI GPT-5 Mini** - Primary text generation & reasoning
- **OpenAI GPT-4o** - Vision analysis for slides
- **Claude 3.5 Sonnet** - Advanced reasoning & optimization
- **ElevenLabs** - Neural text-to-speech with emotion
- **D-ID** - Photorealistic avatar videos
- **Google Veo 3.0** - AI video generation for intros

### Infrastructure
- **Cloudflare R2** - S3-compatible object storage
- **Flyway** - Database version control
- **Docker** - Containerization (optional)

## Prerequisites

- **Java 24+**
- **Node.js 18+** / **npm 8+**
- **PostgreSQL 17**
- **ImageMagick** (for slide rendering)
- **Git**

## Quick Start

### 1. Database Setup
```sql
CREATE DATABASE ppt2video_poc;
CREATE USER ppt2video_db_user WITH PASSWORD 'powerpoint2video';
GRANT ALL PRIVILEGES ON DATABASE ppt2video_poc TO ppt2video_db_user;
```

### 2. Environment Configuration
Create `backend/src/main/resources/application-local.properties`:
```properties
# Database
spring.datasource.password=powerpoint2video

# Required API Keys
openai.api.key=sk-...              # GPT-5 Mini & GPT-4o access
elevenlabs.api.key=...
did.api.key=...
google.gemini.api.key=...          # For Gemini & Veo APIs

# Cloudflare R2
cloudflare.r2.access-key-id=...
cloudflare.r2.secret-access-key=...
cloudflare.r2.bucket-name=ppt2video
cloudflare.r2.endpoint=https://[account-id].r2.cloudflarestorage.com

# Optional: Claude API
spring.ai.anthropic.api-key=sk-ant-...
```

### 3. Install & Run
```bash
# Clone repository
git clone https://github.com/your-org/powerpoint-2-video-poc.git
cd powerpoint-2-video-poc

# Install dependencies
npm run install:all

# Start development servers
npm run dev
```

Access at:
- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
- Health Check: http://localhost:8080/api/health

## Key API Endpoints

```
POST   /api/presentations/upload          # Upload PowerPoint
GET    /api/presentations/{id}            # Get presentation details
POST   /api/ai-analysis/analyze/{id}      # Trigger AI analysis
POST   /api/narratives/generate/{id}      # Generate narratives
POST   /api/narratives/enhance/{id}       # Enhance with emotions
POST   /api/speech/generate-all/{id}      # Generate all audio
POST   /api/avatar/generate-all/{id}      # Generate avatar videos
POST   /api/intro-video/generate          # Generate intro video
POST   /api/preflight/{id}/run            # Run preflight check
GET    /api/assets/{id}/publish/{type}    # Publish to R2
```

## Development Commands

```bash
# Development
npm run dev                  # Start both services
npm run dev:backend         # Backend only
npm run dev:frontend        # Frontend only

# Code Quality
npm run lint                # Check formatting
npm run format              # Auto-fix issues
mvn spotless:apply          # Format Java code

# Building
npm run build               # Build all
mvn clean package           # Backend JAR
npm run build:frontend     # Frontend dist

# Database
mvn flyway:migrate          # Run migrations
mvn flyway:clean           # Reset database
```

## Configuration Reference

### Required Services
1. **OpenAI API** - GPT-5 Mini & GPT-4o access
2. **ElevenLabs** - Voice generation credits
3. **D-ID** - Avatar video credits
4. **Google AI Studio** - Gemini & Veo 3.0 access
5. **Cloudflare R2** - Storage bucket

### Optional Services
- **Anthropic Claude** - Enhanced analysis
- **PostgreSQL SSL** - Production database

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

## Processing Pipeline

1. **Upload** → Parse PPTX, extract content
2. **Analysis** → AI understands slides, identifies themes
3. **Narrative** → Generate contextual story
4. **Enhancement** → Add emotional markers for TTS
5. **Audio** → Generate voice with ElevenLabs
6. **Avatar** → Create presenter video with D-ID
7. **Intro** → Generate cinematic opening with Veo
8. **Preflight** → Validate all assets ready
9. **Publish** → Upload to R2 storage

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

## Performance Notes

- Slide rendering: ~2-3s per slide
- Narrative generation: ~5-10s per presentation
- TTS generation: ~3-5s per slide
- Avatar video: ~30-60s per slide
- Intro video: ~2-6 minutes (Veo processing)
- R2 upload: <1s per asset

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature-name`
3. Make your changes
4. Run tests and formatting: `npm run lint`
5. Commit using conventional format: `git commit -m "feat: your description"`
6. Push to your fork and submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.
