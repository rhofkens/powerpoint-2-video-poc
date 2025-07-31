# Coding Guidelines
## PowerPoint to Video Story POC

### 1. Overview

This document provides coding standards and best practices for the PowerPoint to Video Story POC project. All team members should follow these guidelines to ensure code consistency, maintainability, and quality across the codebase.

### 2. General Principles

- **KISS (Keep It Simple, Stupid):** Write simple, clear code that solves the problem at hand
- **YAGNI (You Aren't Gonna Need It):** Don't add functionality until it's actually needed
- **DRY (Don't Repeat Yourself):** Avoid code duplication; extract common functionality
- **SOLID Principles:** Follow SOLID principles where applicable, especially in Java code
- **Clean Code:** Write code that is self-documenting and easy to understand

### 3. Java/Spring Boot Guidelines

#### 3.1 Java Version and Language Features
- **Java 24:** Use modern Java features appropriately
- Prefer `var` for local variables with obvious types
- Use records for immutable data classes
- Leverage pattern matching in switch expressions
- Use text blocks for multi-line strings

#### 3.2 Spring Boot Best Practices
- **Spring Boot 3.5.4 conventions:**
  - Use constructor injection (avoid field injection)
  - Leverage Spring Boot's auto-configuration
  - Use `@RestController` for REST endpoints
  - Apply `@Service`, `@Repository`, `@Component` appropriately

### 4. React/TypeScript Guidelines

#### 4.1 TypeScript Configuration
- **Strict mode enabled:** Use strict TypeScript checking
- **No implicit any:** All variables must have explicit or inferred types
- **React 18.x features:** Use modern React patterns

#### 4.2 Component Structure
```typescript
// Functional components with TypeScript
interface SlideViewerProps {
  slideId: string;
  onRefresh: () => void;
}

export const SlideViewer: React.FC<SlideViewerProps> = ({ slideId, onRefresh }) => {
  // Hooks at the top
  const [loading, setLoading] = useState(false);
  const { data, error } = useSlideData(slideId);
  
  // Zustand store access
  const { currentPresentation, setProcessingStatus } = usePresentationStore();
  
  // Event handlers
  const handleGenerateSpeech = async () => {
    setLoading(true);
    setProcessingStatus(slideId, 'processing');
    try {
      await api.generateSpeech(slideId);
      onRefresh();
      setProcessingStatus(slideId, 'completed');
    } catch (error) {
      setProcessingStatus(slideId, 'failed');
      throw error;
    } finally {
      setLoading(false);
    }
  };
  
  // Early returns for loading/error states
  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorMessage error={error} />;
  
  // Main render
  return (
    <div className="slide-viewer">
      {/* Component JSX */}
    </div>
  );
};
```

#### 4.3 Zustand State Management
```typescript
// store/presentationStore.ts
import { create } from 'zustand';
import { devtools } from 'zustand/middleware';

interface PresentationState {
  presentations: Presentation[];
  currentPresentation: Presentation | null;
  processingStatus: Record<string, ProcessingStatus>;
  
  // Actions
  setCurrentPresentation: (presentation: Presentation | null) => void;
  updatePresentation: (id: string, updates: Partial<Presentation>) => void;
  setProcessingStatus: (slideId: string, status: ProcessingStatus) => void;
  resetStore: () => void;
}

export const usePresentationStore = create<PresentationState>()(
  devtools(
    (set) => ({
      presentations: [],
      currentPresentation: null,
      processingStatus: {},
      
      setCurrentPresentation: (presentation) =>
        set({ currentPresentation: presentation }),
        
      updatePresentation: (id, updates) =>
        set((state) => ({
          presentations: state.presentations.map((p) =>
            p.id === id ? { ...p, ...updates } : p
          ),
        })),
        
      setProcessingStatus: (slideId, status) =>
        set((state) => ({
          processingStatus: { ...state.processingStatus, [slideId]: status },
        })),
        
      resetStore: () =>
        set({ presentations: [], currentPresentation: null, processingStatus: {} }),
    }),
    {
      name: 'presentation-store', // Name for devtools
    }
  )
);
```

### 5. Code Quality Tools

#### 5.1 Java/Spring Boot
```xml
<!-- Maven plugins in pom.xml -->
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <configuration>
        <java>
            <googleJavaFormat>
                <version>1.28.0</version>
            </googleJavaFormat>
        </java>
    </configuration>
</plugin>
```

#### 5.2 TypeScript/React
```json
// .eslintrc.json
{
  "extends": [
    "eslint:recommended",
    "plugin:@typescript-eslint/recommended",
    "plugin:react/recommended",
    "plugin:react-hooks/recommended"
  ],
  "rules": {
    "@typescript-eslint/explicit-function-return-type": "off",
    "@typescript-eslint/no-explicit-any": "error",
    "react/react-in-jsx-scope": "off"
  }
}

// .prettierrc
{
  "semi": true,
  "trailingComma": "es5",
  "singleQuote": true,
  "printWidth": 100,
  "tabWidth": 2
}
```

### 6. Error Handling and Logging

#### 6.1 Backend Logging
```java
@Service
@Slf4j
public class VideoGenerationService {
    
    public String generateVideo(String slideId, String audioPath) {
        log.info("Starting video generation for slide: {}", slideId);
        
        try {
            // Video generation logic
            String videoPath = processVideo(audioPath);
            log.info("Video generation completed for slide: {}, path: {}", slideId, videoPath);
            return videoPath;
        } catch (Exception e) {
            log.error("Video generation failed for slide: {}", slideId, e);
            throw new ProcessingException("Failed to generate video", e);
        }
    }
}
```

#### 6.2 Frontend Error Handling
```typescript
// Global error boundary
export class ErrorBoundary extends React.Component<Props, State> {
  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('React Error Boundary:', error, errorInfo);
    // Could send to error tracking service in production
  }
  
  render() {
    if (this.state.hasError) {
      return <ErrorFallback onReset={() => this.setState({ hasError: false })} />;
    }
    
    return this.props.children;
  }
}

// API error handling
const handleApiError = (error: unknown): string => {
  if (error instanceof Error) {
    console.error('API Error:', error.message);
    return error.message;
  }
  console.error('Unknown error:', error);
  return 'An unexpected error occurred';
};
```

### 7. Version Control Guidelines

#### 7.1 Commit Messages
```
feat: add speech generation endpoint
fix: correct file upload validation
docs: update API documentation
refactor: extract narrative generation logic
style: format code with prettier
test: add presentation service tests
chore: update dependencies
```

Format: `<type>: <description>`
- Keep descriptions under 72 characters
- Use present tense ("add" not "added")
- Reference issue numbers when applicable

### 8. Documentation Standards

#### 8.1 Java Documentation
```java
/**
 * Service for processing PowerPoint presentations and extracting content.
 * This service handles the parsing of PPTX files and manages the conversion pipeline.
 */
@Service
public class PowerPointParserService {
    
    /**
     * Parses a PowerPoint file and extracts content from all slides.
     * 
     * @param file the uploaded PowerPoint file
     * @param presentationId the ID of the presentation entity
     * @return list of processed slides with extracted content
     * @throws ProcessingException if the file cannot be parsed
     */
    public List<Slide> parsePresentation(MultipartFile file, UUID presentationId) {
        // Implementation
    }
}
```

#### 8.2 TypeScript Documentation
```typescript
/**
 * Custom hook for managing presentation data and operations.
 * Handles loading states, error handling, and data refresh.
 * 
 * @param presentationId - The UUID of the presentation
 * @returns Object containing presentation data, loading state, and error
 */
export const usePresentation = (presentationId: string) => {
  // Implementation
};

interface SlideViewerProps {
  /** The unique identifier of the slide */
  slideId: string;
  /** Callback fired when slide data should be refreshed */
  onRefresh: () => void;
  /** Optional CSS class name for styling */
  className?: string;
}
```

### 9. Performance Guidelines

#### 9.1 Backend Performance
- Use database connection pooling (HikariCP is configured by default)
- Implement pagination for list endpoints
- Use `@Async` for long-running operations
- Cache frequently accessed data where appropriate

#### 9.2 Frontend Performance
- Lazy load components with React.lazy()
- Memoize expensive computations with useMemo
- Use React.memo for pure components
- Implement virtual scrolling for large lists
- Optimize bundle size with code splitting

### 10. Security Best Practices

#### 10.1 Input Validation
- Validate all user inputs at the controller level
- Use Bean Validation annotations in Java
- Sanitize file uploads (check type, size, content)
- Never trust client-side validation alone

#### 10.2 API Security
- Store API keys in environment variables
- Never commit secrets to version control
- Implement rate limiting for expensive operations

### 11. Monorepo Guidelines

#### 11.1 Project Structure
- Keep backend and frontend at the root level for simplicity
- Share types through the `shared` folder when needed


#### 11.2 Shared Resources
```
shared/
└── api-docs/          # OpenAPI specifications
    └── openapi.yaml   # API documentation
```

#### 11.3 Development Workflow
- Run both frontend and backend from the root directory
- Use concurrent scripts for development:
  ```json
  {
    "scripts": {
      "dev": "concurrently \"npm run dev:backend\" \"npm run dev:frontend\"",
      "dev:backend": "cd backend && mvn spring-boot:run",
      "dev:frontend": "cd frontend && npm run dev"
    }
  }
  ```

### 12. Code Review Checklist

Before submitting code for review, ensure:
- [ ] Code follows the style guidelines
- [ ] Package names use `ai.bluefields.ppt2video` convention
- [ ] Zustand stores are properly typed and organized
- [ ] No commented-out code
- [ ] No `console.log` statements (use proper logging)
- [ ] Error handling is implemented
- [ ] Code is self-documenting or has appropriate comments
- [ ] No hardcoded values (use configuration)
- [ ] Security considerations have been addressed
- [ ] Performance impact has been considered
