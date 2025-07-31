#!/bin/bash

# Development startup script for PowerPoint to Video POC
# This script starts both backend and frontend services concurrently

set -e

echo "🚀 Starting PowerPoint to Video POC development environment..."

# Check if PostgreSQL is running
if ! pg_isready -h localhost -p 5432 >/dev/null 2>&1; then
    echo "❌ PostgreSQL is not running. Please start PostgreSQL first."
    echo "   You can start it with: brew services start postgresql"
    exit 1
fi

# Check if required environment variables are set
if [ -z "$PPT2VIDEOPOC_DB_PWD" ]; then
    echo "❌ PPT2VIDEOPOC_DB_PWD environment variable is not set."
    echo "   Please set it in your shell profile or use: export PPT2VIDEOPOC_DB_PWD=your_password"
    exit 1
fi

# Start both services using npm script
echo "✅ Starting backend and frontend services..."
npm run dev