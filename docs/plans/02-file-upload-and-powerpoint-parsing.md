# Increment 2: File Upload & PowerPoint Parsing

## Detailed Scope

This increment implements the complete file upload and PowerPoint parsing functionality as defined in the PRD workflow steps 1-2. It establishes the foundation for processing PowerPoint presentations by creating robust upload capabilities and comprehensive content extraction.

### Backend Tasks

1. **REST API Endpoint Implementation**
   - Create POST `/api/presentations/upload` endpoint
   - Implement multipart file handling with Spring Boot
   - Return presentation ID and basic metadata on successful upload

2. **File Validation & Storage**
   - Implement file size validation (50MB limit as per PRD)
   - Validate MIME type (application/vnd.openxmlformats-officedocument.presentationml.presentation)
   - Create unique file naming strategy using UUID
   - Store files in configured upload directory (`${app.upload-dir}`)
   - Implement virus scanning integration point (stub for POC)

3. **Database Schema Creation**
   - Create `presentations` table:
     - id (UUID primary key)
     - filename (original filename)
     - file_path (stored location)
     - file_size (bytes)
     - upload_timestamp
     - status (UPLOADED, PARSING, PARSED, FAILED)
     - error_message (nullable)
   - Create `slides` table:
     - id (UUID primary key)
     - presentation_id (foreign key)
     - slide_number (order)
     - title (extracted from slide)
     - content_text (all text content)
     - speaker_notes
     - layout_type
     - created_at

4. **Apache POI Integration**
   - Add Apache POI dependencies (version 5.3.0)
   - Implement PowerPoint parsing service
   - Extract slide content:
     - Text from all text boxes
     - Slide titles
     - Speaker notes
     - Slide order/numbering
     - Layout information
   - Extract presentation metadata:
     - Total slide count
     - Presentation title
     - Author information (if available)

5. **Image Extraction & Storage**
   - Extract images from slides
   - Store images in file system with reference in database
   - Create `slide_images` table:
     - id (UUID)
     - slide_id (foreign key)
     - image_path
     - image_type (PNG, JPEG, etc.)
     - width, height
     - order_in_slide

6. **Error Handling**
   - Implement comprehensive error handling for:
     - Corrupt PowerPoint files
     - Unsupported file formats
     - File size exceeded
     - Parsing failures
   - Create custom exceptions:
     - `InvalidFileFormatException`
     - `FileSizeExceededException`
     - `PowerPointParsingException`

7. **Asynchronous Processing Setup**
   - Implement async parsing using Spring's `@Async`
   - Create background job for parsing after upload
   - Update presentation status during processing

### Frontend Tasks

8. **File Upload Component**
   - Create React component with drag-and-drop using react-dropzone
   - Display file selection area with clear instructions
   - Show selected file information before upload
   - Implement file type and size validation in UI

9. **Upload Progress & Feedback**
   - Implement upload progress bar using Axios progress events
   - Show upload speed and estimated time
   - Display success message with presentation ID
   - Show parsing status (queued → parsing → complete)

10. **Error Display & Handling**
    - Create error notification component
    - Display specific error messages for:
      - File too large
      - Invalid file type
      - Upload failures
      - Network errors
    - Implement retry mechanism for failed uploads

11. **Upload History Component**
    - Create simple list view of uploaded presentations
    - Show upload timestamp, filename, status
    - Enable click-through to parsed content (placeholder for now)

### Shared Tasks

12. **API Client Setup**
    - Configure Axios with base URL and interceptors
    - Implement error handling middleware
    - Add request/response logging for debugging

13. **Type Definitions**
    - Create TypeScript interfaces for:
      - Presentation model
      - Slide model
      - Upload response
      - Error responses
    - Share common types between frontend and backend

### Explicitly Excluded from This Increment

- AI integration for content analysis
- Video generation capabilities
- Advanced UI features (preview, editing)
- Authentication/authorization
- Batch file uploads
- PPT (older format) support
- Cloud storage integration
- Advanced parsing features (animations, transitions)
- Thumbnail generation

## Detailed Acceptance Criteria

### Upload Functionality
- [ ] User can drag and drop a PPTX file onto the upload area
- [ ] User can click to browse and select a PPTX file
- [ ] System validates file size limit of 50MB before upload
- [ ] System validates file type is PPTX format
- [ ] Upload progress is displayed with percentage and time estimate
- [ ] Successful upload returns a unique presentation ID

### File Validation
- [ ] Files larger than 50MB are rejected with clear error message
- [ ] Non-PPTX files are rejected with specific error message
- [ ] Corrupt PPTX files are handled gracefully with error message
- [ ] All validation errors are logged for debugging

### PowerPoint Parsing
- [ ] System extracts all text content from each slide
- [ ] System preserves slide order correctly
- [ ] System extracts speaker notes when present
- [ ] System extracts and stores all images from slides
- [ ] System identifies slide titles and layout types
- [ ] Parsing completes within 30 seconds for typical presentations

### Data Persistence
- [ ] All presentation metadata is stored in PostgreSQL
- [ ] All slide content is stored with proper relationships
- [ ] Uploaded files are stored securely with unique names
- [ ] Database queries complete within 100ms for single presentation

### Error Handling
- [ ] All errors return appropriate HTTP status codes
- [ ] Error messages are user-friendly and actionable
- [ ] Failed uploads can be retried
- [ ] System logs all errors with stack traces

### Frontend Experience
- [ ] Upload interface is intuitive and responsive
- [ ] All UI actions provide immediate feedback
- [ ] Error states are clearly communicated
- [ ] Success states show next steps clearly
- [ ] UI works on Chrome, Firefox, Safari, and Edge

## Detailed Documentation Tasks

### API Documentation
- Document POST `/api/presentations/upload` endpoint:
  - Request format (multipart/form-data)
  - Response format (JSON with presentation ID)
  - Error response formats
  - Example requests using curl and JavaScript

### README Updates
- Add "File Upload" section covering:
  - Supported file formats and size limits
  - Step-by-step upload instructions
  - Common error messages and solutions
  - Backend file storage configuration

### Code Documentation
- Document PowerPoint parsing service methods
- Add JSDoc comments to all public APIs
- Document database schema with column descriptions
- Add inline comments for complex parsing logic

### Architecture Updates
- Update architecture.md with:
  - File storage strategy details
  - Asynchronous processing flow
  - Error handling patterns
  - Database schema diagram