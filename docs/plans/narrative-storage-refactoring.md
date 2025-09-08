# Narrative Storage Refactoring Architecture Plan

## Executive Summary
This plan outlines the refactoring of the narrative storage system to support separate storage of original and emotionally enhanced narratives. The refactoring will enable better subtitle generation, translation support, and audio generation flexibility while maintaining backward compatibility with existing data.

## Current State Analysis

### Existing Architecture Overview
The current system stores narratives in a single field with the following structure:

1. **Database Schema**:
   - Table: `slide_narratives`
   - Key Field: `narrative_text` (TEXT) - stores the narrative content
   - The same field is overwritten when emotional enhancement is applied
   - No distinction between original and enhanced versions

2. **Narrative Generation Flow**:
   ```
   1. Initial Generation → narrative_text (original)
   2. Emotional Enhancement → narrative_text (overwritten with enhanced version)
   3. Audio Generation → uses narrative_text (enhanced if available)
   ```

3. **Key Components**:
   - `NarrativeGenerationService`: Creates initial narratives
   - `ElevenLabsEmotionalEnhancer`: Enhances narratives with emotional markers (overwrites original)
   - `TextToSpeechService`: Generates audio from narratives
   - `BatchNarrativeOrchestrator`: Handles bulk narrative generation
   - `NarrativeOptimizationOrchestrator`: Coordinates enhancement processes

### Identified Limitations
1. **Data Loss**: Original narrative is lost after emotional enhancement
2. **Translation Issues**: Enhanced narratives with punctuation/capitalization changes are not ideal for translation
3. **Subtitle Quality**: Enhanced narratives with emotional markers (CAPS, ellipses) are not suitable for subtitles
4. **No Fallback**: Cannot revert to original narrative if enhancement fails or is inappropriate
5. **Limited Flexibility**: Cannot choose between original and enhanced versions based on use case

### Dependencies and Constraints
- Must maintain backward compatibility with existing data
- Cannot disrupt ongoing video generation workflows
- Must support both single and batch narrative operations
- Need to preserve existing API contracts for frontend compatibility

## Proposed Architecture

### High-Level Design
The solution introduces dual storage for narratives with intelligent routing based on the use case:

```
┌─────────────────────────────────────────────────────────────┐
│                     slide_narratives                         │
├─────────────────────────────────────────────────────────────┤
│ id                      │ UUID                              │
│ slide_id               │ UUID                              │
│ narrative_text         │ TEXT (original narrative)         │
│ enhanced_narrative_text│ TEXT (emotionally enhanced)       │
│ enhancement_metadata   │ JSONB (enhancement details)      │
│ ...existing fields...  │                                   │
└─────────────────────────────────────────────────────────────┘
```

**Service Layer Logic**:
```
Audio Generation:     enhanced_narrative_text ?? narrative_text
Subtitle Generation:  narrative_text (always original)
Translation:         narrative_text (always original)
Display:            configurable based on context
```

### Detailed Technical Specifications

#### Backend Changes

##### 1. Database Schema Modifications
```sql
-- Add new columns to slide_narratives table
ALTER TABLE slide_narratives 
ADD COLUMN enhanced_narrative_text TEXT,
ADD COLUMN enhancement_metadata JSONB,
ADD COLUMN enhancement_timestamp TIMESTAMP,
ADD COLUMN enhancement_model_used VARCHAR(100);

-- Create index for queries filtering by enhancement status
CREATE INDEX idx_slide_narratives_enhanced 
ON slide_narratives(id) 
WHERE enhanced_narrative_text IS NOT NULL;
```

##### 2. Entity Updates
**SlideNarrative.java**:
```java
@Column(name = "enhanced_narrative_text", columnDefinition = "TEXT")
private String enhancedNarrativeText;

@Column(name = "enhancement_metadata", columnDefinition = "JSONB")
@JdbcTypeCode(SqlTypes.JSON)
private String enhancementMetadata;

@Column(name = "enhancement_timestamp")
private LocalDateTime enhancementTimestamp;

@Column(name = "enhancement_model_used")
private String enhancementModelUsed;

// Helper method for getting effective narrative
@Transient
public String getEffectiveNarrativeForAudio() {
    return enhancedNarrativeText != null ? enhancedNarrativeText : narrativeText;
}

@Transient
public boolean hasEnhancement() {
    return enhancedNarrativeText != null && !enhancedNarrativeText.isEmpty();
}
```

##### 3. Service Layer Modifications

**ElevenLabsEmotionalEnhancer.java**:
```java
// Update to store enhanced version separately
@Override
@Transactional
public String enhanceNarrative(SlideNarrative narrative) {
    try {
        // Generate enhanced text
        String enhancedText = callAIForEmotionalEnhancement(narrative, presentationId);
        
        if (enhancedText != null && !enhancedText.isEmpty()) {
            // Store in new field instead of overwriting
            narrative.setEnhancedNarrativeText(enhancedText);
            narrative.setEnhancementTimestamp(LocalDateTime.now());
            narrative.setEnhancementModelUsed(modelUsed);
            
            // Store metadata about enhancement
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("enhancer", "elevenlabs");
            metadata.put("originalLength", narrative.getNarrativeText().length());
            metadata.put("enhancedLength", enhancedText.length());
            narrative.setEnhancementMetadata(objectMapper.writeValueAsString(metadata));
            
            slideNarrativeRepository.save(narrative);
            return enhancedText;
        }
        
        return narrative.getNarrativeText();
    } catch (Exception e) {
        log.error("Failed to enhance narrative", e);
        return narrative.getNarrativeText();
    }
}
```

**TextToSpeechService.java**:
```java
// Update to use enhanced narrative if available
private TransitionResult prepareTextWithTransitionAndTrack(SlideNarrative narrative) {
    // Use enhanced narrative for audio if available
    String baseText = narrative.getEffectiveNarrativeForAudio();
    
    // Continue with existing transition logic...
    TransitionResult result = new TransitionResult();
    result.text = baseText;
    // ... rest of existing logic
}
```

**NarrativeGenerationService.java**:
```java
// No changes needed - continues to store original in narrative_text
// Enhancement is handled separately by the enhancer
```

##### 4. New Service Methods

**SlideNarrativeRepository.java**:
```java
// Add query methods for enhanced narratives
@Query("SELECT sn FROM SlideNarrative sn WHERE sn.slide.presentation.id = :presentationId " +
       "AND sn.isActive = true AND sn.enhancedNarrativeText IS NOT NULL")
List<SlideNarrative> findEnhancedNarrativesByPresentationId(@Param("presentationId") UUID presentationId);

@Query("SELECT sn FROM SlideNarrative sn WHERE sn.slide.presentation.id = :presentationId " +
       "AND sn.isActive = true AND sn.enhancedNarrativeText IS NULL")
List<SlideNarrative> findUnenhancedNarrativesByPresentationId(@Param("presentationId") UUID presentationId);
```

##### 5. API Modifications

**SlideNarrativeDto.java**:
```java
public class SlideNarrativeDto {
    // Existing fields...
    private String narrativeText;           // Original narrative
    private String enhancedNarrativeText;   // Enhanced narrative
    private boolean hasEnhancement;         // Quick check flag
    private LocalDateTime enhancementTimestamp;
    private Map<String, Object> enhancementMetadata;
    
    // Add display helper
    public String getDisplayNarrative(boolean preferEnhanced) {
        return preferEnhanced && enhancedNarrativeText != null 
            ? enhancedNarrativeText 
            : narrativeText;
    }
}
```

#### Frontend Changes

##### 1. Type Updates
**presentation.ts**:
```typescript
export interface SlideNarrative {
  // Existing fields...
  narrativeText: string;               // Original narrative
  enhancedNarrativeText?: string;      // Enhanced narrative
  hasEnhancement: boolean;             // Quick check flag
  enhancementTimestamp?: string;       // ISO timestamp
  enhancementMetadata?: Record<string, unknown>;
}
```

##### 2. Component Updates
**NarrativeDisplay.tsx**:
```typescript
interface NarrativeDisplayProps {
  narrative: SlideNarrative;
  showEnhanced?: boolean;  // Toggle for showing enhanced version
  showToggle?: boolean;    // Show toggle button if both versions exist
}

export function NarrativeDisplay({ 
  narrative, 
  showEnhanced = false, 
  showToggle = true 
}: NarrativeDisplayProps) {
  const [displayEnhanced, setDisplayEnhanced] = useState(showEnhanced);
  const hasEnhancement = narrative.hasEnhancement;
  
  const displayText = displayEnhanced && narrative.enhancedNarrativeText
    ? narrative.enhancedNarrativeText
    : narrative.narrativeText;
  
  return (
    <div>
      {hasEnhancement && showToggle && (
        <Toggle 
          label="Show Enhanced Version"
          checked={displayEnhanced}
          onChange={setDisplayEnhanced}
        />
      )}
      <div className="narrative-text">{displayText}</div>
    </div>
  );
}
```

#### Database Design

##### Migration Strategy
1. **V17__Add_enhanced_narrative_fields.sql**:
```sql
-- Add new columns for enhanced narratives
ALTER TABLE slide_narratives 
ADD COLUMN enhanced_narrative_text TEXT,
ADD COLUMN enhancement_metadata JSONB,
ADD COLUMN enhancement_timestamp TIMESTAMP,
ADD COLUMN enhancement_model_used VARCHAR(100);

-- Create performance indexes
CREATE INDEX idx_slide_narratives_enhanced 
ON slide_narratives(id) 
WHERE enhanced_narrative_text IS NOT NULL;

-- Add comments for documentation
COMMENT ON COLUMN slide_narratives.enhanced_narrative_text IS 
  'Emotionally enhanced version of narrative_text for TTS engines';
COMMENT ON COLUMN slide_narratives.enhancement_metadata IS 
  'Metadata about the enhancement process including engine type and parameters';
```

2. **Data Migration Script** (optional, for existing enhanced narratives):
```sql
-- This migration would need to be run if we want to preserve
-- existing enhanced narratives (though they're currently overwriting originals)
-- Since originals are lost, this is not applicable for existing data
```

## Implementation Roadmap

### Phase 1: Database and Entity Foundation
**Duration: 1-2 days**

Tasks:
1. Create and test migration script V17__Add_enhanced_narrative_fields.sql
2. Update SlideNarrative entity with new fields and helper methods
3. Update SlideNarrativeRepository with new query methods
4. Create unit tests for entity changes

Deliverables:
- Migrated database schema
- Updated entity with backward compatibility
- Repository methods for querying enhanced narratives

### Phase 2: Service Layer Implementation
**Duration: 2-3 days**

Tasks:
1. Refactor ElevenLabsEmotionalEnhancer to use new fields
2. Update TextToSpeechService to use getEffectiveNarrativeForAudio()
3. Update NarrativeOptimizationOrchestrator for new field structure
4. Ensure BatchNarrativeOrchestrator continues to work correctly
5. Add service methods for subtitle/translation use cases
6. Create integration tests for service layer changes

Deliverables:
- Emotional enhancement storing to separate field
- Audio generation using enhanced narrative with fallback
- Preserved batch processing functionality

### Phase 3: API and Frontend Integration
**Duration: 2-3 days**

Tasks:
1. Update DTOs to include new fields
2. Update API endpoints to return both narrative versions
3. Update frontend types and interfaces
4. Implement UI components for displaying/toggling narratives
5. Add frontend logic for subtitle generation using original narrative
6. Test end-to-end workflows

Deliverables:
- Updated API responses with both narrative versions
- Frontend components supporting narrative switching
- Subtitle generation using original narratives

### Phase 4: Manual Testing
**Duration: 1 day**

Tasks:
1. Manual integration testing of narrative generation flow
2. Verify emotional enhancement workflow
3. Test fallback behavior (enhanced → original)
4. Validate API responses with both narrative types
5. Test batch narrative generation with enhancement

Deliverables:
- Test checklist completion
- Known issues documentation
- Basic usage documentation

## Data Migration Strategy

### Detection of Enhanced Narratives

We can detect enhanced narratives using pattern matching:
```sql
-- Detect enhanced narratives by looking for ElevenLabs emotional markers
SELECT COUNT(*) as total_narratives,
       COUNT(CASE WHEN 
           narrative_text LIKE '%...%' OR           -- Ellipses for pauses
           narrative_text ~ '[A-Z]{2,}' OR          -- CAPITALIZED words
           narrative_text LIKE '%!%' OR              -- Exclamation marks
           narrative_text LIKE '%—%'                 -- Em dashes
       THEN 1 END) as likely_enhanced
FROM slide_narratives;
```

### Migration Approach

1. **Pre-migration Analysis**
   ```sql
   -- Create temporary table to track enhanced narratives
   CREATE TEMP TABLE enhanced_narrative_analysis AS
   SELECT id, slide_id,
          CASE WHEN 
              narrative_text LIKE '%...%' OR
              narrative_text ~ '[A-Z]{2,}' OR
              narrative_text LIKE '%!%' OR
              narrative_text LIKE '%—%'
          THEN true ELSE false END as is_enhanced
   FROM slide_narratives;
   ```

2. **Migration Script**
   ```sql
   -- Add new columns
   ALTER TABLE slide_narratives
   ADD COLUMN enhanced_narrative_text TEXT,
   ADD COLUMN original_narrative_text TEXT,
   ADD COLUMN enhancement_metadata JSONB,
   ADD COLUMN enhancement_timestamp TIMESTAMP,
   ADD COLUMN enhancement_model_used VARCHAR(100);

   -- Migrate existing data
   UPDATE slide_narratives sn
   SET enhanced_narrative_text = CASE 
           WHEN ena.is_enhanced THEN sn.narrative_text 
           ELSE NULL 
       END,
       original_narrative_text = CASE
           WHEN ena.is_enhanced THEN NULL  -- We lost the original
           ELSE sn.narrative_text
       END,
       enhancement_timestamp = CASE
           WHEN ena.is_enhanced THEN sn.updated_at
           ELSE NULL
       END,
       enhancement_model_used = CASE
           WHEN ena.is_enhanced THEN 'elevenlabs-legacy'
           ELSE NULL
       END,
       enhancement_metadata = CASE
           WHEN ena.is_enhanced THEN 
               jsonb_build_object(
                   'migration_note', 'Migrated from single narrative_text field',
                   'original_lost', true,
                   'detection_method', 'pattern_matching'
               )
           ELSE NULL
       END
   FROM enhanced_narrative_analysis ena
   WHERE ena.id = sn.id;

   -- Update narrative_text to always contain the original (or best available)
   UPDATE slide_narratives
   SET narrative_text = COALESCE(original_narrative_text, narrative_text);
   ```

3. **Post-migration Cleanup**
   ```sql
   -- For enhanced narratives where we lost the original,
   -- we'll need to regenerate the original narrative
   SELECT s.presentation_id, s.slide_number, sn.id
   FROM slide_narratives sn
   JOIN slides s ON s.id = sn.slide_id
   WHERE sn.enhanced_narrative_text IS NOT NULL
     AND sn.original_narrative_text IS NULL
   ORDER BY s.presentation_id, s.slide_number;
   ```

### Handling Lost Original Narratives

For slides where the original narrative was overwritten:
1. Mark them in enhancement_metadata with `"original_lost": true`
2. Optionally regenerate original narratives using the base narrative generation service
3. Keep the enhanced version as-is to maintain continuity

### Rollback Plan

```sql
-- Rollback script if migration needs to be reversed
UPDATE slide_narratives
SET narrative_text = COALESCE(enhanced_narrative_text, original_narrative_text, narrative_text);

ALTER TABLE slide_narratives
DROP COLUMN enhanced_narrative_text,
DROP COLUMN original_narrative_text,
DROP COLUMN enhancement_metadata,
DROP COLUMN enhancement_timestamp,
DROP COLUMN enhancement_model_used;
```

## Risk Assessment

### Technical Risks

1. **Database Migration Complexity**
   - Risk: Migration failure on production database
   - Mitigation: Test migration on staging environment first, create rollback script
   - Rollback: `ALTER TABLE slide_narratives DROP COLUMN enhanced_narrative_text, DROP COLUMN enhancement_metadata, DROP COLUMN enhancement_timestamp, DROP COLUMN enhancement_model_used;`

2. **Performance Impact**
   - Risk: Increased storage and query time due to additional fields
   - Mitigation: Add appropriate indexes, monitor query performance
   - Measurement: Track query execution times before and after

3. **Backward Compatibility**
   - Risk: Breaking existing integrations
   - Mitigation: Use null-safe operations, maintain existing API contracts
   - Testing: Ensure all existing endpoints continue to work

### Operational Risks

1. **Data Consistency**
   - Risk: Mismatch between original and enhanced narratives
   - Mitigation: Add validation to ensure enhanced is derived from current original
   - Monitoring: Add alerts for consistency checks

2. **Enhancement Failure Handling**
   - Risk: System fails if enhancement service is down
   - Mitigation: Graceful fallback to original narrative
   - Implementation: Try-catch blocks with fallback logic

### Security Considerations
- No new security risks identified
- Existing access controls remain in place
- No sensitive data in new fields

## Alternative Approaches Considered

### 1. Separate Table for Enhanced Narratives
**Approach**: Create a new table `enhanced_narratives` with foreign key to `slide_narratives`

**Pros**:
- Complete separation of concerns
- No modification to existing table structure
- Could store multiple enhancement versions

**Cons**:
- Additional join required for queries
- More complex data management
- Increased complexity for cascade deletes

**Rejection Reason**: Unnecessary complexity for the current use case

### 2. Version-Based Approach
**Approach**: Create new narrative records with version numbers for enhanced versions

**Pros**:
- Maintains history of all versions
- Flexible for multiple enhancement attempts

**Cons**:
- Complicates active narrative selection
- Increases data volume significantly
- More complex queries for getting latest version

**Rejection Reason**: Over-engineered for current requirements

### 3. JSON Storage for Both Versions
**Approach**: Store both narratives in a single JSONB field

**Pros**:
- Single field to manage
- Flexible structure

**Cons**:
- Loss of SQL querying capabilities
- Harder to index and search
- Not aligned with current architecture patterns

**Rejection Reason**: Reduces queryability and doesn't align with existing patterns

## Success Metrics

### Performance Benchmarks
- Audio generation time: ≤ 5% increase (due to field selection logic)
- Narrative query time: < 50ms for enhanced narrative retrieval
- Storage overhead: < 2x current narrative storage (expected ~1.3x)

### Quality Indicators
- Zero data loss during migration
- 100% backward compatibility with existing APIs
- Subtitle quality improvement (measured by user feedback)
- Successful translation of original narratives (manual verification)

### Business Metrics
- Reduced subtitle correction time: Target 50% reduction
- Improved translation accuracy: Measured through translation service metrics
- User satisfaction with subtitle quality: Survey feedback

## Dependencies and Prerequisites

### Required Tools and Libraries
- PostgreSQL 13+ (for JSONB support)
- Flyway 8+ (for migration management)
- Spring Boot 3.x (existing)
- React 18+ (existing)

### Team Skills Needed
- Database migration experience
- Spring Data JPA knowledge
- React/TypeScript proficiency
- Understanding of TTS systems

### Infrastructure Requirements
- Database backup before migration
- Staging environment for testing
- Monitoring for performance metrics
- Rollback capability

## Post-Implementation Considerations

### Monitoring Requirements
1. Track enhancement success rate
2. Monitor storage growth
3. Query performance metrics
4. API response times

### Maintenance Tasks
1. Regular cleanup of failed enhancements
2. Monitoring enhancement quality
3. Updating enhancement algorithms
4. Performance optimization as needed

### Future Enhancements
1. Support for multiple enhancement engines
2. A/B testing framework for enhancement algorithms
3. User preference storage for narrative display
4. Enhancement quality scoring system
5. Automatic re-enhancement when algorithms improve

## Conclusion

This refactoring provides a robust solution for separate storage of original and enhanced narratives while maintaining system stability and backward compatibility. The phased approach ensures minimal disruption to existing workflows while delivering the required functionality for improved subtitle generation, translation support, and flexible audio generation.